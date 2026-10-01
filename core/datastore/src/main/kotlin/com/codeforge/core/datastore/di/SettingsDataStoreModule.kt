package com.codeforge.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.AppSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Singleton

object AppSettingsSerializer : Serializer<AppSettings> {
    override val defaultValue: AppSettings = AppSettings.newBuilder()
        .setEditor(
            com.codeforge.core.datastore.proto.EditorConfig.newBuilder()
                .setWordWrap(true)
                .setShowMinimap(true)
                .setShowLineNumbers(true)
                .setHighlightCurrentLineEnabled(true)
                .setHighlightCurrentBlockEnabled(true)
                .setBracketHighlightEnabled(true)
                .setAutoIndentEnabled(true)
                .setCursorAnimation(true)
                .setSymbolPairCompletionEnabled(true)
                .setTabSize(4)
                .setFontSize(14)
                .setLineSpacing(1.1f)
                .setSymbolBarVisible(true)
                .setTextmateTheme("CodeForge 2 Dark") // Oder ein anderes Standard-Theme
                .build()
        )
        // Hier können bei Bedarf auch Defaults für FileTreeConfig etc. gesetzt werden
        .build()

    override suspend fun readFrom(input: InputStream): AppSettings {
        return try {
            AppSettings.parseFrom(input)
        } catch (e: Exception) {
            defaultValue
        }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) {
        t.writeTo(output)
    }
}

val Context.appSettingsDataStore: DataStore<AppSettings> by dataStore(
    fileName = "app_settings.pb",
    serializer = AppSettingsSerializer
)

@Module
@InstallIn(SingletonComponent::class)
object SettingsDataStoreModule {

    @Provides
    @Singleton
    fun provideAppSettingsDataStore(
        @ApplicationContext context: Context
    ): DataStore<AppSettings> = context.appSettingsDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(
        dataStore: DataStore<AppSettings>
    ): SettingsRepository = SettingsRepository(dataStore)
}
