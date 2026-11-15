package {{packageName}}.core.data.di

import {{packageName}}.core.data.InMemoryProfileRepository
import {{packageName}}.core.data.InMemoryTaskRepository
import {{packageName}}.core.domain.repository.ProfileRepository
import {{packageName}}.core.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun bindTaskRepository(impl: InMemoryTaskRepository): TaskRepository

    @Binds
    abstract fun bindProfileRepository(impl: InMemoryProfileRepository): ProfileRepository
}
