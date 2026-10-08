// Modul: :core:datastore
package com.codeforge.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import com.codeforge.core.datastore.proto.AppSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideAppSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<AppSettings> = androidx.datastore.core.DataStoreFactory.create(
        serializer = AppSettingsSerializer,
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.dataStoreFile("app_settings.pb") }
    )
}
