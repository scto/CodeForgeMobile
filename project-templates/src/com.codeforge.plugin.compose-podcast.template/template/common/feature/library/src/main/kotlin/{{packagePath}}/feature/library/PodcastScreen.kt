package {{packageName}}.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.designsystem.component.GradientButton
import {{packageName}}.core.designsystem.component.GradientCover
import {{packageName}}.core.designsystem.theme.AppGradients
import {{packageName}}.core.designsystem.theme.AppTheme
import {{packageName}}.core.domain.model.Category
import {{packageName}}.core.domain.model.Episode
import {{packageName}}.core.domain.model.Podcast
import {{packageName}}.core.domain.model.PodcastDetail
import {{packageName}}.core.resources.R

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun PodcastRoute(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: PodcastViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PodcastScreen(state, viewModel::onEvent, onBack, modifier)
}

/** Podcast-Seite: Kopf im Cover-Verlauf, Abo-Knopf, Beschreibung und Folgenliste. */
@Composable
fun PodcastScreen(state: PodcastUiState, onEvent: (PodcastUiEvent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val detail = state.detail
    if (detail == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val spacing = AppTheme.spacing
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(spacing.m)
    ) {
        item { Header(detail, onBack, onSubscribe = { onEvent(PodcastUiEvent.SubscribeToggled) }) }
        item {
            Text(
                detail.podcast.description,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = spacing.l)
            )
        }
        item {
            Text(
                stringResource(R.string.podcast_episodes),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = spacing.l, top = spacing.l)
            )
        }
        items(detail.episodes, key = { it.id }) { episode ->
            EpisodeItemRow(episode, onPlay = {
                onEvent(PodcastUiEvent.PlayEpisode(episode.id))
            }, modifier = Modifier.padding(horizontal = spacing.l))
        }
    }
}

@Composable
private fun Header(detail: PodcastDetail, onBack: () -> Unit, onSubscribe: () -> Unit) {
    val podcast = detail.podcast
    Box(
        Modifier
            .fillMaxWidth()
            .background(AppGradients.forSeed(podcast.seed))
    ) {
        // Verlauf nach unten in den Hintergrund
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
        )
        Column(
            Modifier.statusBarsPadding().padding(AppTheme.spacing.l),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.m)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = Color.White)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.l), verticalAlignment = Alignment.Bottom) {
                GradientCover(seed = podcast.seed + 2, label = podcast.title, size = 132.dp, shape = MaterialTheme.shapes.large)
                Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xs)) {
                    Text(podcast.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(podcast.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        stringResource(podcast.category.label()),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            AnimatedContent(targetState = detail.subscribed, label = "subscribe") { subscribed ->
                if (subscribed) {
                    OutlinedButton(onClick = onSubscribe) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Text(stringResource(R.string.podcast_subscribed), modifier = Modifier.padding(start = AppTheme.spacing.s))
                    }
                } else {
                    GradientButton(text = stringResource(R.string.podcast_subscribe), onClick = onSubscribe, icon = Icons.Filled.Add)
                }
            }
        }
    }
}

@Composable
private fun EpisodeItemRow(episode: Episode, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(
                    R.string.podcast_episode_meta,
                    pluralStringResource(R.plurals.podcast_days_ago, episode.publishedDaysAgo, episode.publishedDaysAgo),
                    episode.durationSec / SECONDS_PER_MINUTE
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                episode.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        IconButton(onClick = onPlay) {
            Icon(Icons.Filled.PlayArrow, stringResource(R.string.player_play), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private const val SECONDS_PER_MINUTE = 60

private fun Category.label(): Int = when (this) {
    Category.TECH -> R.string.category_tech
    Category.SCIENCE -> R.string.category_science
    Category.CULTURE -> R.string.category_culture
    Category.NEWS -> R.string.category_news
    Category.COMEDY -> R.string.category_comedy
}

@Preview
@Composable
private fun PodcastScreenPreview() {
    val podcast = Podcast(1, "Compose Kitchen", "Lena Fischer", Category.TECH, "Rezepte für schöne Oberflächen.", 0)
    AppTheme {
        PodcastScreen(
            state = PodcastUiState(PodcastDetail(podcast, listOf(Episode(1, 1, "Folge 1", "Inhalt", 1800, 2)), subscribed = false)),
            onEvent = {},
            onBack = {}
        )
    }
}
