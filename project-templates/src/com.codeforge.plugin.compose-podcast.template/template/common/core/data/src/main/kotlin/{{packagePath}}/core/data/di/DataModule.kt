package {{packageName}}.core.data.di

import {{packageName}}.core.data.ApplicationScope
import {{packageName}}.core.data.InMemoryPodcastRepository
import {{packageName}}.core.data.InMemorySettingsRepository
import {{packageName}}.core.data.SimulatedPlaybackController
import {{packageName}}.core.domain.repository.PlaybackController
import {{packageName}}.core.domain.repository.PodcastRepository
import {{packageName}}.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun bindPodcastRepository(impl: InMemoryPodcastRepository): PodcastRepository

    @Binds
    abstract fun bindSettingsRepository(impl: InMemorySettingsRepository): SettingsRepository

    @Binds
    abstract fun bindPlaybackController(impl: SimulatedPlaybackController): PlaybackController

    companion object {
        /** Lebt so lange wie die App (z. B. für die laufende Wiedergabe). */
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
