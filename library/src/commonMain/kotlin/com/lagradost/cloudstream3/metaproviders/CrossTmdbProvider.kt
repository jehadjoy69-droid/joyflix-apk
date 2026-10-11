package com.lagradost.cloudstream3.metaproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

class CrossTmdbProvider : TmdbProvider() {
    override var name = "JoyFlix Discover"
    override val apiName = "JoyFlix Discover"
    override var mainUrl = "https://www.themoviedb.org"
    override var lang = "en"
    override val useMetaLoadResponse = true
    override val usesWebView = false
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val includeAdult = true

    private fun filterName(name: String): String {
        return Regex("""[^a-zA-Z0-9]""").replace(name, "").lowercase()
    }

    private val validApis: List<com.lagradost.cloudstream3.MainAPI>
        get() = apis.filter {
            it.name != this.name &&
            it !is TmdbProvider &&
            it.name != "All" &&
            it.name != "None" &&
            it.name != "Random" &&
            !it.name.contains("IPTV", ignoreCase = true) &&
            !it.supportedTypes.all { type -> type == TvType.Live }
        }

    @Serializable
    data class CrossMetaData(
        @JsonProperty("isSuccess") @SerialName("isSuccess") val isSuccess: Boolean,
        @JsonProperty("movies") @SerialName("movies") val movies: List<Pair<String, String>>? = null,
    )

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        return super.search(query, page)
    }

    override suspend fun load(url: String): LoadResponse? {
        return super.load(url)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // 1. Backwards compatibility with cached CrossMetaData
        tryParseJson<CrossMetaData>(data)?.let { metaData ->
            if (metaData.isSuccess) {
                metaData.movies?.amap { (apiName, movieData) ->
                    getApiFromNameNull(apiName)?.let { api ->
                        try {
                            api.loadLinks(movieData, isCasting, subtitleCallback, callback)
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                }
                return true
            }
        }

        // 2. Resolve TmdbLink across all active scraping provider extensions
        val tmdbLink = tryParseJson<TmdbLink>(data) ?: return false
        val title = tmdbLink.movieName ?: return false
        val cleanTitle = filterName(title)
        val season = tmdbLink.season
        val episode = tmdbLink.episode
        val isTv = season != null && episode != null
        val imdbId = tmdbLink.imdbID

        val available = validApis.filter { api ->
            if (isTv) api.supportedTypes.contains(TvType.TvSeries)
            else api.supportedTypes.contains(TvType.Movie)
        }

        if (available.isEmpty()) return false

        available.amap { api ->
            try {
                var loaded: LoadResponse? = null

                // Try IMDb direct sync URL if supported
                if (!imdbId.isNullOrBlank() && api.supportedSyncNames.contains(SyncIdName.Imdb)) {
                    try {
                        val syncUrl = api.getLoadUrl(SyncIdName.Imdb, imdbId)
                        if (!syncUrl.isNullOrBlank()) {
                            loaded = api.load(syncUrl)
                        }
                    } catch (e: Exception) {
                        logError(e)
                    }
                }

                // If direct sync not available, search by title
                if (loaded == null) {
                    val items = api.search(title) ?: emptyList()
                    val match = items.firstOrNull { item ->
                        val itemClean = filterName(item.name)
                        itemClean == cleanTitle || itemClean.contains(cleanTitle) || cleanTitle.contains(itemClean)
                    } ?: items.firstOrNull()

                    if (match != null) {
                        loaded = api.load(match.url)
                    }
                }

                if (loaded != null) {
                    if (isTv && loaded is TvSeriesLoadResponse) {
                        val targetEp: Episode? = loaded.episodes.firstOrNull { ep ->
                            ep.season == season && ep.episode == episode
                        } ?: if (season == 1) loaded.episodes.firstOrNull { it.episode == episode } else null

                        if (targetEp != null) {
                            api.loadLinks(targetEp.data, isCasting, subtitleCallback) { extLink ->
                                val streamName = if (extLink.name.contains(api.name, ignoreCase = true)) {
                                    extLink.name
                                } else {
                                    "[${api.name}] ${extLink.name}"
                                }
                                callback(
                                    ExtractorLink(
                                        source = api.name,
                                        name = streamName,
                                        url = extLink.url,
                                        referer = extLink.referer,
                                        quality = extLink.quality,
                                        headers = extLink.headers,
                                        extractorData = extLink.extractorData,
                                        type = extLink.type,
                                        audioTracks = extLink.audioTracks,
                                    )
                                )
                            }
                        }
                    } else if (!isTv) {
                        val movieData = (loaded as? MovieLoadResponse)?.dataUrl?.ifBlank { null } ?: loaded.url
                        api.loadLinks(movieData, isCasting, subtitleCallback) { extLink ->
                            val streamName = if (extLink.name.contains(api.name, ignoreCase = true)) {
                                extLink.name
                            } else {
                                "[${api.name}] ${extLink.name}"
                            }
                            callback(
                                ExtractorLink(
                                    source = api.name,
                                    name = streamName,
                                    url = extLink.url,
                                    referer = extLink.referer,
                                    quality = extLink.quality,
                                    headers = extLink.headers,
                                    extractorData = extLink.extractorData,
                                    type = extLink.type,
                                    audioTracks = extLink.audioTracks,
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                logError(e)
            }
        }

        return true
    }
}
