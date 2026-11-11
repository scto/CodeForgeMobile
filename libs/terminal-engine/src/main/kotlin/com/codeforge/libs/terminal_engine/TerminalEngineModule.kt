// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import com.codeforge.core.domain.repository.TermuxEnvironmentRepository
import com.codeforge.core.domain.repository.PackageRepository
import com.codeforge.core.domain.repository.SdkRepository
import com.codeforge.core.domain.repository.TerminalSessionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Modul der Terminal-Engine. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TerminalEngineModule {
    /** Bindet das Termux-Umgebungs-Repository. */
    @Binds
    @Singleton
    abstract fun bindTermuxEnvironmentRepository(
        impl: TermuxEnvironmentRepositoryImpl
    ): TermuxEnvironmentRepository

    /** Bindet das Terminal-Sitzungs-Repository. */
    @Binds
    @Singleton
    abstract fun bindTerminalSessionRepository(
        impl: TerminalSessionRepositoryImpl
    ): TerminalSessionRepository

    /** Bindet das SDK-Repository (Skript `codeforge-env`). */
    @Binds
    @Singleton
    abstract fun bindSdkRepository(
        impl: TermuxScriptSdkRepository
    ): SdkRepository

    /** Bindet das Paket-Repository (APT-Repo über `codeforge-env`). */
    @Binds
    @Singleton
    abstract fun bindPackageRepository(
        impl: TermuxScriptPackageRepository
    ): PackageRepository
}
