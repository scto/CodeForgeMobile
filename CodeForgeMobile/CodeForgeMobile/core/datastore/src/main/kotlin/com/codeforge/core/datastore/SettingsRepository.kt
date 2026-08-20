// Modul: :core:datastore
package com.codeforge.core.datastore

import androidx.datastore.core.DataStore
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.datastore.proto.ThemeConfig
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zentrale Read/Write-Schnittstelle auf das AppSettings-Proto.
 * Wird von :feature:themebuilder, :feature:settings und :feature:onboarding konsumiert.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<AppSettings>
) {
    val appSettings: Flow<AppSettings> = dataStore.data

    suspend fun updateTheme(transform: (ThemeConfig) -> ThemeConfig) {
        dataStore.updateData { current ->
            current.toBuilder()
                .setTheme(transform(current.theme))
                .build()
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.updateData { current ->
            current.toBuilder()
                .setOnboardingCompleted(completed)
                .build()
        }
    }

    suspend fun updateTerminalDistro(distro: String) {
        dataStore.updateData { current ->
            current.toBuilder()
                .setTerminal(current.terminal.toBuilder().setDefaultDistro(distro))
                .build()
        }
    }
}
