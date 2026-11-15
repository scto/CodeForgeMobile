package {{packageName}}.feature.player

import androidx.lifecycle.ViewModel
import {{packageName}}.core.domain.model.PlaybackState
import {{packageName}}.core.domain.usecase.PlaybackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Aktionen des Players. */
sealed interface PlayerUiEvent {
    data object TogglePlay : PlayerUiEvent
    data class SeekTo(val positionSec: Int) : PlayerUiEvent
    data class Skip(val deltaSec: Int) : PlayerUiEvent
    data object CycleSpeed : PlayerUiEvent
}

/** Player und Mini-Player teilen sich den Wiedergabezustand. */
@HiltViewModel
class PlayerViewModel @Inject constructor(private val playback: PlaybackUseCase) : ViewModel() {
    val uiState: StateFlow<PlaybackState> = playback.state

    fun onEvent(event: PlayerUiEvent) {
        when (event) {
            PlayerUiEvent.TogglePlay -> playback.togglePlay()
            is PlayerUiEvent.SeekTo -> playback.seekTo(event.positionSec)
            is PlayerUiEvent.Skip -> playback.skip(event.deltaSec)
            PlayerUiEvent.CycleSpeed -> playback.cycleSpeed()
        }
    }

    companion object {
        /** Sprungweite der Spul-Knöpfe in Sekunden. */
        const val SKIP_BACK_SEC = 15
        const val SKIP_FORWARD_SEC = 30
    }
}
