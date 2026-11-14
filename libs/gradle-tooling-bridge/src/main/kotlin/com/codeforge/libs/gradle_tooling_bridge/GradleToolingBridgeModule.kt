// Modul: :libs:gradle-tooling-bridge
package com.codeforge.libs.gradle_tooling_bridge

import com.codeforge.core.domain.repository.GradleBuildRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Modul der Gradle-Anbindung. */
@Module
@InstallIn(SingletonComponent::class)
abstract class GradleToolingBridgeModule {

    /** Bindet das Build-Repository. */
    @Binds
    @Singleton
    abstract fun bindGradleBuildRepository(
        impl: ProcessGradleBuildRepository
    ): GradleBuildRepository

    /** Bindet die Gradle-Aufräumarbeiten. */
    @Binds
    @Singleton
    abstract fun bindGradleMaintenanceRepository(
        impl: GradleMaintenanceRepositoryImpl
    ): com.codeforge.core.domain.repository.GradleMaintenanceRepository

    /** Gradle-Daemon als Teil der Speicherfreigabe. */
    @Binds
    @dagger.multibindings.IntoSet
    abstract fun bindGradleDaemonMemoryReleaser(impl: GradleDaemonMemoryReleaser): com.codeforge.core.domain.runtime.MemoryReleaser
}
