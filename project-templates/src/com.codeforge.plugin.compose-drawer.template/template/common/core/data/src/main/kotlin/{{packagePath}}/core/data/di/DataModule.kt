package {{packageName}}.core.data.di

import {{packageName}}.core.data.InMemoryNoteRepository
import {{packageName}}.core.data.InMemorySettingsRepository
import {{packageName}}.core.domain.repository.NoteRepository
import {{packageName}}.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun bindNoteRepository(impl: InMemoryNoteRepository): NoteRepository

    @Binds
    abstract fun bindSettingsRepository(impl: InMemorySettingsRepository): SettingsRepository
}
