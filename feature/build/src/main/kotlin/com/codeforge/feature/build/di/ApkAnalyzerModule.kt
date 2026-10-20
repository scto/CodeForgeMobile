// Modul: :feature:build
package com.codeforge.feature.build.di

import com.codeforge.core.domain.repository.ApkAnalyzerRepository
import com.codeforge.feature.build.analyzer.ApkAnalyzerRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ApkAnalyzerModule {

    @Binds
    @Singleton
    abstract fun bindApkAnalyzerRepository(
        impl: ApkAnalyzerRepositoryImpl
    ): ApkAnalyzerRepository
}
