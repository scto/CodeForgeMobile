// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.compose.runtime.Immutable
import com.codeforge.core.datastore.proto.EditorConfig
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspPosition

data class OpenFile(
    val path: String,
    val content: String,
    val isDirty: Boolean = false,
    val diagnostics: List<LspDiagnostic> = emptyList(),
    val completions: List<LspCompletionItem> = emptyList(),
    val isAnalyzing: Boolean = false,
    val cursorPosition: LspPosition = LspPosition(0, 0),
    val languageName: String = "",
    val themeName: String = ""
)

@Immutable
data class EditorUiState(
    val rootPath: String = "",
    val openFiles: List<OpenFile> = emptyList(),
    val activeFileIndex: Int = 0,
    val isLspConnected: Boolean = false,
    val isLoading: Boolean = false,
    val isBuilding: Boolean = false,
    val showBuildLogs: Boolean = false,
    val buildLogs: List<String> = emptyList(),
    val isSearchPanelVisible: Boolean = false,
    val searchQuery: String = "",
    val replaceQuery: String = "",
    val isRegex: Boolean = false,
    val isMatchCase: Boolean = false,
    val isWholeWord: Boolean = false,
    val positionText: String = "",
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val editorConfig: EditorConfig = EditorConfig.getDefaultInstance(),
    val fileTreeConfig: com.codeforge.core.datastore.proto.FileTreeConfig = com.codeforge.core.datastore.proto.FileTreeConfig.getDefaultInstance()
)

sealed interface EditorUiEvent {
    data class OpenFile(val path: String) : EditorUiEvent
    data class CloseTab(val index: Int) : EditorUiEvent
    data class CloseOthersTab(val index: Int) : EditorUiEvent
    data object CloseAllTabs : EditorUiEvent
    data class SelectTab(val index: Int) : EditorUiEvent
    data class TextChanged(val text: String) : EditorUiEvent
    data class CursorPositionChanged(val line: Int, val column: Int) : EditorUiEvent
    data object CompletionRequested : EditorUiEvent
    data class CompletionItemSelected(val item: LspCompletionItem) : EditorUiEvent
    data object SaveFile : EditorUiEvent
    data object RunLspFormat : EditorUiEvent
    data object RunBuild : EditorUiEvent
    data object ToggleBuildLogs : EditorUiEvent
    data object BackClicked : EditorUiEvent

    // Extended Editor Features
    data object ToggleSearchPanel : EditorUiEvent
    data class SearchQueryChanged(val query: String) : EditorUiEvent
    data class ReplaceQueryChanged(val query: String) : EditorUiEvent
    data object ToggleRegex : EditorUiEvent
    data object ToggleMatchCase : EditorUiEvent
    data object ToggleWholeWord : EditorUiEvent
    data object FindNext : EditorUiEvent
    data object FindPrev : EditorUiEvent
    data object ReplaceCurrent : EditorUiEvent
    data object ReplaceAll : EditorUiEvent
    data object Undo : EditorUiEvent
    data object Redo : EditorUiEvent
    data class GotoLine(val line: Int) : EditorUiEvent
    data class SelectLanguage(val languageName: String) : EditorUiEvent
    data class SelectTheme(val themeName: String) : EditorUiEvent
    data class SelectTypeface(val fontName: String) : EditorUiEvent
    data class SelectLinePanelPosition(val mode: Int, val position: Int) : EditorUiEvent
    data class PositionTextChanged(val text: String) : EditorUiEvent
    data class UpdateUndoRedoState(val canUndo: Boolean, val canRedo: Boolean) : EditorUiEvent
    data object NavigateToSettings : EditorUiEvent
    data object ToggleComposePreview : EditorUiEvent
}

sealed interface EditorUiEffect {
    data class ShowSnackbar(val message: String) : EditorUiEffect
    data class NavigateTo(val route: String) : EditorUiEffect
    data object NavigateBack : EditorUiEffect
    data object TriggerEditorUndo : EditorUiEffect
    data object TriggerEditorRedo : EditorUiEffect
    data class TriggerGotoLine(val line: Int) : EditorUiEffect
    data object TriggerComposePreview : EditorUiEffect
}
