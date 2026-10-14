package com.lagradost.cloudstream3.ui.result

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.APIHolder.unixTime
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.metaproviders.CrossTmdbProvider
import com.lagradost.cloudstream3.metaproviders.TmdbLink
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.player.Cache
import com.lagradost.cloudstream3.ui.player.GeneratorPlayer
import com.lagradost.cloudstream3.ui.player.LOADTYPE_ALL
import com.lagradost.cloudstream3.ui.player.RepoLinkGenerator
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.player.VideoGenerator
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ImageLoader.loadImage
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StreamSourcesDialogFragment : DialogFragment() {

    companion object {
        const val TAG = "StreamSourcesDialogFragment"

        var pendingEpisode: ResultEpisode? = null
        var pendingResponse: LoadResponse? = null
        var pendingGenerator: VideoGenerator<*>? = null
        var pendingIndex: Int = 0
        var pendingSyncData: HashMap<String, String>? = null

        fun show(
            activity: FragmentActivity?,
            episode: ResultEpisode,
            response: LoadResponse?,
            generator: VideoGenerator<*>?,
            index: Int,
            syncData: HashMap<String, String>?
        ) {
            if (activity == null || activity.isFinishing) return
            val fm = activity.supportFragmentManager
            if (fm.isStateSaved) return
            if (fm.findFragmentByTag(TAG) != null) return

            pendingEpisode = episode
            pendingResponse = response
            pendingGenerator = generator
            pendingIndex = index
            pendingSyncData = syncData

            val dialog = StreamSourcesDialogFragment()
            dialog.show(fm, TAG)
        }
    }

    private var currentEpisode: ResultEpisode? = null
    private var currentResponse: LoadResponse? = null
    private var currentGenerator: VideoGenerator<*>? = null
    private var currentIndex: Int = 0
    private var syncData: HashMap<String, String>? = null

    private var scrapingJob: Job? = null
    private var isScrapingComplete: Boolean = false
    private var selectedFilter: String? = null // null = "All"

    private val activeScrapers = linkedSetOf<String>()
    private val fetchingScrapers = linkedSetOf<String>()
    private val providerLinks = linkedMapOf<String, MutableList<ExtractorLink>>()
    private val allLoadedLinks = mutableListOf<ExtractorLink>()
    private val allLoadedSubs = mutableListOf<SubtitleData>()

    private var sourcesAdapter: StreamSourcesAdapter? = null
    private var loadingIndicator: ProgressBar? = null
    private var emptyView: LinearLayout? = null
    private var filterContainer: LinearLayout? = null
    private var scrapersContainer: LinearLayout? = null
    private var scrapersSection: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogFullscreenPlayer)
        currentEpisode = pendingEpisode
        currentResponse = pendingResponse
        currentGenerator = pendingGenerator
        currentIndex = pendingIndex
        syncData = pendingSyncData
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_stream_sources, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val backButton = view.findViewById<ImageButton>(R.id.sources_back_button)
        val refreshButton = view.findViewById<ImageButton>(R.id.sources_refresh_button)
        val posterView = view.findViewById<ImageView>(R.id.sources_poster)
        val titleView = view.findViewById<TextView>(R.id.sources_title)
        val subtitleView = view.findViewById<TextView>(R.id.sources_subtitle)
        val recyclerView = view.findViewById<RecyclerView>(R.id.sources_recycler_view)
        val retryButton = view.findViewById<Button>(R.id.sources_retry_button)

        loadingIndicator = view.findViewById(R.id.sources_loading_indicator)
        emptyView = view.findViewById(R.id.sources_empty_view)
        filterContainer = view.findViewById(R.id.sources_filter_container)
        scrapersContainer = view.findViewById(R.id.sources_scrapers_container)
        scrapersSection = view.findViewById(R.id.sources_scrapers_section)

        backButton?.setOnClickListener {
            dismissAllowingStateLoss()
        }

        refreshButton?.setOnClickListener {
            startScraping(forceRefresh = true)
        }

        retryButton?.setOnClickListener {
            startScraping(forceRefresh = true)
        }

        val ep = currentEpisode
        val resp = currentResponse

        val displayTitle = resp?.name ?: ep?.name ?: ""
        titleView?.text = displayTitle

        val season = ep?.season
        val episodeNum = ep?.episode
        if (season != null && episodeNum != null && season > 0) {
            val epName = ep.name
            subtitleView?.text = if (!epName.isNullOrBlank() && epName != displayTitle) {
                "Season $season • Episode $episodeNum: $epName"
            } else {
                "Season $season • Episode $episodeNum"
            }
            subtitleView?.isVisible = true
        } else if (resp?.year != null) {
            subtitleView?.text = "${resp.year}"
            subtitleView?.isVisible = true
        } else {
            subtitleView?.isVisible = false
        }

        val posterUrl = ep?.poster ?: resp?.posterUrl
        if (!posterUrl.isNullOrBlank()) {
            posterView?.loadImage(posterUrl)
        }

        sourcesAdapter = StreamSourcesAdapter { selectedLink ->
            playSelectedLink(selectedLink)
        }
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = sourcesAdapter

        startScraping(forceRefresh = false)
    }

    private fun startScraping(forceRefresh: Boolean = false) {
        scrapingJob?.cancel()
        isScrapingComplete = false
        loadingIndicator?.isVisible = true
        emptyView?.isVisible = false

        if (forceRefresh) {
            providerLinks.clear()
            allLoadedLinks.clear()
            allLoadedSubs.clear()
            fetchingScrapers.clear()
            activeScrapers.clear()
            updateFilterChips()
            updateSourcesList()
        }

        val episode = currentEpisode ?: return

        // Pre-discover scrapers based on media type (anime vs general, series vs movie)
        val tmdbLink = tryParseJson<TmdbLink>(episode.data)
        val isAnime = tmdbLink?.isAnime ?: false
        val isTv = (tmdbLink?.season != null && tmdbLink.episode != null) || ((episode.season ?: 0) > 0)
        val initialScrapers = CrossTmdbProvider.getAvailableScrapers(isAnime, isTv)
        activeScrapers.addAll(initialScrapers)
        fetchingScrapers.addAll(initialScrapers)
        updateActiveScrapersUi()
        updateFilterChips()
        updateSourcesList()

        CrossTmdbProvider.scraperListener = object : CrossTmdbProvider.ScraperListener {
            override fun onScrapersDiscovered(scrapers: List<String>) {
                view?.post {
                    activeScrapers.addAll(scrapers)
                    fetchingScrapers.addAll(scrapers)
                    updateActiveScrapersUi()
                    updateFilterChips()
                    updateSourcesList()
                }
            }

            override fun onScraperStarted(scraper: String) {
                view?.post {
                    activeScrapers.add(scraper)
                    fetchingScrapers.add(scraper)
                    updateActiveScrapersUi()
                    updateSourcesList()
                }
            }

            override fun onScraperFinished(scraper: String, linksCount: Int) {
                view?.post {
                    fetchingScrapers.remove(scraper)
                    updateSourcesList()
                }
            }
        }

        val tempGenerator = RepoLinkGenerator(listOf(episode), page = currentResponse)

        scrapingJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                tempGenerator.generateLinks(
                    clearCache = forceRefresh,
                    sourceTypes = LOADTYPE_ALL,
                    callback = { (link, _) ->
                        if (link != null && isValidLink(link)) {
                            lifecycleScope.launch(Dispatchers.Main) {
                                handleIncomingLink(link)
                            }
                        }
                    },
                    subtitleCallback = { sub ->
                        allLoadedSubs.add(sub)
                    },
                    offset = 0,
                    isCasting = false
                )
            } catch (e: Exception) {
                logError(e)
            } finally {
                withContext(Dispatchers.Main) {
                    isScrapingComplete = true
                    loadingIndicator?.isVisible = false
                    fetchingScrapers.clear()
                    updateSourcesList()
                    updateFilterChips()
                }
            }
        }
    }

    private fun isValidLink(link: ExtractorLink): Boolean {
        if (link.url.isBlank()) return false
        if (com.lagradost.cloudstream3.utils.DeadLinkManager.isDead(link.url)) return false
        val nameLower = link.name.lowercase()
        val sourceLower = link.source.lowercase()
        if (nameLower.contains("netflix") || sourceLower.contains("netflix") ||
            nameLower.contains("netmirror") || sourceLower.contains("netmirror")
        ) return false
        return true
    }

    private fun handleIncomingLink(link: ExtractorLink) {
        if (allLoadedLinks.any { it.url == link.url }) return
        allLoadedLinks.add(link)

        val provider = link.source.ifBlank { "Stream" }
        val list = providerLinks.getOrPut(provider) { mutableListOf() }
        list.add(link)
        fetchingScrapers.remove(provider)

        updateSourcesList()
        updateFilterChips()
    }

    @SuppressLint("InflateParams")
    private fun updateActiveScrapersUi() {
        val container = scrapersContainer ?: return
        container.removeAllViews()

        if (activeScrapers.isEmpty()) {
            scrapersSection?.isVisible = false
            return
        }

        scrapersSection?.isVisible = true
        val inflater = LayoutInflater.from(context ?: return)

        for (scraper in activeScrapers) {
            val chip = inflater.inflate(R.layout.item_active_scraper_chip, container, false) as TextView
            chip.text = scraper
            container.addView(chip)
        }
    }

    @SuppressLint("InflateParams")
    private fun updateFilterChips() {
        val container = filterContainer ?: return
        container.removeAllViews()
        val inflater = LayoutInflater.from(context ?: return)

        // "All" chip
        val allChip = inflater.inflate(R.layout.item_stream_filter_chip, container, false) as TextView
        allChip.text = "All"
        styleFilterChip(allChip, selectedFilter == null)
        allChip.setOnClickListener {
            selectedFilter = null
            updateFilterChips()
            updateSourcesList()
        }
        container.addView(allChip)

        // Provider chips for providers with links or known scrapers
        val providersWithLinks = providerLinks.filter { it.value.isNotEmpty() }.keys
        val filterOptions = providersWithLinks.ifEmpty { activeScrapers }

        for (provider in filterOptions) {
            val chip = inflater.inflate(R.layout.item_stream_filter_chip, container, false) as TextView
            chip.text = provider
            styleFilterChip(chip, selectedFilter == provider)
            chip.setOnClickListener {
                selectedFilter = if (selectedFilter == provider) null else provider
                updateFilterChips()
                updateSourcesList()
            }
            container.addView(chip)
        }
    }

    private fun styleFilterChip(chip: TextView, isSelected: Boolean) {
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_filter_chip_selected)
            chip.setTextColor(0xFF000000.toInt())
        } else {
            chip.setBackgroundResource(R.drawable.bg_filter_chip_unselected)
            chip.setTextColor(0xFFFFFFFF.toInt())
        }
    }

    private fun updateSourcesList() {
        val items = mutableListOf<StreamSourceItem>()

        val displayedProviders = if (selectedFilter != null) {
            listOf(selectedFilter!!)
        } else {
            val set = linkedSetOf<String>()
            // Providers that found links
            set.addAll(providerLinks.filter { it.value.isNotEmpty() }.keys)
            // While still scraping: show scrapers currently fetching
            if (!isScrapingComplete) {
                set.addAll(fetchingScrapers)
            }
            // Once scraping completes: providers without links are omitted!
            set.toList()
        }

        for (provider in displayedProviders) {
            val links = providerLinks[provider] ?: emptyList()
            val isFetching = fetchingScrapers.contains(provider) && !isScrapingComplete

            if (links.isNotEmpty() || isFetching) {
                items.add(StreamSourceItem.Header(provider, isFetching))
                for (link in links) {
                    items.add(StreamSourceItem.Link(link, provider))
                }
            }
        }

        sourcesAdapter?.submitList(items)
        emptyView?.isVisible = isScrapingComplete && allLoadedLinks.isEmpty()
    }

    private fun playSelectedLink(selectedLink: ExtractorLink) {
        val act = activity ?: return
        val episode = currentEpisode ?: return
        val generator = currentGenerator ?: RepoLinkGenerator(listOf(episode), page = currentResponse)

        // 1. Populate RepoLinkGenerator.cache with selected link first!
        synchronized(RepoLinkGenerator.cache) {
            val cacheItem = RepoLinkGenerator.cache.getOrPut(episode.apiName to episode.id) {
                Cache(mutableSetOf(), mutableSetOf(), unixTime, true)
            }
            val orderedLinks = linkedSetOf<ExtractorLink>()
            orderedLinks.add(selectedLink)
            orderedLinks.addAll(allLoadedLinks)

            cacheItem.linkCache.clear()
            cacheItem.linkCache.addAll(orderedLinks)
            cacheItem.subtitleCache.clear()
            cacheItem.subtitleCache.addAll(allLoadedSubs)
            cacheItem.saturated = true
            cacheItem.lastCachedTimestamp = unixTime
        }

        // 2. Dismiss dialog
        dismissAllowingStateLoss()

        // 3. Start generator player!
        act.navigate(
            R.id.global_to_navigation_player,
            GeneratorPlayer.newInstance(
                generator,
                currentIndex,
                syncData
            )
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        scrapingJob?.cancel()
        CrossTmdbProvider.scraperListener = null
        pendingEpisode = null
        pendingResponse = null
        pendingGenerator = null
        pendingSyncData = null
    }
}

sealed class StreamSourceItem {
    data class Header(val providerName: String, val isFetching: Boolean) : StreamSourceItem()
    data class Link(val link: ExtractorLink, val providerName: String) : StreamSourceItem()
}

class StreamSourcesAdapter(
    private val onLinkClick: (ExtractorLink) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_HEADER = 0
        const val TYPE_LINK = 1
    }

    private var items: List<StreamSourceItem> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newItems: List<StreamSourceItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is StreamSourceItem.Header -> TYPE_HEADER
            is StreamSourceItem.Link -> TYPE_LINK
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            val view = inflater.inflate(R.layout.item_stream_source_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_stream_source_card, parent, false)
            LinkViewHolder(view, onLinkClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is StreamSourceItem.Header -> (holder as HeaderViewHolder).bind(item)
            is StreamSourceItem.Link -> (holder as LinkViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.header_provider_title)
        private val status: TextView = itemView.findViewById(R.id.header_provider_status)

        fun bind(header: StreamSourceItem.Header) {
            title.text = header.providerName
            status.isVisible = header.isFetching
            status.text = if (header.isFetching) "Fetching..." else ""
        }
    }

    class LinkViewHolder(
        itemView: View,
        private val onLinkClick: (ExtractorLink) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.source_name)
        private val quality: TextView = itemView.findViewById(R.id.source_quality)
        private val badge: TextView = itemView.findViewById(R.id.source_badge)

        fun bind(linkItem: StreamSourceItem.Link) {
            val link = linkItem.link
            val cleanName = link.name
                .replace("[${linkItem.providerName}]", "")
                .trim()
                .ifBlank { linkItem.providerName }

            title.text = cleanName

            val qualityStr = Qualities.getStringByInt(link.quality)
            quality.text = if (qualityStr.isNotBlank() && qualityStr != "Unknown") {
                "$qualityStr • Unknown"
            } else {
                qualityStr.ifBlank { "Unknown" }
            }

            if (qualityStr.isNotBlank() && qualityStr != "Unknown") {
                badge.text = qualityStr
                badge.isVisible = true
            } else {
                badge.isVisible = false
            }

            itemView.setOnClickListener {
                onLinkClick(link)
            }
        }
    }
}
