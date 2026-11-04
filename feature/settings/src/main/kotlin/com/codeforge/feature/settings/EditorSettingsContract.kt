/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.runtime.Immutable
import com.codeforge.core.designsystem.theme.TextMateThemeCatalog
import com.codeforge.core.designsystem.theme.TextMateThemeItem

/** Bereiche der Editor-Einstellungen (für „Zurücksetzen“). */
enum class EditorSettingsSection {
    DISPLAY,
    INPUT,
    LANGUAGE,
    FORMAT_ON_SAVE,
    INDEXING
}

/** Zustand der Editor-Einstellungen (Anzeige, Eingabe, Sprache, Format beim Speichern, Indexierung, Datei-Kopf). */
@Immutable
data class EditorSettingsUiState(
    val tabSize: Int = 4,
    val useTreeSitter: Boolean = true,
    val textmateTheme: String = "auto",
    val lspServerPath: String = "",
    val fontSize: Int = 14,
    val fontFamily: String = "",
    val wordWrap: Boolean = false,
    val showNonPrintableChars: Boolean = false,
    val stickyScrollEnabled: Boolean = true,
    val magnifierEnabled: Boolean = true,
    val symbolPairAutocompleteEnabled: Boolean = true,
    val minimapEnabled: Boolean = true,
    val rainbowBracketsEnabled: Boolean = true,
    val pinchToZoomEnabled: Boolean = true,
    val desktopShortcutsEnabled: Boolean = true,
    val formatOnSaveKotlin: Boolean = false,
    val formatOnSaveJava: Boolean = false,
    val formatOnSaveXml: Boolean = false,
    val formatOnSaveJson: Boolean = false,
    val lspMaxConcurrentSessions: Int = 3,
    val lspIdleTimeoutMinutes: Int = 10,
    val projectIndexingEnabled: Boolean = true,
    val showLineNumbers: Boolean = true,
    val pinLineNumbers: Boolean = false,
    val cursorAnimationEnabled: Boolean = true,
    val lineSpacingPercent: Int = 100,
    val readOnlyDefault: Boolean = false,
    val txtWordWrap: Boolean = false,
    val colorPreviewsEnabled: Boolean = true,
    val disableSoftKeyboardWithHardwareKeyboard: Boolean = false,
    val keyboardSuggestions: Boolean = false,
    val fileHeaderEnabled: Boolean = false,
    val fileHeaderAuthor: String = "",
    val availableThemes: List<TextMateThemeItem> = TextMateThemeCatalog.BUNDLED_THEMES,
    val isLoading: Boolean = true
)

/** Aktionen in den Editor-Einstellungen. */
sealed interface EditorSettingsUiEvent {
    /** Die Tabulatorbreite wurde geändert. */
    data class TabSizeChanged(val size: Int) : EditorSettingsUiEvent
    /** Die Höchstzahl gleichzeitiger LSP-Sitzungen wurde geändert. */
    data class LspMaxConcurrentSessionsChanged(val count: Int) : EditorSettingsUiEvent
    /** Das Leerlauf-Zeitlimit der LSP-Sitzungen wurde geändert. */
    data class LspIdleTimeoutMinutesChanged(val minutes: Int) : EditorSettingsUiEvent
    /** Tree-sitter ein-/ausgeschaltet. */
    data object TreeSitterToggled : EditorSettingsUiEvent
    /** Das Editor-Theme wurde geändert. */
    data class TextmateThemeChanged(val value: String) : EditorSettingsUiEvent
    /** Der Pfad des LSP-Servers wurde geändert. */
    data class LspServerPathChanged(val value: String) : EditorSettingsUiEvent
    /** Die Schriftgröße wurde geändert. */
    data class FontSizeChanged(val size: Int) : EditorSettingsUiEvent
    /** Die Schriftart wurde geändert. */
    data class FontFamilyChanged(val value: String) : EditorSettingsUiEvent
    /** Zeilenumbruch umgeschaltet. */
    data object WordWrapToggled : EditorSettingsUiEvent
    /** Anzeige nicht druckbarer Zeichen umgeschaltet. */
    data object NonPrintableCharsToggled : EditorSettingsUiEvent
    /** Sticky Scroll umgeschaltet. */
    data object StickyScrollToggled : EditorSettingsUiEvent
    /** Lupe umgeschaltet. */
    data object MagnifierToggled : EditorSettingsUiEvent
    /** Automatisches Schließen von Klammern und Anführungszeichen umgeschaltet. */
    data object SymbolPairAutocompleteToggled : EditorSettingsUiEvent
    /** Minimap umgeschaltet. */
    data object MinimapToggled : EditorSettingsUiEvent
    /** Projektindexierung umgeschaltet. */
    data object ProjectIndexingToggled : EditorSettingsUiEvent
    /** Zeilennummern umgeschaltet. */
    data object LineNumbersToggled : EditorSettingsUiEvent
    /** Fixieren der Zeilennummern umgeschaltet. */
    data object PinLineNumbersToggled : EditorSettingsUiEvent
    /** Cursor-Animation umgeschaltet. */
    data object CursorAnimationToggled : EditorSettingsUiEvent
    /** Der Zeilenabstand wurde geändert. */
    data class LineSpacingChanged(val percent: Int) : EditorSettingsUiEvent
    /** „Schreibgeschützt öffnen“ umgeschaltet. */
    data object ReadOnlyDefaultToggled : EditorSettingsUiEvent
    /** Zeilenumbruch für Textdateien umgeschaltet. */
    data object TxtWordWrapToggled : EditorSettingsUiEvent
    /** Farbvorschau umgeschaltet. */
    data object ColorPreviewsToggled : EditorSettingsUiEvent
    /** Bildschirmtastatur bei Hardware-Tastatur umgeschaltet. */
    data object DisableSoftKeyboardToggled : EditorSettingsUiEvent
    /** Tastaturvorschläge umgeschaltet. */
    data object KeyboardSuggestionsToggled : EditorSettingsUiEvent
    /** Regenbogen-Klammern umgeschaltet. */
    data object RainbowBracketsToggled : EditorSettingsUiEvent
    /** Zoomen per Fingergeste umgeschaltet. */
    data object PinchToZoomToggled : EditorSettingsUiEvent
    /** Desktop-Tastenkürzel umgeschaltet. */
    data object DesktopShortcutsToggled : EditorSettingsUiEvent
    /** Formatieren beim Speichern für Kotlin umgeschaltet. */
    data object FormatOnSaveKotlinToggled : EditorSettingsUiEvent
    /** Formatieren beim Speichern für Java umgeschaltet. */
    data object FormatOnSaveJavaToggled : EditorSettingsUiEvent
    /** Formatieren beim Speichern für XML umgeschaltet. */
    data object FormatOnSaveXmlToggled : EditorSettingsUiEvent
    /** Formatieren beim Speichern für JSON umgeschaltet. */
    data object FormatOnSaveJsonToggled : EditorSettingsUiEvent
    /** Datei-Kopf für neue Dateien umgeschaltet. */
    data object FileHeaderEnabledToggled : EditorSettingsUiEvent
    /** Der Autor im Datei-Kopf wurde geändert. */
    data class FileHeaderAuthorChanged(val author: String) : EditorSettingsUiEvent
    /** Einen Bereich auf Standardwerte zurücksetzen. */
    data class ResetSection(val section: EditorSettingsSection) : EditorSettingsUiEvent
}
