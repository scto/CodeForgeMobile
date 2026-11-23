// Modul: :feature:settings
package com.codeforge.feature.settings.appearance

import androidx.compose.runtime.Immutable
import com.codeforge.core.datastore.proto.ThemeMode
import com.codeforge.core.domain.extension.ExtensionIconTheme
import com.codeforge.core.domain.extension.ExtensionLanguagePack

/**
 * Ein Farbschema zur Auswahl: CodeForge-Standard (`pluginId` leer) oder eine Plugin-Familie mit ihren
 * Hell-/Dunkel-Varianten.
 *
 * @property id Plugin und Familienname (`""` = CodeForge-Standard).
 * @property pluginId Plugin des Farbschemas (leer = CodeForge).
 * @property label Anzeigename der Familie ohne Hell/Dunkel-Zusatz.
 * @property key Schlüssel, der beim Auswählen gespeichert wird.
 * @property swatches Primär, Tertiär und Fläche zur Anzeige (ARGB).
 * @property hasLight Familie hat eine helle Variante.
 * @property hasDark Familie hat eine dunkle Variante.
 */
@Immutable
data class SchemeOption(
    val id: String,
    val pluginId: String,
    val label: String,
    val key: String,
    val swatches: List<Int>,
    val hasLight: Boolean,
    val hasDark: Boolean,
)

/** Zustand der Seite Erscheinungsbild. */
@Immutable
data class AppearanceUiState(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val dynamicSupported: Boolean = false,
    /** [SchemeOption.id] der aktuellen Auswahl; leer = CodeForge-Standard. */
    val selectedSchemeId: String = "",
    val schemes: List<SchemeOption> = emptyList(),
    val iconThemes: List<ExtensionIconTheme> = emptyList(),
    val iconThemeId: String = "",
    val languagePacks: List<ExtensionLanguagePack> = emptyList(),
    val languagePackId: String = "",
    val loading: Boolean = true,
)

/** Aktionen der Seite Erscheinungsbild. */
sealed interface AppearanceUiEvent {
    /** Hell/Dunkel/System. */
    data class ModeSelected(val mode: ThemeMode) : AppearanceUiEvent

    /** Dynamische Farben an/aus. */
    data object DynamicToggled : AppearanceUiEvent

    /** Farbschema gewählt (leerer Schlüssel = CodeForge-Standard). */
    data class SchemeSelected(val key: String) : AppearanceUiEvent

    /** Icon-Paket (leer = Standard). */
    data class IconThemeSelected(val key: String) : AppearanceUiEvent

    /** Sprachpaket (leer = App-Sprache). */
    data class LanguagePackSelected(val key: String) : AppearanceUiEvent
}
