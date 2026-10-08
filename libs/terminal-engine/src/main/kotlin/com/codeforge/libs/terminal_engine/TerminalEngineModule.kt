// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import com.codeforge.core.domain.repository.TermuxEnvironmentRepository
import com.codeforge.core.domain.repository.SdkRepository
import com.codeforge.core.domain.repository.TerminalSessionRepository
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
    abstract fun bindTermuxEnvironmentRepository(
        impl: TermuxEnvironmentRepositoryImpl
    ): TermuxEnvironmentRepository

    @Binds
    @Singleton
    abstract fun bindTerminalSessionRepository(
        impl: TerminalSessionRepositoryImpl
    ): TerminalSessionRepository

    @Binds
    @Singleton
    abstract fun bindSdkRepository(
        impl: TermuxScriptSdkRepository
    ): SdkRepository
}
