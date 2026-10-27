// Modul: :feature:themebuilder
package com.codeforge.feature.themebuilder

import androidx.compose.runtime.Immutable
import com.codeforge.core.datastore.proto.ThemeConfig
import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.domain.extension.ExtensionColorTheme
import com.codeforge.core.domain.extension.ExtensionIconTheme

enum class PaletteSlot { PRIMARY, SECONDARY, TERTIARY }

@Immutable
data class ThemeBuilderUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val colorSchemeId: String = "",
    val useDynamicColor: Boolean = false,
    val customPrimary: String = "",
    val customSecondary: String = "",
    val customTertiary: String = "",
    val isDynamicColorSupported: Boolean = false,
    val previewTheme: ThemeConfig = ThemeConfig.getDefaultInstance(),
    /** Von aktivierten Plugins beigesteuerte Farbschemata. */
    val extensionThemes: List<ExtensionColorTheme> = emptyList(),
    /** Von aktivierten Plugins beigesteuerte Icon-Pakete und das aktuell gewählte (leer = Standard). */
    val iconThemes: List<ExtensionIconTheme> = emptyList(),
    val iconThemeId: String = "",
    /** Aktives Plugin-Farbschema für die Vorschau (null = keins gewählt). */
    val previewExtensionTheme: ExtensionColorTheme? = null,
    val isLoading: Boolean = true
)

sealed interface ThemeBuilderUiEvent {
    data class ModeSelected(val mode: ThemeMode) : ThemeBuilderUiEvent
    data class PresetSelected(val presetId: String) : ThemeBuilderUiEvent
    /** Leerer Schlüssel = App-Standardicons. */
    data class IconThemeSelected(val key: String) : ThemeBuilderUiEvent
    data object DynamicColorToggled : ThemeBuilderUiEvent
    data class CustomColorChanged(val slot: PaletteSlot, val hex: String) : ThemeBuilderUiEvent
}
