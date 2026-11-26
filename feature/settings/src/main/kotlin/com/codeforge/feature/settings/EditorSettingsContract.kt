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
    /** Wortauswahl über ICU-Wortgrenzen. */
    val icuWordSelection: Boolean = true,
    /** Schriftart-Ligaturen. */
    val fontLigatures: Boolean = true,
    /** Leerzeichen statt `\t`. */
    val softTabs: Boolean = true,
    /** Leere Zeile mit einem Backspace löschen. */
    val deleteEmptyLineFast: Boolean = true,
    /** Backspace löscht Leerzeichen in Tab-Breite. */
    val backspaceDeletesTab: Boolean = true,
    val symbolPairAutocompleteEnabled: Boolean = true,
    val minimapEnabled: Boolean = true,
    val rainbowBracketsEnabled: Boolean = true,
    /** Code-Faltung (Faltmarken in der Randspalte). */
    val codeFoldingEnabled: Boolean = true,
    val pinchToZoomEnabled: Boolean = true,
    val desktopShortcutsEnabled: Boolean = true,
    val formatOnSaveKotlin: Boolean = false,
    val formatOnSaveJava: Boolean = false,
    val formatOnSaveXml: Boolean = false,
    val formatOnSaveJson: Boolean = false,
    val lspMaxConcurrentSessions: Int = 3,
    val lspIdleTimeoutMinutes: Int = 10,
    val projectIndexingEnabled: Boolean = true,
    /** Java→Kotlin-Konverter (Vorschlag beim Öffnen). */
    val j2kEnabled: Boolean = true,
    /** XML→Compose-Konverter (Vorschlag beim Öffnen, Menüeinträge). */
    val x2cEnabled: Boolean = true,
    val showLineNumbers: Boolean = true,
    val pinLineNumbers: Boolean = false,
    val cursorAnimationEnabled: Boolean = true,
    val lineSpacingPercent: Int = 100,
    val readOnlyDefault: Boolean = false,
    val txtWordWrap: Boolean = false,
    val colorPreviewsEnabled: Boolean = true,
    val disableSoftKeyboardWithHardwareKeyboard: Boolean = false,
    val keyboardSuggestions: Boolean = false,
    /** Symbolleiste über der Bildschirmtastatur. */
    val symbolBarEnabled: Boolean = true,
    val highlightCurrentLine: Boolean = true,
    val indentGuides: Boolean = true,
    val cursorBlink: Boolean = true,
    /** 0 = Bewegen, 1 = Überblenden, 2 = Skalieren. */
    val cursorAnimationStyle: Int = 0,
    val scrollbars: Boolean = true,
    val stickyScrollMaxLines: Int = 3,
    val lineNumberSelectsLine: Boolean = false,
    /** 0 = Wellenlinie, 1 = Linie, 2 = Doppellinie, 3 = keine. */
    val diagnosticStyle: Int = 0,
    val inlayHints: Boolean = true,
    /** 0 = automatisch, 1 = immer, 2 = nie. */
    val mouseMode: Int = 0,
    /** 0 = Tropfen, 1 = seitlicher Tropfen. */
    val selectionHandleStyle: Int = 0,
    val fastScrollLineTip: Boolean = true,
    val foldMarksAsLines: Boolean = false,
    val letterSpacingPercent: Int = 0,
    val rulerColumn: Int = 0,
    val formatOnPaste: Boolean = false,
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
    /** ICU-Wortauswahl an/aus. */
    data object IcuWordSelectionToggled : EditorSettingsUiEvent
    /** Ligaturen an/aus. */
    data object FontLigaturesToggled : EditorSettingsUiEvent
    /** Soft-Tab an/aus. */
    data object SoftTabsToggled : EditorSettingsUiEvent
    /** Leere Zeile per Backspace löschen an/aus. */
    data object DeleteEmptyLineFastToggled : EditorSettingsUiEvent
    /** Tab-Breite per Backspace löschen an/aus. */
    data object BackspaceDeletesTabToggled : EditorSettingsUiEvent
    /** Automatisches Schließen von Klammern und Anführungszeichen umgeschaltet. */
    data object SymbolPairAutocompleteToggled : EditorSettingsUiEvent
    /** Code-Faltung umgeschaltet. */
    data object CodeFoldingToggled : EditorSettingsUiEvent
    /** Minimap umgeschaltet. */
    data object MinimapToggled : EditorSettingsUiEvent
    /** Projektindexierung umgeschaltet. */
    data object ProjectIndexingToggled : EditorSettingsUiEvent
    /** Java→Kotlin-Konverter an/aus. */
    data object J2kToggled : EditorSettingsUiEvent
    /** XML→Compose-Konverter an/aus. */
    data object X2cToggled : EditorSettingsUiEvent
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
    /** Symbolleiste über der Tastatur umgeschaltet. */
    data object SymbolBarToggled : EditorSettingsUiEvent
    /** Hervorhebung der aktuellen Zeile umgeschaltet. */
    data object CurrentLineHighlightToggled : EditorSettingsUiEvent
    /** Einrückungslinien umgeschaltet. */
    data object IndentGuidesToggled : EditorSettingsUiEvent
    /** Blinkender Cursor umgeschaltet. */
    data object CursorBlinkToggled : EditorSettingsUiEvent
    /** Bildlaufleisten umgeschaltet. */
    data object ScrollbarsToggled : EditorSettingsUiEvent
    /** Tipp auf Zeilennummer markiert Zeile umgeschaltet. */
    data object LineNumberSelectsLineToggled : EditorSettingsUiEvent
    /** Cursor-Animation gewählt (0 Gleiten, 1 Überblenden, 2 Skalieren). */
    data class CursorAnimationStyleChanged(val style: Int) : EditorSettingsUiEvent
    /** Höchstzahl der Zeilen im Sticky Scroll. */
    data class StickyScrollMaxLinesChanged(val lines: Int) : EditorSettingsUiEvent
    /** Stil der Fehler-Unterstreichung (0 Welle, 1 Linie, 2 Doppellinie, 3 keine). */
    data class DiagnosticStyleChanged(val style: Int) : EditorSettingsUiEvent
    /** Inlay-Hints umgeschaltet. */
    data object InlayHintsToggled : EditorSettingsUiEvent
    /** Zeilennummer beim schnellen Scrollen umgeschaltet. */
    data object FastScrollTipToggled : EditorSettingsUiEvent
    /** Faltmarken als Linien umgeschaltet. */
    data object FoldMarksToggled : EditorSettingsUiEvent
    /** Formatieren beim Einfügen umgeschaltet. */
    data object FormatOnPasteToggled : EditorSettingsUiEvent
    /** Mausmodus gewählt. */
    data class MouseModeChanged(val mode: Int) : EditorSettingsUiEvent
    /** Stil der Auswahlgriffe gewählt. */
    data class SelectionHandleStyleChanged(val style: Int) : EditorSettingsUiEvent
    /** Zeichenabstand in Hundertstel em. */
    data class LetterSpacingChanged(val percent: Int) : EditorSettingsUiEvent
    /** Lineal-Spalte (0 = aus). */
    data class RulerColumnChanged(val column: Int) : EditorSettingsUiEvent
    /** Einen Bereich auf Standardwerte zurücksetzen. */
    data class ResetSection(val section: EditorSettingsSection) : EditorSettingsUiEvent
}
