// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.LspDiagnostic

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
    val isLspLoading: Boolean = false,
    val isLoading: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val diagnostics: List<LspDiagnostic> = emptyList()
)

sealed interface EditorUiEvent {
    data class OpenFile(val path: String) : EditorUiEvent
    data class CloseTab(val index: Int) : EditorUiEvent
    data class SelectTab(val index: Int) : EditorUiEvent
    data class TextChanged(val text: String) : EditorUiEvent
    data object SaveFile : EditorUiEvent
    data object SaveAllFiles : EditorUiEvent
    data object RunLspFormat : EditorUiEvent
    data object UndoClicked : EditorUiEvent
    data object RedoClicked : EditorUiEvent
}

sealed interface EditorUiEffect {
    data class ShowSnackbar(val message: String) : EditorUiEffect
    data class NavigateTo(val route: String) : EditorUiEffect
}
