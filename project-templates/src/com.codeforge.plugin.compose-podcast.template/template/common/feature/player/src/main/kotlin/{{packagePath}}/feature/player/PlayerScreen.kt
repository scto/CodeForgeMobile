package {{packageName}}.feature.player

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.designsystem.component.GradientCover
import {{packageName}}.core.designsystem.component.GradientIconButton
import {{packageName}}.core.designsystem.component.PillChip
import {{packageName}}.core.designsystem.icon.AppIcons
import {{packageName}}.core.designsystem.theme.AppGradients
import {{packageName}}.core.designsystem.theme.AppTheme
import {{packageName}}.core.designsystem.theme.MonoNumbers
import {{packageName}}.core.domain.model.Category
import {{packageName}}.core.domain.model.Episode
import {{packageName}}.core.domain.model.EpisodeItem
import {{packageName}}.core.domain.model.PlaybackState
import {{packageName}}.core.domain.model.Podcast
import {{packageName}}.core.resources.R
import kotlin.math.roundToInt

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun PlayerRoute(onClose: () -> Unit, modifier: Modifier = Modifier, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlayerScreen(state, viewModel::onEvent, onClose, modifier)
}

/** Vollbild-Player: Hintergrund im Cover-Verlauf, „atmendes“ Cover, Positionsregler, Steuerung, Tempo. */
@Composable
fun PlayerScreen(state: PlaybackState, onEvent: (PlayerUiEvent) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val item = state.item
    val seed = item?.podcast?.seed ?: 0
    Box(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(AppGradients.forSeed(seed))
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background), startY = 300f))
        )
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(AppTheme.spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.xl)
        ) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.player_close), tint = Color.White)
            }
            if (item == null) {
                Text(stringResource(R.string.player_nothing), style = MaterialTheme.typography.titleMedium)
                return@Column
            }
            BreathingCover(item, isPlaying = state.isPlaying)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    item.episode.title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(item.podcast.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            PositionSlider(state, onSeek = { onEvent(PlayerUiEvent.SeekTo(it)) })
            Controls(state, onEvent)
            PillChip(label = stringResource(R.string.player_speed, state.speed.toString().removeSuffix(".0")), selected = true, onClick = {
                onEvent(PlayerUiEvent.CycleSpeed)
            })
        }
    }
}

@Composable
private fun BreathingCover(item: EpisodeItem, isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "breathing")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1600), RepeatMode.Reverse),
        label = "pulse"
    )
    val scale by animateFloatAsState(if (isPlaying) pulse else 0.9f, label = "coverScale")
    GradientCover(
        seed = item.podcast.seed,
        label = item.podcast.title,
        size = 260.dp,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.scale(scale)
    )
}

@Composable
private fun PositionSlider(state: PlaybackState, onSeek: (Int) -> Unit) {
    val duration = state.item?.episode?.durationSec ?: 0
    var dragging by remember { mutableStateOf<Float?>(null) }
    Column {
        Slider(
            value = dragging ?: state.positionSec.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek(it.roundToInt()) }
                dragging = null
            },
            valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
            colors = SliderDefaults.colors(thumbColor = AppTheme.colors.gradientEnd, activeTrackColor = AppTheme.colors.gradientStart)
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(state.positionSec), style = MonoNumbers, modifier = Modifier.weight(1f))
            Text("-" + formatTime(duration - state.positionSec), style = MonoNumbers)
        }
    }
}

@Composable
private fun Controls(state: PlaybackState, onEvent: (PlayerUiEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.xl)) {
        IconButton(onClick = { onEvent(PlayerUiEvent.Skip(-PlayerViewModel.SKIP_BACK_SEC)) }, modifier = Modifier.size(56.dp)) {
            Icon(AppIcons.Rewind, stringResource(R.string.player_back, PlayerViewModel.SKIP_BACK_SEC), modifier = Modifier.size(32.dp))
        }
        GradientIconButton(
            icon = if (state.isPlaying) AppIcons.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (state.isPlaying) R.string.player_pause else R.string.player_play),
            onClick = { onEvent(PlayerUiEvent.TogglePlay) },
            size = 80.dp
        )
        IconButton(onClick = { onEvent(PlayerUiEvent.Skip(PlayerViewModel.SKIP_FORWARD_SEC)) }, modifier = Modifier.size(56.dp)) {
            Icon(
                AppIcons.FastForward,
                stringResource(R.string.player_forward, PlayerViewModel.SKIP_FORWARD_SEC),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

/** „m:ss“ bzw. „h:mm:ss“. */
internal fun formatTime(totalSec: Int): String {
    val sec = totalSec.coerceAtLeast(0)
    val hours = sec / SECONDS_PER_HOUR
    val minutes = sec % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = sec % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600

@Preview
@Composable
private fun PlayerScreenPreview() {
    val item = EpisodeItem(Episode(1, 1, "Neuanfang", "", 1800, 1), Podcast(1, "Compose Kitchen", "Lena", Category.TECH, "", 0))
    AppTheme { PlayerScreen(PlaybackState(item, positionSec = 420, isPlaying = true), onEvent = {}, onClose = {}) }
}
