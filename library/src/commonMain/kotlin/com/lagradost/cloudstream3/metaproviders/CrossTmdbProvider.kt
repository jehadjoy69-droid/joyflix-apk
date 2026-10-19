package com.lagradost.cloudstream3.metaproviders

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.APIHolder.apis
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    private val SearchResponse.yearOrNull: Int?
        get() = (this as? MovieSearchResponse)?.year ?: (this as? TvSeriesSearchResponse)?.year

    private fun filterName(name: String): String {
        return Regex("""[^a-zA-Z0-9]""").replace(name, "").lowercase()
    }

    private fun findMatchingItem(
        items: List<SearchResponse>,
        cleanTitle: String,
        releaseYear: Int?,
        isTv: Boolean
    ): SearchResponse? {
        val cleanNoYear = if (releaseYear != null) cleanTitle.removeSuffix("$releaseYear") else cleanTitle

        var bestMatch: SearchResponse? = null
        var highestScore = -1

        for (item in items) {
            // 1. Strict media type check: do not mix movies and TV series
            if (isTv) {
                if (item.type == TvType.Movie || item.type == TvType.AnimeMovie) continue
            } else {
                if (item.type == TvType.TvSeries || item.type == TvType.AsianDrama) continue
            }

            // 2. Year check: discard if release year difference is greater than 1
            val itemYear = item.yearOrNull
            if (releaseYear != null && itemYear != null) {
                if (kotlin.math.abs(itemYear - releaseYear) > 1) {
                    continue
                }
            }

            val itemClean = filterName(item.name)
            if (itemClean.isBlank()) continue
            val itemCleanNoYear = if (itemYear != null) itemClean.removeSuffix("$itemYear") else itemClean

            var score = 0
            val isExact = itemClean == cleanTitle ||
                itemCleanNoYear == cleanNoYear ||
                itemClean == cleanNoYear ||
                itemCleanNoYear == cleanTitle

            if (isExact) {
                score = if (itemYear != null && releaseYear != null && itemYear == releaseYear) {
                    100
                } else if (itemYear != null && releaseYear != null && kotlin.math.abs(itemYear - releaseYear) <= 1) {
                    95
                } else {
                    90
                }
            } else if (cleanNoYear.length >= 4 && itemCleanNoYear.startsWith(cleanNoYear)) {
                val diff = itemCleanNoYear.length - cleanNoYear.length
                if (diff <= 8) {
                    score = if (itemYear != null && releaseYear != null && itemYear == releaseYear) {
                        80
                    } else if (itemYear == null || releaseYear == null) {
                        75
                    } else {
                        70
                    }
                }
            } else if (cleanNoYear.length >= 6 && itemCleanNoYear.length >= 5 && cleanNoYear.startsWith(itemCleanNoYear)) {
                val diff = cleanNoYear.length - itemCleanNoYear.length
                if (diff <= 4) {
                    score = if (itemYear != null && releaseYear != null && itemYear == releaseYear) {
                        75
                    } else if (itemYear == null || releaseYear == null) {
                        70
                    } else {
                        65
                    }
                }
            }

            if (score >= 70 && score > highestScore) {
                highestScore = score
                bestMatch = item
            }
        }

        return bestMatch
    }

    interface ScraperListener {
        fun onScrapersDiscovered(scrapers: List<String>)
        fun onScraperStarted(scraper: String)
        fun onScraperFinished(scraper: String, linksCount: Int)
    }

    companion object {
        var scraperListener: ScraperListener? = null

        fun isAnimeApiStatic(api: com.lagradost.cloudstream3.MainAPI): Boolean {
            val types = api.supportedTypes
            val isPureAnime = types.isNotEmpty() && types.all {
                it == TvType.Anime || it == TvType.AnimeMovie || it == TvType.OVA
            }
            val hasAnimeNoGeneral = types.contains(TvType.Anime) &&
                !types.contains(TvType.Movie) &&
                !types.contains(TvType.TvSeries)
            val nameLower = api.name.lowercase()
            val isAnimeName = nameLower.contains("anime") ||
                nameLower.contains("aniwave") ||
                nameLower.contains("gogo") ||
                nameLower.contains("zoro") ||
                nameLower.contains("marin") ||
                nameLower.contains("yomiroll") ||
                nameLower.contains("aniwatch") ||
                nameLower.contains("allanime") ||
                nameLower.contains("nineanime") ||
                nameLower.contains("kickass")

            return isPureAnime || hasAnimeNoGeneral || isAnimeName
        }

        fun getValidApisStatic(): List<com.lagradost.cloudstream3.MainAPI> {
            return (apis + com.lagradost.cloudstream3.APIHolder.allProviders).distinctBy { it.name }.filter {
                it.name != "JoyFlix Discover" &&
                it !is TmdbProvider &&
                it.name != "All" &&
                it.name != "None" &&
                it.name != "Random" &&
                !it.name.contains("IPTV", ignoreCase = true) &&
                !it.supportedTypes.all { type -> type == TvType.Live }
            }
        }

        fun getAvailableScrapers(isAnime: Boolean = false, isTv: Boolean = false): List<String> {
            val valid = getValidApisStatic()
            return valid.filter { api ->
                if (!isAnime && isAnimeApiStatic(api)) {
                    return@filter false
                }

                if (isAnime) {
                    api.supportedTypes.any {
                        it == TvType.Anime || it == TvType.AnimeMovie || it == TvType.TvSeries || it == TvType.Movie
                    } || isAnimeApiStatic(api)
                } else if (isTv) {
                    api.supportedTypes.any {
                        it == TvType.TvSeries || it == TvType.AsianDrama || it == TvType.Cartoon
                    }
                } else {
                    api.supportedTypes.any {
                        it == TvType.Movie || it == TvType.Cartoon || it == TvType.Torrent
                    }
                }
            }.ifEmpty { valid.filter { !(!isAnime && isAnimeApiStatic(it)) } }
            .map { it.name }
        }
    }

    private val validApis: List<com.lagradost.cloudstream3.MainAPI>
        get() = getValidApisStatic()

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

    private suspend fun loadUniversalEmbeds(
        tmdbId: Int?,
        imdbId: String?,
        isTv: Boolean,
        season: Int?,
        episode: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (tmdbId == null && imdbId.isNullOrBlank()) return

        val embedUrls = mutableListOf<String>()

        if (tmdbId != null) {
            if (isTv && season != null && episode != null) {
                embedUrls.add("https://player.autoembed.cc/embed/tv/$tmdbId/$season/$episode")
                embedUrls.add("https://vidsrc.xyz/embed/tv?tmdb=$tmdbId&season=$season&episode=$episode")
                embedUrls.add("https://vidsrc.to/embed/tv/$tmdbId/$season/$episode")
                embedUrls.add("https://www.2embed.cc/embedtv/$tmdbId&s=$season&e=$episode")
            } else {
                embedUrls.add("https://player.autoembed.cc/embed/movie/$tmdbId")
                embedUrls.add("https://vidsrc.xyz/embed/movie?tmdb=$tmdbId")
                embedUrls.add("https://vidsrc.to/embed/movie/$tmdbId")
                embedUrls.add("https://vidsrc.cc/v2/embed/movie/$tmdbId")
                embedUrls.add("https://www.2embed.cc/embed/$tmdbId")
            }
        }

        if (!imdbId.isNullOrBlank()) {
            if (isTv && season != null && episode != null) {
                embedUrls.add("https://vidsrc.xyz/embed/tv?imdb=$imdbId&season=$season&episode=$episode")
                embedUrls.add("https://vidsrc.me/embed/tv?imdb=$imdbId&season=$season&episode=$episode")
            } else {
                embedUrls.add("https://vidsrc.xyz/embed/movie?imdb=$imdbId")
                embedUrls.add("https://vidsrc.me/embed/movie?imdb=$imdbId")
            }
        }

        for (embedUrl in embedUrls) {
            try {
                if (loadExtractor(embedUrl, subtitleCallback, callback)) {
                    // Extractor handled this embed directly
                    continue
                }
                val response = app.get(
                    embedUrl,
                    headers = mapOf(
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        "Referer" to "https://www.google.com/"
                    ),
                    timeout = 10_000L
                )
                val html = response.text
                if (html.isNotBlank()) {
                    val iframeRegex = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    val iframeUrls = iframeRegex.findAll(html).map { it.groupValues[1] }.take(3).toList()

                    for (iframeUrl in iframeUrls) {
                        try {
                            val resolvedUrl = if (iframeUrl.startsWith("//")) "https:$iframeUrl" else iframeUrl
                            loadExtractor(resolvedUrl, embedUrl, subtitleCallback, callback)
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun isAnimeApi(api: com.lagradost.cloudstream3.MainAPI): Boolean = isAnimeApiStatic(api)


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var foundAnyLink = false
        val wrappedCallback: (ExtractorLink) -> Unit = { link ->
            foundAnyLink = true
            callback(link)
        }

        // 1. Backwards compatibility with cached CrossMetaData
        tryParseJson<CrossMetaData>(data)?.let { metaData ->
            if (metaData.isSuccess) {
                metaData.movies?.amap { (apiName, movieData) ->
                    getApiFromNameNull(apiName)?.let { api ->
                        try {
                            api.loadLinks(movieData, isCasting, subtitleCallback, wrappedCallback)
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                }
                return foundAnyLink
            }
        }

        // 2. Resolve TmdbLink across all active scraping provider extensions
        var resolvedLink = tryParseJson<TmdbLink>(data)
        if (resolvedLink == null) {
            val tmdbUrlRegex = Regex("""themoviedb\.org/(movie|tv)/(\d+)""")
            val match = tmdbUrlRegex.find(data)
            if (match != null) {
                val isTvType = match.groupValues[1] == "tv"
                val id = match.groupValues[2].toIntOrNull()
                if (id != null) {
                    try {
                        val path = if (isTvType) "/tv/$id" else "/movie/$id"
                        val json = getApi(path, mapOf("language" to "en-US", "append_to_response" to "external_ids"))
                        if (isTvType) {
                            val detail = parseJson<TmdbTvDetail>(json)
                            resolvedLink = TmdbLink(
                                imdbID = detail.externalIds?.imdbId,
                                tmdbID = id,
                                episode = 1,
                                season = 1,
                                movieName = detail.displayTitle,
                                year = detail.year,
                                isAnime = detail.isAnime
                            )
                        } else {
                            val detail = parseJson<TmdbMovieDetail>(json)
                            resolvedLink = TmdbLink(
                                imdbID = detail.imdbId ?: detail.externalIds?.imdbId,
                                tmdbID = id,
                                episode = null,
                                season = null,
                                movieName = detail.displayTitle,
                                year = detail.year,
                                isAnime = detail.isAnime
                            )
                        }
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            }
        }

        val tmdbLink = resolvedLink ?: return false
        var title = tmdbLink.movieName
        if (title.isNullOrBlank() && tmdbLink.tmdbID != null) {
            try {
                val isTvType = tmdbLink.season != null && tmdbLink.episode != null
                val path = if (isTvType) "/tv/${tmdbLink.tmdbID}" else "/movie/${tmdbLink.tmdbID}"
                val json = getApi(path, mapOf("language" to "en-US"))
                val nameRegex = Regex(""""(?:title|name)"\s*:\s*"([^"]+)"""")
                title = nameRegex.find(json)?.groupValues?.getOrNull(1)
            } catch (e: Exception) {
                logError(e)
            }
        }
        if (title.isNullOrBlank()) return false

        val cleanTitle = filterName(title)
        val season = tmdbLink.season
        val episode = tmdbLink.episode
        val isTv = season != null && episode != null
        val imdbId = tmdbLink.imdbID
        val tmdbId = tmdbLink.tmdbID
        val releaseYear = tmdbLink.year
        val isAnime = tmdbLink.isAnime

        val available = validApis.filter { api ->
            // ANIME NA HOLE ANIME EXTENSION / ANIME SITE-A KONO REQUEST JABE NA
            if (!isAnime && isAnimeApi(api)) {
                return@filter false
            }

            if (isAnime) {
                api.supportedTypes.any {
                    it == TvType.Anime || it == TvType.AnimeMovie || it == TvType.TvSeries || it == TvType.Movie
                } || isAnimeApi(api)
            } else if (isTv) {
                api.supportedTypes.any {
                    it == TvType.TvSeries || it == TvType.AsianDrama || it == TvType.Cartoon
                }
            } else {
                api.supportedTypes.any {
                    it == TvType.Movie || it == TvType.Cartoon || it == TvType.Torrent
                }
            }
        }.ifEmpty { validApis.filter { !(!isAnime && isAnimeApi(it)) } }

        val scraperNames = available.map { it.name }
        scraperListener?.onScrapersDiscovered(scraperNames)

        if (available.isNotEmpty()) {
            available.amap { api ->
                scraperListener?.onScraperStarted(api.name)
                var linksFromThisScraper = 0
                try {
                    var loaded: LoadResponse? = null

                    // Try IMDb direct sync URL if supported
                    if (!imdbId.isNullOrBlank() && api.supportedSyncNames.contains(SyncIdName.Imdb)) {
                        try {
                            val syncUrl = api.getLoadUrl(SyncIdName.Imdb, imdbId)
                            if (!syncUrl.isNullOrBlank()) {
                                val syncLoaded = api.load(syncUrl)
                                if (syncLoaded != null) {
                                    if (isTv && syncLoaded is TvSeriesLoadResponse) {
                                        loaded = syncLoaded
                                    } else if (!isTv && syncLoaded !is TvSeriesLoadResponse) {
                                        loaded = syncLoaded
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }

                    // If direct sync not available, search with title variations
                    if (loaded == null) {
                        val searchQueries = linkedSetOf<String>()
                        searchQueries.add(title)
                        if (title.contains("&")) {
                            searchQueries.add(title.replace("&", "and"))
                            searchQueries.add(title.replace("&", " "))
                        }
                        if (title.contains(":")) {
                            searchQueries.add(title.substringBefore(":").trim())
                            searchQueries.add(title.replace(":", " "))
                        }
                        val cleanQuery = title.replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
                            .replace(Regex("""\s+"""), " ").trim()
                        if (cleanQuery.isNotBlank() && !cleanQuery.equals(title, ignoreCase = true)) {
                            searchQueries.add(cleanQuery)
                        }

                        for (q in searchQueries) {
                            try {
                                val res = api.search(q)
                                if (!res.isNullOrEmpty()) {
                                    val match = findMatchingItem(res, cleanTitle, releaseYear, isTv)
                                    if (match != null) {
                                        val matchLoaded = api.load(match.url)
                                        if (matchLoaded != null) {
                                            if (isTv && matchLoaded is TvSeriesLoadResponse) {
                                                loaded = matchLoaded
                                                break
                                            } else if (!isTv && matchLoaded !is TvSeriesLoadResponse) {
                                                loaded = matchLoaded
                                                break
                                            }
                                        }
                                    }
                                }
                            } catch (_: Throwable) {
                            }
                        }
                    }

                    if (loaded != null) {
                        val targetData: String? = if (isTv) {
                            if (loaded is TvSeriesLoadResponse) {
                                val targetEp = loaded.episodes.firstOrNull { ep ->
                                    ep.season == season && ep.episode == episode
                                } ?: if (season == 1) {
                                    loaded.episodes.firstOrNull { ep ->
                                        (ep.season == 1 || ep.season == null || ep.season == 0) && ep.episode == episode
                                    }
                                } else {
                                    null
                                }

                                targetEp?.data
                            } else {
                                null
                            }
                        } else {
                            if (loaded is MovieLoadResponse) {
                                loaded.dataUrl.ifBlank { null } ?: loaded.url
                            } else if (loaded is TvSeriesLoadResponse) {
                                null
                            } else {
                                loaded.url
                            }
                        }

                        if (!targetData.isNullOrBlank()) {
                            api.loadLinks(targetData, isCasting, subtitleCallback) { extLink ->
                                linksFromThisScraper++
                                val streamName = if (extLink.name.contains(api.name, ignoreCase = true)) {
                                    extLink.name
                                } else {
                                    "[${api.name}] ${extLink.name}"
                                }
                                @Suppress("DEPRECATION")
                                wrappedCallback(
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
                } finally {
                    scraperListener?.onScraperFinished(api.name, linksFromThisScraper)
                }
            }
        }

        // 3. Fallback to universal embeds if extensions returned no playable stream links
        if (!foundAnyLink) {
            try {
                scraperListener?.onScraperStarted("Universal Embeds")
                var embedCount = 0
                loadUniversalEmbeds(
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    isTv = isTv,
                    season = season,
                    episode = episode,
                    subtitleCallback = subtitleCallback,
                    callback = { extLink ->
                        embedCount++
                        wrappedCallback(
                            ExtractorLink(
                                source = "Universal Embeds",
                                name = extLink.name,
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
                )
                scraperListener?.onScraperFinished("Universal Embeds", embedCount)
            } catch (e: Exception) {
                logError(e)
            }
        }

        return foundAnyLink || available.isNotEmpty()
    }
}
