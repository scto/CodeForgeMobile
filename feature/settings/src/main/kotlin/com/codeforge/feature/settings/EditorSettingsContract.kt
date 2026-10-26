/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.runtime.Immutable
import com.codeforge.core.designsystem.theme.TextMateThemeCatalog
import com.codeforge.core.designsystem.theme.TextMateThemeItem

enum class EditorSettingsSection {
    DISPLAY,
    INPUT,
    LANGUAGE,
    FORMAT_ON_SAVE,
    INDEXING
}

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

sealed interface EditorSettingsUiEvent {
    data class TabSizeChanged(val size: Int) : EditorSettingsUiEvent
    data class LspMaxConcurrentSessionsChanged(val count: Int) : EditorSettingsUiEvent
    data class LspIdleTimeoutMinutesChanged(val minutes: Int) : EditorSettingsUiEvent
    data object TreeSitterToggled : EditorSettingsUiEvent
    data class TextmateThemeChanged(val value: String) : EditorSettingsUiEvent
    data class LspServerPathChanged(val value: String) : EditorSettingsUiEvent
    data class FontSizeChanged(val size: Int) : EditorSettingsUiEvent
    data class FontFamilyChanged(val value: String) : EditorSettingsUiEvent
    data object WordWrapToggled : EditorSettingsUiEvent
    data object NonPrintableCharsToggled : EditorSettingsUiEvent
    data object StickyScrollToggled : EditorSettingsUiEvent
    data object MagnifierToggled : EditorSettingsUiEvent
    data object SymbolPairAutocompleteToggled : EditorSettingsUiEvent
    data object MinimapToggled : EditorSettingsUiEvent
    data object ProjectIndexingToggled : EditorSettingsUiEvent
    data object LineNumbersToggled : EditorSettingsUiEvent
    data object PinLineNumbersToggled : EditorSettingsUiEvent
    data object CursorAnimationToggled : EditorSettingsUiEvent
    data class LineSpacingChanged(val percent: Int) : EditorSettingsUiEvent
    data object ReadOnlyDefaultToggled : EditorSettingsUiEvent
    data object TxtWordWrapToggled : EditorSettingsUiEvent
    data object ColorPreviewsToggled : EditorSettingsUiEvent
    data object DisableSoftKeyboardToggled : EditorSettingsUiEvent
    data object KeyboardSuggestionsToggled : EditorSettingsUiEvent
    data object RainbowBracketsToggled : EditorSettingsUiEvent
    data object PinchToZoomToggled : EditorSettingsUiEvent
    data object DesktopShortcutsToggled : EditorSettingsUiEvent
    data object FormatOnSaveKotlinToggled : EditorSettingsUiEvent
    data object FormatOnSaveJavaToggled : EditorSettingsUiEvent
    data object FormatOnSaveXmlToggled : EditorSettingsUiEvent
    data object FormatOnSaveJsonToggled : EditorSettingsUiEvent
    data object FileHeaderEnabledToggled : EditorSettingsUiEvent
    data class FileHeaderAuthorChanged(val author: String) : EditorSettingsUiEvent
    data class ResetSection(val section: EditorSettingsSection) : EditorSettingsUiEvent
}
