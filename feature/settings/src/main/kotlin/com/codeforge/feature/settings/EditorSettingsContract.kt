/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.runtime.Immutable

@Immutable
data class EditorSettingsUiState(
    val tabSize: Int = 4,
    val useTreeSitter: Boolean = true,
    val textmateTheme: String = "",
    val lspServerPath: String = "",
    val fontSize: Int = 14,
    val fontFamily: String = "",
    val wordWrap: Boolean = false,
    val showNonPrintableChars: Boolean = false,
    val stickyScrollEnabled: Boolean = true,
    val magnifierEnabled: Boolean = true,
    val symbolPairAutocompleteEnabled: Boolean = true,
    val isLoading: Boolean = true
)

sealed interface EditorSettingsUiEvent {
    data class TabSizeChanged(val size: Int) : EditorSettingsUiEvent
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
}
