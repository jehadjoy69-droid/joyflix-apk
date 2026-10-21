package com.lagradost.cloudstream3.utils

import com.lagradost.cloudstream3.ui.player.ExtractorUri
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages scraper and server preference persistence across episodes and sessions.
 * Remembers the last selected scraper/source so next episodes and continue-watching
 * play from the exact same source automatically without re-searching all sources.
 */
object MediaSourcePreferenceHelper {
    private const val PREF_SOURCE_FOLDER = "preferred_media_source_v1"

    data class SourcePref(
        val scraper: String?,
        val serverName: String?,
        val timestamp: Long = System.currentTimeMillis()
    )

    // In-memory cache for fast, zero-delay lookups across episode transitions
    private val memoryCache = ConcurrentHashMap<String, SourcePref>()

    fun normalizeKey(title: String?, id: Int?): String? {
        if (id != null && id > 0) return "id_$id"
        if (!title.isNullOrBlank()) {
            val clean = title.lowercase().trim().replace(Regex("""[^a-z0-9]"""), "")
            if (clean.isNotBlank()) return "title_$clean"
        }
        return null
    }

    /**
     * Extracts actual scraper name (e.g. "FlixHQ" from "[FlixHQ] Server 1" or source property).
     * Prevents storing meta-provider names like "JoyFlix Discover" or "TMDB".
     */
    fun extractScraperName(scraper: String?, serverName: String?): String? {
        if (!scraper.isNullOrBlank() &&
            !scraper.equals("JoyFlix Discover", ignoreCase = true) &&
            !scraper.equals("TMDB", ignoreCase = true) &&
            !scraper.equals("Universal Embeds", ignoreCase = true)
        ) {
            return scraper.trim()
        }
        if (!serverName.isNullOrBlank()) {
            val bracketMatch = Regex("""\[(.*?)\]""").find(serverName)
            if (bracketMatch != null) {
                val inside = bracketMatch.groupValues[1].trim()
                if (inside.isNotBlank() &&
                    !inside.equals("JoyFlix Discover", ignoreCase = true) &&
                    !inside.equals("TMDB", ignoreCase = true)
                ) {
                    return inside
                }
            }
        }
        return scraper?.trim()?.takeIf {
            !it.equals("JoyFlix Discover", ignoreCase = true) && !it.equals("TMDB", ignoreCase = true)
        }
    }

    /**
     * Save the user's selected scraper and server for this media.
     */
    fun savePreferredSource(title: String?, id: Int?, scraper: String?, serverName: String?) {
        val cleanScraper = extractScraperName(scraper, serverName)
        val cleanServer = serverName?.trim()

        if (cleanScraper.isNullOrBlank() && cleanServer.isNullOrBlank()) return

        val pref = SourcePref(cleanScraper, cleanServer)

        val keys = linkedSetOf<String>()
        if (id != null && id > 0) keys.add("id_$id")
        normalizeKey(title, null)?.let { keys.add(it) }

        keys.forEach { key ->
            memoryCache[key] = pref
            try {
                if (!cleanScraper.isNullOrBlank()) {
                    setKey("$currentAccount/$PREF_SOURCE_FOLDER", "${key}_scraper", cleanScraper)
                }
                if (!cleanServer.isNullOrBlank()) {
                    setKey("$currentAccount/$PREF_SOURCE_FOLDER", "${key}_server", cleanServer)
                }
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Get the preferred scraper name (e.g. "FlixHQ", "SuperStream") for this media.
     */
    fun getPreferredScraper(title: String?, id: Int?): String? {
        val keys = linkedSetOf<String>()
        if (id != null && id > 0) keys.add("id_$id")
        normalizeKey(title, null)?.let { keys.add(it) }

        for (key in keys) {
            memoryCache[key]?.scraper?.let { return it }
            try {
                getKey<String>("$currentAccount/$PREF_SOURCE_FOLDER", "${key}_scraper")?.let { scraper ->
                    if (scraper.isNotBlank()) {
                        memoryCache[key] = SourcePref(scraper, null)
                        return scraper
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    /**
     * Get the preferred server name (e.g. "[FlixHQ] Server 1", "UpCloud") for this media.
     */
    fun getPreferredServer(title: String?, id: Int?): String? {
        val keys = linkedSetOf<String>()
        if (id != null && id > 0) keys.add("id_$id")
        normalizeKey(title, null)?.let { keys.add(it) }

        for (key in keys) {
            memoryCache[key]?.serverName?.let { return it }
            try {
                getKey<String>("$currentAccount/$PREF_SOURCE_FOLDER", "${key}_server")?.let { server ->
                    if (server.isNotBlank()) {
                        return server
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    /**
     * Computes matching score between a link and the preferred scraper/server.
     * Returns:
     * - 100: Both scraper and server match
     * - 80: Scraper matches
     * - 50: Server matches
     * - 0: No match
     */
    fun matchScore(
        link: Pair<ExtractorLink?, ExtractorUri?>,
        preferredScraper: String?,
        preferredServer: String?
    ): Int {
        val ext = link.first
        val uri = link.second
        val src = ext?.source
        val sName = ext?.name ?: uri?.name

        val matchesScraper = !preferredScraper.isNullOrBlank() && (
            src.equals(preferredScraper, ignoreCase = true) ||
            sName?.contains(preferredScraper, ignoreCase = true) == true ||
            sName?.contains("[$preferredScraper]", ignoreCase = true) == true
        )

        val matchesServer = !preferredServer.isNullOrBlank() && (
            sName.equals(preferredServer, ignoreCase = true) ||
            (ext?.name != null && preferredServer.contains(ext.name, ignoreCase = true))
        )

        return when {
            matchesScraper && matchesServer -> 100
            matchesScraper -> 80
            matchesServer -> 50
            else -> 0
        }
    }

    /**
     * Helper to test if a link matches the user's preferred scraper and/or server.
     */
    fun matchesPreferred(
        link: Pair<ExtractorLink?, ExtractorUri?>,
        preferredScraper: String?,
        preferredServer: String?
    ): Boolean {
        return matchScore(link, preferredScraper, preferredServer) > 0
    }
}
