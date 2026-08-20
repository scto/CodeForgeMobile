// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import com.codeforge.core.domain.repository.DistroBootstrapRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TerminalEngineModule {
    @Binds
    @Singleton
    abstract fun bindDistroBootstrapRepository(
        impl: DistroBootstrapRepositoryImpl
    ): DistroBootstrapRepository
}
