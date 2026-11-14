package {{packageName}}.core.data.di

import {{packageName}}.core.data.InMemoryGreetingRepository
import {{packageName}}.core.domain.repository.GreetingRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun bindGreetingRepository(impl: InMemoryGreetingRepository): GreetingRepository
}
