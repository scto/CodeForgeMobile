// Modul: :feature:build
package com.codeforge.feature.build.di

import com.codeforge.core.domain.repository.TestRunnerRepository
import com.codeforge.feature.build.testrunner.TestRunnerRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TestRunnerModule {

    @Binds
    @Singleton
    abstract fun bindTestRunnerRepository(
        impl: TestRunnerRepositoryImpl
    ): TestRunnerRepository
}
