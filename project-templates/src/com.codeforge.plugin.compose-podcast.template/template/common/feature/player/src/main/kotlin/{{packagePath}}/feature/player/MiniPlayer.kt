package {{packageName}}.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.designsystem.component.GradientCover
import {{packageName}}.core.designsystem.icon.AppIcons
import {{packageName}}.core.designsystem.theme.AppGradients
import {{packageName}}.core.designsystem.theme.AppTheme
import {{packageName}}.core.domain.model.PlaybackState
import {{packageName}}.core.resources.R

/** Mini-Player über der unteren Leiste; erscheint, sobald eine Folge geladen ist. */
@Composable
fun MiniPlayerRoute(onOpen: () -> Unit, modifier: Modifier = Modifier, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AnimatedVisibility(visible = state.item != null, enter = expandVertically(), exit = shrinkVertically(), modifier = modifier) {
        MiniPlayer(state, onTogglePlay = { viewModel.onEvent(PlayerUiEvent.TogglePlay) }, onOpen = onOpen)
    }
}

/** Kompakte Wiedergabeleiste mit Fortschrittslinie im Akzentverlauf. */
@Composable
fun MiniPlayer(state: PlaybackState, onTogglePlay: () -> Unit, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val item = state.item ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.large,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.spacing.s, vertical = AppTheme.spacing.xs)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onOpen)
    ) {
        Column {
            Row(
                Modifier.padding(AppTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.m)
            ) {
                GradientCover(seed = item.podcast.seed, label = item.podcast.title, size = 44.dp, shape = MaterialTheme.shapes.small)
                Column(Modifier.weight(1f)) {
                    Text(item.episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        item.podcast.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onTogglePlay) {
                    Icon(
                        if (state.isPlaying) AppIcons.Pause else Icons.Filled.PlayArrow,
                        stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play)
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(state.progress).height(3.dp).background(AppGradients.accent))
            }
        }
    }
}
