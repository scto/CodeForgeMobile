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
    val isLoading: Boolean = true
)

sealed interface EditorSettingsUiEvent {
    data class TabSizeChanged(val size: Int) : EditorSettingsUiEvent
    data object TreeSitterToggled : EditorSettingsUiEvent
    data class TextmateThemeChanged(val value: String) : EditorSettingsUiEvent
    data class LspServerPathChanged(val value: String) : EditorSettingsUiEvent
}
