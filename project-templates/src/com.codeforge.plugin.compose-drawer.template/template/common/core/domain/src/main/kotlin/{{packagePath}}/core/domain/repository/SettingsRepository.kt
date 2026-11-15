package {{packageName}}.core.domain.repository

import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/** Gespeicherte Einstellungen. */
interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)
}
