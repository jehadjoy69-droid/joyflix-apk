package com.lagradost.cloudstream3.ui.player

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class TmdbMediaInfo(
    val logoUrl: String?,
    val rating: String?,
    val year: String?,
    val overview: String?,
    val backdropUrl: String? = null
)

object TmdbMetadataHelper {
    private const val TMDB_API_KEY = "e6333b32409e02a4a6eba6fb7ff866bb"
    private const val TMDB_SEARCH_URL = "https://api.themoviedb.org/3/search/multi"
    private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"
    private const val TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"

    private val cache = ConcurrentHashMap<String, TmdbMediaInfo>()

    fun cleanTitle(rawTitle: String): String {
        var t = rawTitle
        // Remove Season X, S01, Part X, Cour X, Dub, Sub, Uncensored, Movie etc.
        t = t.replace(Regex("(?i)\\b(season\\s*\\d+|s\\d+|part\\s*\\d+|cour\\s*\\d+|dub|sub|uncensored|bd|movie)\\b"), "")
        // Remove quality and release tags: 1080p, 720p, 4k, x264, x265, hevc, web-dl, webrip, bluray
        t = t.replace(Regex("(?i)\\b(1080p|720p|2160p|4k|x264|x265|hevc|web-dl|webrip|hdrip|bluray|hdtv|repack)\\b"), "")
        // Remove parenthesis/brackets content e.g. (2024), [Dub]
        t = t.replace(Regex("[\\(\\[\\{].*?[\\)\\]\\}]"), "")
        // Normalize whitespace
        t = t.replace(Regex("\\s+"), " ").trim()
        return if (t.length >= 2) t else rawTitle.trim()
    }

    suspend fun fetchMediaInfo(
        rawTitle: String,
        tmdbId: Int? = null,
        isMovie: Boolean? = null,
        imdbId: String? = null
    ): TmdbMediaInfo? = withContext(Dispatchers.IO) {
        val clean = cleanTitle(rawTitle)
        val cacheKey = when {
            tmdbId != null && tmdbId > 0 -> "tmdb_$tmdbId"
            !imdbId.isNullOrBlank() -> "imdb_$imdbId"
            else -> clean
        }
        if (cacheKey.isBlank()) return@withContext null

        cache[cacheKey]?.let { return@withContext it }

        try {
            var mediaType: String? = when (isMovie) {
                true -> "movie"
                false -> "tv"
                null -> null
            }
            var resolvedId = if (tmdbId != null && tmdbId > 0) tmdbId else null

            // 1. If IMDB ID provided and no TMDB ID, find via /find/{external_id}
            if (resolvedId == null && !imdbId.isNullOrBlank()) {
                try {
                    val findUrl = "https://api.themoviedb.org/3/find/$imdbId"
                    val findRes = app.get(
                        findUrl,
                        params = mapOf(
                            "api_key" to TMDB_API_KEY,
                            "external_source" to "imdb_id"
                        ),
                        cacheTime = 60 * 24
                    )
                    val findJson = JSONObject(findRes.text)
                    val movieResults = findJson.optJSONArray("movie_results")
                    val tvResults = findJson.optJSONArray("tv_results")
                    if (movieResults != null && movieResults.length() > 0) {
                        resolvedId = movieResults.getJSONObject(0).optInt("id", -1)
                        mediaType = "movie"
                    } else if (tvResults != null && tvResults.length() > 0) {
                        resolvedId = tvResults.getJSONObject(0).optInt("id", -1)
                        mediaType = "tv"
                    }
                } catch (_: Exception) {}
            }

            // 2. If no TMDB ID yet, search TMDB by clean title
            var voteAvg = 0.0
            var yearStr: String? = null
            var overview: String? = null

            if (resolvedId == null || resolvedId <= 0) {
                val searchRes = app.get(
                    TMDB_SEARCH_URL,
                    params = mapOf(
                        "api_key" to TMDB_API_KEY,
                        "query" to clean,
                        "include_adult" to "false",
                        "language" to "en-US"
                    ),
                    cacheTime = 60 * 24
                )

                val searchJson = JSONObject(searchRes.text)
                val results = searchJson.optJSONArray("results") ?: return@withContext null
                if (results.length() == 0) return@withContext null

                var selected: JSONObject? = null
                for (i in 0 until results.length()) {
                    val item = results.optJSONObject(i) ?: continue
                    val mType = item.optString("media_type")
                    if (mediaType != null) {
                        if (mType == mediaType) {
                            selected = item
                            break
                        }
                    } else if (mType == "movie" || mType == "tv") {
                        selected = item
                        break
                    }
                }
                if (selected == null) selected = results.optJSONObject(0) ?: return@withContext null

                mediaType = selected.optString("media_type", "movie")
                resolvedId = selected.optInt("id", -1)
                voteAvg = selected.optDouble("vote_average", 0.0)
                val releaseDate = selected.optString("release_date").ifBlank { selected.optString("first_air_date") }
                yearStr = if (releaseDate.length >= 4) releaseDate.substring(0, 4) else null
                overview = selected.optString("overview").takeIf { it.isNotBlank() }
            }

            if (resolvedId == null || resolvedId <= 0) return@withContext null
            val mType = mediaType ?: "movie"

            // If we didn't get details from search, fetch details for rating & year
            if (overview == null || yearStr == null) {
                try {
                    val detailRes = app.get(
                        "https://api.themoviedb.org/3/$mType/$resolvedId",
                        params = mapOf("api_key" to TMDB_API_KEY, "language" to "en-US"),
                        cacheTime = 60 * 24
                    )
                    val detailJson = JSONObject(detailRes.text)
                    if (voteAvg <= 0.0) voteAvg = detailJson.optDouble("vote_average", 0.0)
                    val releaseDate = detailJson.optString("release_date").ifBlank { detailJson.optString("first_air_date") }
                    if (yearStr == null && releaseDate.length >= 4) yearStr = releaseDate.substring(0, 4)
                    if (overview == null) overview = detailJson.optString("overview").takeIf { it.isNotBlank() }
                } catch (_: Exception) {}
            }

            val ratingStr = if (voteAvg > 0.0) String.format("%.1f", voteAvg) else null

            // 3. Fetch Logos & Backdrops
            val imgUrl = "https://api.themoviedb.org/3/$mType/$resolvedId/images"
            val imgRes = app.get(
                imgUrl,
                params = mapOf(
                    "api_key" to TMDB_API_KEY,
                    "include_image_language" to "en,null,ja"
                ),
                cacheTime = 60 * 24
            )

            val imgJson = JSONObject(imgRes.text)
            val logos = imgJson.optJSONArray("logos")
            var bestLogoPath: String? = null

            if (logos != null && logos.length() > 0) {
                // Prefer English logo
                for (i in 0 until logos.length()) {
                    val l = logos.optJSONObject(i) ?: continue
                    val lang = l.optString("iso_639_1")
                    if (lang == "en") {
                        bestLogoPath = l.optString("file_path")
                        break
                    }
                }
                if (bestLogoPath == null) {
                    bestLogoPath = logos.optJSONObject(0)?.optString("file_path")
                }
            }

            val backdrops = imgJson.optJSONArray("backdrops")
            val backdropPath = if (backdrops != null && backdrops.length() > 0) {
                backdrops.optJSONObject(0)?.optString("file_path")
            } else null

            val logoFullUrl = if (!bestLogoPath.isNullOrBlank()) "$TMDB_IMAGE_BASE$bestLogoPath" else null
            val backdropFullUrl = if (!backdropPath.isNullOrBlank()) "$TMDB_BACKDROP_BASE$backdropPath" else null

            val info = TmdbMediaInfo(
                logoUrl = logoFullUrl,
                rating = ratingStr,
                year = yearStr,
                overview = overview,
                backdropUrl = backdropFullUrl
            )
            cache[cacheKey] = info
            if (clean.isNotBlank()) cache[clean] = info
            info
        } catch (e: Exception) {
            logError(e)
            null
        }
    }
}
