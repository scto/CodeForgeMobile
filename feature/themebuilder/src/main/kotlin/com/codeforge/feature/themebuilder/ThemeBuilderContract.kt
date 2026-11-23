// Modul: :feature:themebuilder
package com.codeforge.feature.themebuilder

import androidx.compose.runtime.Immutable
import com.codeforge.libs.theme_kit.ColorRoles
import com.codeforge.libs.theme_kit.ThemeContrast
import com.codeforge.libs.theme_kit.ThemeEngine
import com.codeforge.libs.theme_kit.ThemeSpec
import com.codeforge.libs.theme_kit.ThemeStyle

/** Optional überschreibbare Paletten. */
enum class OverrideSlot { SECONDARY, TERTIARY, NEUTRAL, ERROR }

/** Ein Ziel im Projekt, in das das Theme übernommen werden kann. */
@Immutable
data class TargetItem(val id: String, val isCompose: Boolean, val module: String, val detail: String, val selected: Boolean)

/** Eine geplante Dateiänderung für die Anzeige. */
@Immutable
data class ChangeItem(val path: String, val isNew: Boolean, val added: Int, val removed: Int, val preview: String)

/** Hinweis zur Übernahme (Text wird in der UI aufgelöst). */
@Immutable
sealed interface NoteItem {
    /** `Theme.kt` ohne erkennbare Schemata. */
    data class SchemesNotFound(val path: String) : NoteItem

    /** `dynamicColor` ausgeschaltet. */
    data class DynamicColorOff(val path: String) : NoteItem

    /** App-Style nicht gefunden. */
    data class StyleMissing(val style: String) : NoteItem

    /** Kein Material3-Parent. */
    data class LimitedFamily(val style: String) : NoteItem
}

/** Vorschau der Übernahme. */
@Immutable
data class PlanUi(val changes: List<ChangeItem>, val notes: List<NoteItem>, val computing: Boolean = false)

/**
 * Zustand des Theme-Builders.
 *
 * @property projectRoot Projektordner; `null` = ohne Projekt geöffnet (nur „Als App-Farbschema“).
 * @property projectName Ordnername des Projekts für die Titelzeile.
 * @property spec aktuell gestaltetes Theme.
 * @property seedText Inhalt des Hex-Feldes (kann beim Tippen ungültig sein).
 * @property light hell erzeugte Rollen (für Vorschau und Rollenliste).
 * @property dark dunkel erzeugte Rollen.
 * @property previewDark Vorschau dunkel statt hell.
 * @property imageSeeds Grundfarben-Vorschläge aus einem gewählten Bild.
 * @property targets gefundene Übernahme-Ziele im Projekt.
 * @property scanning Projekt wird durchsucht.
 * @property plan Vorschau der Übernahme; `null` = geschlossen.
 * @property busy Übernahme oder Installation läuft.
 */
@Immutable
data class ThemeBuilderUiState(
    val projectRoot: String? = null,
    val projectName: String = "",
    val spec: ThemeSpec = ThemeSpec.CODEFORGE,
    val seedText: String = ThemeSpec.CODEFORGE_SEED,
    val light: ColorRoles = ThemeEngine.roles(ThemeSpec.CODEFORGE, isDark = false),
    val dark: ColorRoles = ThemeEngine.roles(ThemeSpec.CODEFORGE, isDark = true),
    val previewDark: Boolean = false,
    val imageSeeds: List<Int> = emptyList(),
    val targets: List<TargetItem> = emptyList(),
    val scanning: Boolean = false,
    val plan: PlanUi? = null,
    val busy: Boolean = false,
) {
    /** Rollen der aktuell angezeigten Helligkeit. */
    val shown: ColorRoles get() = if (previewDark) dark else light
}

/** Aktionen des Nutzers. */
sealed interface ThemeBuilderUiEvent {
    /** Text im Hex-Feld der Grundfarbe geändert. */
    data class SeedTextChanged(val text: String) : ThemeBuilderUiEvent

    /** Grundfarbe gewählt (Farbwähler, Schnellauswahl, Bild). */
    data class SeedPicked(val hex: String) : ThemeBuilderUiEvent

    /** Palettenstil gewählt. */
    data class StyleSelected(val style: ThemeStyle) : ThemeBuilderUiEvent

    /** Kontrast gewählt. */
    data class ContrastSelected(val contrast: ThemeContrast) : ThemeBuilderUiEvent

    /** Eigene Palette gesetzt (`null` = wieder ableiten). */
    data class OverrideChanged(val slot: OverrideSlot, val hex: String?) : ThemeBuilderUiEvent

    /** Vorschau hell/dunkel umgeschaltet. */
    data class PreviewModeChanged(val dark: Boolean) : ThemeBuilderUiEvent

    /** Pixel eines gewählten Bildes (verkleinert), für Grundfarben-Vorschläge. */
    class ImagePixels(val pixels: IntArray) : ThemeBuilderUiEvent

    /** Zurück auf den CodeForge-Standard bzw. das gespeicherte Projekt-Theme. */
    data object Reset : ThemeBuilderUiEvent

    /** „Ins Projekt übernehmen“: Ziele suchen und Änderungen berechnen. */
    data object OpenPlan : ThemeBuilderUiEvent

    /** Ziel an/aus. */
    data class TargetToggled(val id: String) : ThemeBuilderUiEvent

    /** Änderungen schreiben. */
    data object ConfirmApply : ThemeBuilderUiEvent

    /** Vorschau der Übernahme schließen. */
    data object DismissPlan : ThemeBuilderUiEvent

    /** Als App-Farbschema installieren und aktivieren. */
    data class InstallAsAppTheme(val name: String) : ThemeBuilderUiEvent
}

/** Einmalige Rückmeldungen. */
sealed interface ThemeBuilderEffect {
    /** Theme übernommen. */
    data class Applied(val files: Int, val backup: String?) : ThemeBuilderEffect

    /** Projekt nutzt das Theme bereits. */
    data object NothingToApply : ThemeBuilderEffect

    /** Gespeichertes Projekt-Theme geladen. */
    data object LoadedFromProject : ThemeBuilderEffect

    /** Als App-Farbschema installiert. */
    data class Installed(val name: String) : ThemeBuilderEffect

    /** Fehler mit Meldung. */
    data class Failed(val message: String) : ThemeBuilderEffect
}
