package {{packageName}}.core.domain.repository

import {{packageName}}.core.domain.model.Accent
import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.Episode
import {{packageName}}.core.domain.model.EpisodeItem
import {{packageName}}.core.domain.model.PlaybackState
import {{packageName}}.core.domain.model.Podcast
import {{packageName}}.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Katalog und Abos. */
interface PodcastRepository {
    val podcasts: Flow<List<Podcast>>
    val episodes: Flow<List<Episode>>
    val subscriptions: Flow<Set<Long>>

    suspend fun toggleSubscription(podcastId: Long)
}

/** Steuert die Wiedergabe (hier simuliert; mit Media3 ersetzbar). */
interface PlaybackController {
    val state: StateFlow<PlaybackState>

    fun play(item: EpisodeItem, speed: Float)

    fun togglePlay()

    fun seekTo(positionSec: Int)

    fun skip(deltaSec: Int)

    fun setSpeed(speed: Float)
}

/** Gespeicherte Einstellungen. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setAccent(accent: Accent)

    suspend fun setPlaybackSpeed(speed: Float)
}
