package com.codeforge.feature.x2c.di

import com.codeforge.core.domain.repository.ComposeBuildEnabler
import com.codeforge.core.domain.repository.ComposeMigrationScanner
import com.codeforge.core.domain.repository.X2cBackupRepository
import com.codeforge.core.domain.repository.X2cBuildChecker
import com.codeforge.core.domain.repository.X2cRepository
import com.codeforge.feature.x2c.build.GradleRepositoryX2cBuildChecker
import com.codeforge.feature.x2c.run.AndroidX2cRunNotifier
import com.codeforge.feature.x2c.run.X2cRunNotifier
import com.codeforge.libs.x2c.X2cRepositoryImpl
import com.codeforge.libs.x2c.backup.X2cBackupRepositoryImpl
import com.codeforge.libs.x2c.build.ComposeBuildEnablerImpl
import com.codeforge.libs.x2c.scanner.ComposeMigrationScannerImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class X2cBindingsModule {

    @Binds
    @Singleton
    abstract fun bindX2cBuildChecker(impl: GradleRepositoryX2cBuildChecker): X2cBuildChecker

    @Binds
    @Singleton
    abstract fun bindX2cRunNotifier(impl: AndroidX2cRunNotifier): X2cRunNotifier
}

@Module
@InstallIn(SingletonComponent::class)
object X2cModule {

    @Provides
    @Singleton
    fun provideComposeMigrationScanner(): ComposeMigrationScanner = ComposeMigrationScannerImpl()

    @Provides
    @Singleton
    fun provideX2cBackupRepository(): X2cBackupRepository = X2cBackupRepositoryImpl()

    @Provides
    @Singleton
    fun provideComposeBuildEnabler(): ComposeBuildEnabler = ComposeBuildEnablerImpl()

    @Provides
    @Singleton
    fun provideX2cRepository(
        scanner: ComposeMigrationScanner,
        backupRepo: X2cBackupRepository,
        buildEnabler: ComposeBuildEnabler,
        buildChecker: X2cBuildChecker
    ): X2cRepository = X2cRepositoryImpl(
        scanner = scanner,
        backupRepo = backupRepo,
        buildEnabler = buildEnabler,
        buildChecker = buildChecker
    )
}
