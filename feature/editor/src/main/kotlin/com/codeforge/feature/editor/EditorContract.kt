/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.navigation.JumpTarget
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.FileUpdateAnnotation

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
    val isLoading: Boolean = false,
    val displaySettings: EditorDisplaySettings = EditorDisplaySettings(),
    val diagnosticsByPath: Map<String, List<LspDiagnostic>> = emptyMap(),
    val isSearchBarVisible: Boolean = false,
    val searchQuery: String = "",
    val replaceQuery: String = "",
    val isCaseSensitiveSearch: Boolean = false,
    val isRegexSearch: Boolean = false,
    val isWholeWordSearch: Boolean = false,
    /** Von der Projektsuche angefordertes Sprungziel; wird nach dem Anwenden per [EditorUiEvent.JumpHandled] gelöscht. */
    val pendingJump: JumpTarget? = null,
    /** Update-Chips („4.0.1 -> 4.0.3“) der Datei [updateAnnotationsPath]; nur dafür gültig. */
    val updateAnnotations: List<FileUpdateAnnotation> = emptyList(),
    val updateAnnotationsPath: String? = null,
    val pendingUpdateCount: Int = 0,
    /** Per Chip-Klick geöffneter Dialog (Update / Update All). */
    val updateDialog: DependencyUpdate? = null,
    val isApplyingUpdate: Boolean = false
) {
    val activeUpdateAnnotations: List<FileUpdateAnnotation>
        get() = if (updateAnnotationsPath != null && updateAnnotationsPath == activeFile?.path) updateAnnotations else emptyList()

    val activeFile: OpenFile? get() = openFiles.getOrNull(activeFileIndex)
    val activeDiagnostics: List<LspDiagnostic> get() = activeFile?.let { diagnosticsByPath[it.path] } ?: emptyList()
}

sealed interface EditorUiEvent {
    data class OpenFile(val path: String) : EditorUiEvent
    data class CloseTab(val index: Int) : EditorUiEvent
    data class SelectTab(val index: Int) : EditorUiEvent
    data class TextChanged(val text: String) : EditorUiEvent
    data object RunLspFormat : EditorUiEvent
    data object Save : EditorUiEvent

    /** Projektwurzel des Workspaces (für Dependency-Update-Chips); `null` = kein Projektkontext. */
    data class SetProjectRoot(val rootPath: String?) : EditorUiEvent
    data class UpdateChipClicked(val update: DependencyUpdate) : EditorUiEvent
    data object UpdateDialogCancel : EditorUiEvent
    data class ApplyUpdate(val update: DependencyUpdate) : EditorUiEvent
    data object ApplyAllUpdates : EditorUiEvent

    data object ToggleSearchBar : EditorUiEvent
    data class SearchQueryChanged(val value: String) : EditorUiEvent
    data class ReplaceQueryChanged(val value: String) : EditorUiEvent
    data object ToggleCaseSensitiveSearch : EditorUiEvent
    data object ToggleRegexSearch : EditorUiEvent
    data object ToggleWholeWordSearch : EditorUiEvent
    data object JumpHandled : EditorUiEvent
}

sealed interface EditorUiEffect {
    data class ShowSnackbar(val message: String) : EditorUiEffect
    data class NavigateTo(val route: String) : EditorUiEffect
}
