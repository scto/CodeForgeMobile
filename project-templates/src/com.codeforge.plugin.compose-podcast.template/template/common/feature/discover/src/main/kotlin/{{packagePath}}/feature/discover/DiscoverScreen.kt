package {{packageName}}.feature.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.designsystem.component.GlassCard
import {{packageName}}.core.designsystem.component.GradientButton
import {{packageName}}.core.designsystem.component.GradientCover
import {{packageName}}.core.designsystem.component.GradientHeader
import {{packageName}}.core.designsystem.component.GradientIconButton
import {{packageName}}.core.designsystem.component.PillChip
import {{packageName}}.core.designsystem.component.SectionHeader
import {{packageName}}.core.designsystem.icon.AppIcons
import {{packageName}}.core.designsystem.theme.AppGradients
import {{packageName}}.core.designsystem.theme.AppTheme
import {{packageName}}.core.domain.model.Category
import {{packageName}}.core.domain.model.DiscoverFeed
import {{packageName}}.core.domain.model.EpisodeItem
import {{packageName}}.core.domain.model.PlaybackState
import {{packageName}}.core.domain.model.Podcast
import {{packageName}}.core.resources.R
import java.util.Calendar

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun DiscoverRoute(
    onOpenPodcast: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    DiscoverScreen(state, viewModel::onEvent, onOpenPodcast, onOpenPlayer, modifier)
}

/** Startseite: Kopf mit Verlauf, „Weiterhören“, Kategorien, Empfehlung, Trends und neue Folgen. */
@Composable
fun DiscoverScreen(
    state: DiscoverUiState,
    onEvent: (DiscoverUiEvent) -> Unit,
    onOpenPodcast: (Long) -> Unit,
    onOpenPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = AppTheme.spacing
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = spacing.xxl), verticalArrangement = Arrangement.spacedBy(spacing.xl)) {
        item {
            GradientHeader(title = stringResource(R.string.discover_title), subtitle = stringResource(greeting()))
        }
        state.playback.item?.let { item ->
            item {
                ContinueCard(
                    item = item,
                    playback = state.playback,
                    onTogglePlay = { onEvent(DiscoverUiEvent.TogglePlay) },
                    onOpen = onOpenPlayer,
                    modifier = Modifier.padding(horizontal = spacing.l).pullUp(OVERLAP)
                )
            }
        }
        item { CategoryRow(state.category, onSelect = { onEvent(DiscoverUiEvent.CategorySelected(it)) }) }
        state.feed.featured?.let { featured ->
            item { FeaturedCard(featured, onOpen = { onOpenPodcast(featured.id) }, modifier = Modifier.padding(horizontal = spacing.l)) }
        }
        if (state.feed.trending.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.discover_trending))
                LazyRow(contentPadding = PaddingValues(horizontal = spacing.l), horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
                    items(state.feed.trending, key = { it.id }) { podcast -> TrendingCard(podcast) { onOpenPodcast(podcast.id) } }
                }
            }
        }
        if (state.feed.newEpisodes.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.discover_new_episodes)) }
            items(state.feed.newEpisodes, key = { it.episode.id }) { item ->
                EpisodeRow(item, onPlay = {
                    onEvent(DiscoverUiEvent.PlayEpisode(item.episode.id))
                }, modifier = Modifier.padding(horizontal = spacing.l))
            }
        }
    }
}

@Composable
private fun ContinueCard(
    item: EpisodeItem,
    playback: PlaybackState,
    onTogglePlay: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassCard(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onOpen)) {
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.m)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.m)) {
                GradientCover(seed = item.podcast.seed, label = item.podcast.title, size = 56.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.discover_continue),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(item.episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                GradientIconButton(
                    icon = if (playback.isPlaying) AppIcons.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(if (playback.isPlaying) R.string.player_pause else R.string.player_play),
                    onClick = onTogglePlay,
                    size = 48.dp
                )
            }
            LinearProgressIndicator(progress = {
                playback.progress
            }, modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.extraSmall))
        }
    }
}

@Composable
private fun CategoryRow(selected: Category?, onSelect: (Category?) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier,
        contentPadding = PaddingValues(horizontal = AppTheme.spacing.l),
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)
    ) {
        item { PillChip(stringResource(R.string.discover_all), selected == null, { onSelect(null) }) }
        items(Category.entries) { category -> PillChip(stringResource(category.label()), selected == category, { onSelect(category) }) }
    }
}

@Composable
private fun FeaturedCard(podcast: Podcast, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(AppGradients.forSeed(podcast.seed))
            .clickable(onClick = onOpen)
    ) {
        GradientCover(
            seed = podcast.seed + 1,
            label = podcast.title,
            size = 120.dp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = AppTheme.spacing.xl)
        )
        Column(
            Modifier.align(Alignment.CenterStart).padding(AppTheme.spacing.xl).width(180.dp),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.s)
        ) {
            Text(
                stringResource(R.string.discover_featured).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = AppTheme.colors.onGradient
            )
            Text(podcast.title, style = MaterialTheme.typography.headlineSmall, color = AppTheme.colors.onGradient, maxLines = 2)
            GradientButton(text = stringResource(R.string.discover_listen), onClick = onOpen, icon = Icons.Filled.PlayArrow)
        }
    }
}

@Composable
private fun TrendingCard(podcast: Podcast, onClick: () -> Unit) {
    Column(Modifier.width(140.dp).clickable(onClick = onClick)) {
        GradientCover(seed = podcast.seed, label = podcast.title, size = 140.dp)
        Text(
            podcast.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(podcast.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/** Folge mit Cover, Titel, Podcast, Dauer und Abspielknopf. */
@Composable
fun EpisodeRow(item: EpisodeItem, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.m)
    ) {
        GradientCover(seed = item.podcast.seed, label = item.podcast.title, size = 64.dp)
        Column(Modifier.weight(1f)) {
            Text(item.episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.discover_episode_meta, item.podcast.title, item.episode.durationSec / SECONDS_PER_MINUTE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Filled.PlayArrow, stringResource(R.string.player_play), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private const val SECONDS_PER_MINUTE = 60

/** Begrüßung passend zur Tageszeit. */
private fun greeting(): Int = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in MORNING -> R.string.discover_greeting_morning
    in DAY -> R.string.discover_greeting_day
    else -> R.string.discover_greeting_evening
}

private val MORNING = 5..10
private val DAY = 11..17

/** Text der Kategorie. */
fun Category.label(): Int = when (this) {
    Category.TECH -> R.string.category_tech
    Category.SCIENCE -> R.string.category_science
    Category.CULTURE -> R.string.category_culture
    Category.NEWS -> R.string.category_news
    Category.COMEDY -> R.string.category_comedy
}

@Preview
@Composable
private fun DiscoverScreenPreview() {
    val podcast = Podcast(1, "Compose Kitchen", "Lena", Category.TECH, "", 0)
    AppTheme {
        DiscoverScreen(
            state = DiscoverUiState(feed = DiscoverFeed(podcast, listOf(podcast.copy(id = 2, seed = 3)), emptyList()), isLoading = false),
            onEvent = {},
            onOpenPodcast = {},
            onOpenPlayer = {}
        )
    }
}

/** Karte, die in den Kopf hineinragt: verschiebt nach oben und gibt den Platz darunter frei (anders als `offset`). */
private fun Modifier.pullUp(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

private val OVERLAP = 36.dp
