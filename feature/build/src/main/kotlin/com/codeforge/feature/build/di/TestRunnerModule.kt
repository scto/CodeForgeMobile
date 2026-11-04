// Modul: :feature:build
package com.codeforge.feature.build.di

import com.codeforge.core.domain.repository.TestRunnerRepository
import com.codeforge.feature.build.testrunner.TestRunnerRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Modul des Testläufers. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TestRunnerModule {

    /** Bindet die Implementierung. */
    @Binds
    @Singleton
    abstract fun bindTestRunnerRepository(
        impl: TestRunnerRepositoryImpl
    ): TestRunnerRepository
}
