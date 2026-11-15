package {{packageName}}.core.data

import {{packageName}}.core.domain.model.EpisodeItem
import {{packageName}}.core.domain.model.PlaybackState
import {{packageName}}.core.domain.repository.PlaybackController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simulierte Wiedergabe: zählt die Position im Takt der Geschwindigkeit hoch. Für echtes Audio durch einen
 * Media3-`ExoPlayer` ersetzen – die Schnittstelle [PlaybackController] bleibt gleich.
 */
@Singleton
class SimulatedPlaybackController @Inject constructor(@param:ApplicationScope private val scope: CoroutineScope) : PlaybackController {
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private var ticker: Job? = null

    override fun play(item: EpisodeItem, speed: Float) {
        mutableState.value = PlaybackState(item = item, positionSec = 0, isPlaying = true, speed = speed)
        startTicker()
    }

    override fun togglePlay() {
        val current = mutableState.value
        val item = current.item ?: return
        val atEnd = current.positionSec >= item.episode.durationSec
        mutableState.update { it.copy(isPlaying = !it.isPlaying, positionSec = if (atEnd) 0 else it.positionSec) }
        if (mutableState.value.isPlaying) startTicker() else ticker?.cancel()
    }

    override fun seekTo(positionSec: Int) = mutableState.update { it.copy(positionSec = positionSec.coerceIn(0, it.duration)) }

    override fun skip(deltaSec: Int) = seekTo(mutableState.value.positionSec + deltaSec)

    override fun setSpeed(speed: Float) = mutableState.update { it.copy(speed = speed) }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var carry = 0f
            while (isActive && mutableState.value.isPlaying) {
                delay(TICK_MS)
                carry += mutableState.value.speed
                val whole = carry.toInt()
                carry -= whole
                mutableState.update { state ->
                    val position = (state.positionSec + whole).coerceAtMost(state.duration)
                    state.copy(positionSec = position, isPlaying = position < state.duration)
                }
            }
        }
    }

    private val PlaybackState.duration: Int get() = item?.episode?.durationSec ?: 0

    private companion object {
        const val TICK_MS = 1_000L
    }
}
