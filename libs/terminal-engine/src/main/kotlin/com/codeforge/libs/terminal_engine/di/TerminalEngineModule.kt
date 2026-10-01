// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine.di

import com.codeforge.core.domain.repository.DistroBootstrapRepository
import com.codeforge.core.domain.repository.SdkRepository
import com.codeforge.core.domain.repository.TerminalSessionRepository
import com.codeforge.libs.terminal_engine.CommandlineSdkRepository
import com.codeforge.libs.terminal_engine.DistroBootstrapRepositoryImpl
import com.codeforge.libs.terminal_engine.ProotExecutor
import com.codeforge.libs.terminal_engine.ProotExecutorImpl
import com.codeforge.libs.terminal_engine.TerminalSessionRepositoryImpl
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

    @Binds
    @Singleton
    abstract fun bindTerminalSessionRepository(
        impl: TerminalSessionRepositoryImpl
    ): TerminalSessionRepository

    @Binds
    @Singleton
    abstract fun bindSdkRepository(
        impl: CommandlineSdkRepository
    ): SdkRepository

    @Binds
    @Singleton
    abstract fun bindProotExecutor(
        impl: ProotExecutorImpl
    ): ProotExecutor
}
