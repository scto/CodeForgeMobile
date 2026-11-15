package {{packageName}}.feature.settings

import androidx.compose.runtime.Immutable
import {{packageName}}.core.domain.model.AppSettings
import {{packageName}}.core.domain.model.ThemeMode

/** Zustand der Einstellungen. */
@Immutable
data class SettingsUiState(val settings: AppSettings = AppSettings())

/** Aktionen des Nutzers. */
sealed interface SettingsUiEvent {
    data class ThemeModeSelected(val mode: ThemeMode) : SettingsUiEvent
    data class DynamicColorChanged(val enabled: Boolean) : SettingsUiEvent
}
