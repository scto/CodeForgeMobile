package {{packageName}}.core.domain.usecase

import {{packageName}}.core.domain.model.Accent
import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.PlaybackState
import {{packageName}}.core.domain.model.ThemeMode
import {{packageName}}.core.domain.repository.PlaybackController
import {{packageName}}.core.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** Spielt eine Folge mit der eingestellten Standardgeschwindigkeit. */
class PlayEpisodeUseCase @Inject constructor(
    private val findEpisode: FindEpisodeUseCase,
    private val controller: PlaybackController,
    private val settings: SettingsRepository
) {
    suspend operator fun invoke(episodeId: Long) {
        val item = findEpisode(episodeId) ?: return
        val current = controller.state.value
        if (current.item?.episode?.id == episodeId) {
            if (!current.isPlaying) controller.togglePlay()
            return
        }
        controller.play(item, settings.settings.first().playbackSpeed)
    }
}

/** Zustand und Steuerung der laufenden Wiedergabe. */
class PlaybackUseCase @Inject constructor(private val controller: PlaybackController) {
    val state: StateFlow<PlaybackState> get() = controller.state

    fun togglePlay() = controller.togglePlay()

    fun seekTo(positionSec: Int) = controller.seekTo(positionSec)

    fun skip(deltaSec: Int) = controller.skip(deltaSec)

    /** Nächste Geschwindigkeit aus [AppSettings.SPEEDS] (zyklisch). */
    fun cycleSpeed() {
        val speeds = AppSettings.SPEEDS
        val next = speeds[(speeds.indexOf(controller.state.value.speed) + 1) % speeds.size]
        controller.setSpeed(next)
    }
}

/** Liefert die Einstellungen. */
class ObserveSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    operator fun invoke(): Flow<AppSettings> = repository.settings
}

/** Ändert einzelne Einstellungen. */
class UpdateSettingsUseCase @Inject constructor(private val repository: SettingsRepository) {
    suspend fun themeMode(mode: ThemeMode) = repository.setThemeMode(mode)

    suspend fun accent(accent: Accent) = repository.setAccent(accent)

    suspend fun playbackSpeed(speed: Float) = repository.setPlaybackSpeed(speed)
}
