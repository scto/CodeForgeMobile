package com.codeforge.feature.j2k.di

import com.codeforge.core.domain.repository.J2kRepository
import com.codeforge.libs.j2k.J2kRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Hilt-Modul der Umwandlung. */
@Module
@InstallIn(SingletonComponent::class)
object J2kModule {

    /** Stellt das Repository bereit. */
    @Provides
    @Singleton
    fun provideJ2kRepository(): J2kRepository = J2kRepositoryImpl()
}
