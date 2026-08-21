// Modul: :core:data
package com.codeforge.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.codeforge.core.data.repository.FileSystemRepositoryImpl
import com.codeforge.core.data.repository.RecentProjectsRepositoryImpl
import com.codeforge.core.domain.repository.FileSystemRepository
import com.codeforge.core.domain.repository.RecentProjectsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "codeforge_prefs")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreProviderModule {
    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.preferencesDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds
    @Singleton
    abstract fun bindRecentProjectsRepository(
        impl: RecentProjectsRepositoryImpl
    ): RecentProjectsRepository

    @Binds
    @Singleton
    abstract fun bindFileSystemRepository(
        impl: FileSystemRepositoryImpl
    ): FileSystemRepository
}
