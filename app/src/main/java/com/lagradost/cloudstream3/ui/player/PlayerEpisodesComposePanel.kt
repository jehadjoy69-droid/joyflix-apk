package com.lagradost.cloudstream3.ui.player

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.ui.result.ResultEpisode
import com.lagradost.cloudstream3.ui.result.VideoWatchState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlayerEpisodesComposeDialogFragment : DialogFragment() {

    private var episodes: List<ResultEpisode> = emptyList()
    private var currentEpisodeId: Int? = null
    private var currentEpisodeIndex: Int? = null
    private var onEpisodeSelectedListener: ((ResultEpisode, Int) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.DialogFullscreenPlayer)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = Color(0xFFE50914),
                        surface = Color(0xFF1E1E1E),
                        background = Color(0xFF121212)
                    )
                ) {
                    PlayerEpisodesPanel(
                        visible = true,
                        episodes = episodes,
                        currentEpisodeId = currentEpisodeId,
                        currentEpisodeIndex = currentEpisodeIndex,
                        onEpisodeSelected = { ep, idx ->
                            onEpisodeSelectedListener?.invoke(ep, idx)
                            dismissAllowingStateLoss()
                        },
                        onDismiss = { dismissAllowingStateLoss() }
                    )
                }
            }
        }
    }

    companion object {
        const val TAG = "PlayerEpisodesComposeDialog"

        fun show(
            fragmentManager: FragmentManager,
            episodes: List<ResultEpisode>,
            currentEpisodeId: Int? = null,
            currentEpisodeIndex: Int? = null,
            onEpisodeSelected: (ResultEpisode, Int) -> Unit
        ) {
            val existing = fragmentManager.findFragmentByTag(TAG) as? DialogFragment
            if (existing != null && existing.isAdded) return
            val dialog = PlayerEpisodesComposeDialogFragment().apply {
                this.episodes = episodes
                this.currentEpisodeId = currentEpisodeId
                this.currentEpisodeIndex = currentEpisodeIndex
                this.onEpisodeSelectedListener = onEpisodeSelected
            }
            dialog.show(fragmentManager, TAG)
        }
    }
}

@Composable
fun PlayerEpisodesPanel(
    visible: Boolean,
    episodes: List<ResultEpisode>,
    currentEpisodeId: Int? = null,
    currentEpisodeIndex: Int? = null,
    onEpisodeSelected: (ResultEpisode, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val backgroundInteraction = remember { MutableInteractionSource() }
    val panelInteraction = remember { MutableInteractionSource() }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160)),
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.54f))
                .clickable(
                    interactionSource = backgroundInteraction,
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            val panelWidth = minOf(maxWidth, 520.dp)
            val shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)

            AnimatedVisibility(
                visible = visible,
                enter = slideInHorizontally(tween(250)) { it },
                exit = slideOutHorizontally(tween(200)) { it },
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Column(
                    modifier = Modifier
                        .width(panelWidth)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(Color(0xFF161616))
                        .clickable(
                            interactionSource = panelInteraction,
                            indication = null,
                            onClick = {},
                        )
                        .padding(20.dp),
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Episodes",
                            color = Color(0xFFF5F7F8),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF242424))
                                .clickable(onClick = onDismiss)
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_close_24),
                                    contentDescription = "Close",
                                    tint = Color(0xFFB0B0B0),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Close",
                                    color = Color(0xFFB0B0B0),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    EpisodesListPanelContent(
                        episodes = episodes,
                        currentEpisodeId = currentEpisodeId,
                        currentEpisodeIndex = currentEpisodeIndex,
                        onEpisodeSelected = onEpisodeSelected,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodesListPanelContent(
    episodes: List<ResultEpisode>,
    currentEpisodeId: Int?,
    currentEpisodeIndex: Int?,
    onEpisodeSelected: (ResultEpisode, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groupedEpisodes = remember(episodes) {
        if (episodes.any { (it.season ?: 0) > 0 }) {
            episodes.groupBy { it.season?.coerceAtLeast(0) ?: 0 }
        } else {
            mapOf(1 to episodes)
        }
    }

    val availableSeasons = remember(groupedEpisodes) {
        groupedEpisodes.keys.filter { it > 0 }.sorted() + groupedEpisodes.keys.filter { it == 0 }
    }

    val currentEpisodeSeason = remember(episodes, currentEpisodeId, currentEpisodeIndex) {
        val currentEp = episodes.firstOrNull {
            (currentEpisodeId != null && it.id == currentEpisodeId) ||
            (currentEpisodeIndex != null && it.index == currentEpisodeIndex)
        }
        currentEp?.season ?: availableSeasons.firstOrNull() ?: 1
    }

    var selectedSeason by remember(currentEpisodeSeason, availableSeasons) {
        mutableIntStateOf(
            when {
                currentEpisodeSeason in availableSeasons -> currentEpisodeSeason
                availableSeasons.isNotEmpty() -> availableSeasons.first()
                else -> 1
            }
        )
    }

    val seasonEpisodes = remember(groupedEpisodes, selectedSeason) {
        (groupedEpisodes[selectedSeason] ?: emptyList()).sortedBy { it.episode }
    }

    val seasonListState = rememberLazyListState()
    val episodeListState = rememberLazyListState()
    var positionedSeasonRow by remember(availableSeasons) { mutableStateOf(false) }
    var positionedEpisodeList by remember(selectedSeason) { mutableStateOf(false) }

    LaunchedEffect(selectedSeason, availableSeasons) {
        val index = availableSeasons.indexOf(selectedSeason)
        if (index >= 0) {
            if (positionedSeasonRow) {
                seasonListState.animateScrollToItem(index)
            } else {
                seasonListState.scrollToItem(index)
                positionedSeasonRow = true
            }
        }
    }

    LaunchedEffect(selectedSeason, seasonEpisodes, currentEpisodeId, currentEpisodeIndex) {
        if (seasonEpisodes.isEmpty()) return@LaunchedEffect
        val targetIdx = seasonEpisodes.indexOfFirst {
            (currentEpisodeId != null && it.id == currentEpisodeId) ||
            (currentEpisodeIndex != null && it.index == currentEpisodeIndex)
        }.takeIf { it >= 0 } ?: 0

        if (positionedEpisodeList) {
            episodeListState.animateScrollToItem(targetIdx)
        } else {
            episodeListState.scrollToItem(targetIdx)
            positionedEpisodeList = true
        }
    }

    Column(modifier = modifier) {
        // Season Chips Row
        if (availableSeasons.size > 1) {
            LazyRow(
                state = seasonListState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
            ) {
                items(availableSeasons, key = { it }) { season ->
                    val isSelected = selectedSeason == season
                    val shape = RoundedCornerShape(24.dp)
                    val label = if (season == 0) "Specials" else "Season $season"

                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .background(if (isSelected) Color(0xFFE50914) else Color(0xFF242424))
                            .border(
                                1.dp,
                                if (isSelected) Color.Transparent else Color(0xFF383838),
                                shape,
                            )
                            .clickable { selectedSeason = season }
                            .padding(horizontal = 18.dp, vertical = 9.dp),
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFFB0B0B0),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        if (seasonEpisodes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No episodes available",
                    color = Color(0xFF888888),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                state = episodeListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp),
            ) {
                itemsIndexed(
                    items = seasonEpisodes,
                    key = { _, episode -> episode.id },
                ) { _, episode ->
                    val isCurrent = (currentEpisodeId != null && episode.id == currentEpisodeId) ||
                                    (currentEpisodeIndex != null && episode.index == currentEpisodeIndex)
                    val isWatched = episode.videoWatchState == VideoWatchState.Watched

                    EpisodeRow(
                        episode = episode,
                        isCurrent = isCurrent,
                        isWatched = isWatched,
                        onClick = {
                            val originalIndex = episodes.indexOfFirst { it.id == episode.id }.takeIf { it >= 0 } ?: episode.index
                            onEpisodeSelected(episode, originalIndex)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: ResultEpisode,
    isCurrent: Boolean,
    isWatched: Boolean,
    onClick: () -> Unit,
) {
    val cardShape = RoundedCornerShape(16.dp)
    val episodeCode = buildString {
        if (episode.season != null && episode.season > 0) {
            append("S${episode.season} ")
        }
        append("E${episode.episode}")
    }

    val formattedDate = remember(episode.airDate) {
        episode.airDate?.takeIf { it > 0 }?.let { dateMs ->
            try {
                SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(dateMs))
            } catch (_: Throwable) {
                null
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(Color(0xFF1E1E1E))
            .then(
                if (isCurrent) {
                    Modifier.border(width = 2.dp, color = Color(0xFFE50914), shape = cardShape)
                } else {
                    Modifier.border(width = 1.dp, color = Color(0xFF2A2A2A), shape = cardShape)
                }
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Thumbnail Container
        Box(
            modifier = Modifier
                .width(130.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2A2A)),
        ) {
            if (!episode.poster.isNullOrBlank()) {
                AsyncImage(
                    model = episode.poster,
                    contentDescription = episode.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }

            // Episode Code Badge (Bottom Start)
            Text(
                text = episodeCode,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall,
            )

            // Watched Checkmark Badge (Top End)
            if (isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_check_24),
                        contentDescription = "Watched",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }

        // Episode Information
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = episode.name ?: "Episode ${episode.episode}",
                color = Color(0xFFF5F7F8),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (!formattedDate.isNullOrBlank()) {
                Text(
                    text = formattedDate,
                    color = Color(0xFF969CA3),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (!episode.description.isNullOrBlank()) {
                Text(
                    text = episode.description,
                    color = Color(0xFFB0B0B0),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
