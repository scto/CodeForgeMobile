/**
 * Modul: :feature:settings:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.editor

import androidx.compose.runtime.Immutable

import com.codeforge.core.datastore.proto.EditorConfig

@Immutable
data class EditorSettingsUiState(
    val editorConfig: EditorConfig = EditorConfig.getDefaultInstance(),
    val workspaceDirectory: String = "",
    val isLoading: Boolean = true
)

sealed interface EditorSettingsUiEvent {
    data class UpdateEditorConfig(val transform: (EditorConfig.Builder) -> Unit) : EditorSettingsUiEvent
    data class WorkspaceDirectoryChanged(val path: String) : EditorSettingsUiEvent
}
