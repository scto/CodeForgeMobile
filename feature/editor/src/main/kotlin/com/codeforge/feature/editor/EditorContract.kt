// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.compose.runtime.Immutable

data class OpenFile(
    val path: String,
    val content: String,
    val isDirty: Boolean = false
)

@Immutable
data class EditorUiState(
    val openFiles: List<OpenFile> = emptyList(),
    val activeFileIndex: Int = 0,
    val isLspConnected: Boolean = false,
    val isLoading: Boolean = false
)

sealed interface EditorUiEvent {
    data class OpenFile(val path: String) : EditorUiEvent
    data class CloseTab(val index: Int) : EditorUiEvent
    data class TextChanged(val text: String) : EditorUiEvent
    data object RunLspFormat : EditorUiEvent
}

sealed interface EditorUiEffect {
    data class ShowSnackbar(val message: String) : EditorUiEffect
    data class NavigateTo(val route: String) : EditorUiEffect
}
