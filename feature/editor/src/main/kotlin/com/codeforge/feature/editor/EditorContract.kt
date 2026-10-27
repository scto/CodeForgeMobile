/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.core.navigation.JumpTarget
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.FileUpdateAnnotation

data class OpenFile(
    val path: String,
    val content: String,
    val isDirty: Boolean = false
)

@Immutable
data class RenameDialogState(
    val path: String,
    val position: LspPosition,
    val currentName: String,
    val newName: String = currentName,
    val pendingEdit: LspWorkspaceEdit? = null,
    val pendingFileCount: Int = 0,
    val pendingEditCount: Int = 0,
    val isPreview: Boolean = false
)

@Immutable
data class EditorUiState(
    val openFiles: List<OpenFile> = emptyList(),
    val activeFileIndex: Int = 0,
    val lspServerState: LspServerState = LspServerState.STOPPED,
    val serverCapabilities: LspServerCapabilities? = null,
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
    val isApplyingUpdate: Boolean = false,

    // LSP Task 31 UI-States
    val codeActions: List<LspCodeAction> = emptyList(),
    val isCodeActionsSheetVisible: Boolean = false,
    val renameDialog: RenameDialogState? = null,
    val isOutlineVisible: Boolean = false,
    val outlineSymbols: List<LspDocumentSymbol> = emptyList(),
    val outlineFilter: String = "",
    val isLoadingOutline: Boolean = false,
    val isReferencesSheetVisible: Boolean = false,
    val referencesList: List<LspLocation> = emptyList(),
    val referencesSymbol: String = "",
    /** Git-Gutter: Änderungen gegenüber HEAD je Dateipfad (nur gespeicherter Stand). */
    val gitChangesByPath: Map<String, List<com.codeforge.core.domain.model.GitLineChange>> = emptyMap(),
    val gitGutterEnabled: Boolean = true,
    val conflictDetectionEnabled: Boolean = true,
    /** Hover-Dokumentation des Symbols unter dem Cursor (null = Sheet geschlossen). */
    val hoverText: String? = null,
    /** Aktive Signaturhilfe (Parameter-Hinweis) oder null. */
    val signatureHelp: com.codeforge.core.domain.model.LspSignatureHelp? = null,
    val runningLspSessions: List<com.codeforge.core.domain.model.LspSessionInfo> = emptyList(),
    val isLspSessionsDialogVisible: Boolean = false,

    // Task 37b Breakpoints
    val breakpointsByPath: Map<String, List<com.codeforge.core.domain.model.Breakpoint>> = emptyMap(),
    val pausedLine: Int? = null,
    val pausedPath: String? = null,
    val editingBreakpoint: com.codeforge.core.domain.model.Breakpoint? = null,
    val isBreakpointsOverviewVisible: Boolean = false,

    // Task 38 KI-Assistent
    val isAiAssistantOpen: Boolean = false,
    val isAiSettingsOpen: Boolean = false,
    val isAiQuickActionsOpen: Boolean = false,
    val aiHistory: List<com.codeforge.core.domain.ai.AiChatMessage> = emptyList(),
    val isAiStreaming: Boolean = false,
    val aiStreamingText: String = "",
    val aiTokensUsage: com.codeforge.core.domain.ai.AiUsage? = null,
    val aiSelectedContextSources: Set<com.codeforge.core.domain.ai.AiContextSource> = setOf(
        com.codeforge.core.domain.ai.AiContextSource.CURRENT_FILE,
        com.codeforge.core.domain.ai.AiContextSource.SELECTION
    ),
    val aiPreparedContext: com.codeforge.core.domain.ai.AiPreparedContext? = null,
    val aiProposedDiff: com.codeforge.core.domain.ai.AiProposedChange? = null,
    val aiFileConflictChange: com.codeforge.core.domain.ai.AiProposedChange? = null,
    val isAiPrivacyWarningVisible: Boolean = false,
    val pendingAiPrompt: String? = null,
    val isAiDailyLimitWarningVisible: Boolean = false,
    val aiChatOptions: com.codeforge.core.domain.ai.AiChatOptions = com.codeforge.core.domain.ai.AiChatOptions(
        provider = com.codeforge.core.domain.ai.AiProviderType.OPENAI,
        model = "gpt-4o"
    ),
    val aiPrivacyPolicy: com.codeforge.core.domain.ai.AiPrivacyPolicy = com.codeforge.core.domain.ai.AiPrivacyPolicy.ALWAYS_ASK,
    val aiDailyLimit: Int = 50,
    val aiTodayRequestCount: Int = 0,

    // Task 55 Speichern beim Schließen & CFM-Workspace
    val isSaveOnCloseDialogVisible: Boolean = false,
    val saveOnCloseErrors: Map<String, String> = emptyMap(),
    val recoveredUnsavedFilesNotice: Boolean = false
) {
    val dirtyFiles: List<OpenFile> get() = openFiles.filter { it.isDirty }
    val hasDirtyFiles: Boolean get() = dirtyFiles.isNotEmpty()
    val isLspConnected: Boolean get() = lspServerState == LspServerState.RUNNING

    val activeUpdateAnnotations: List<FileUpdateAnnotation>
        get() = if (updateAnnotationsPath != null && updateAnnotationsPath == activeFile?.path) updateAnnotations else emptyList()

    val activeFile: OpenFile? get() = openFiles.getOrNull(activeFileIndex)
    val activeDiagnostics: List<LspDiagnostic> get() = activeFile?.let { diagnosticsByPath[it.path] } ?: emptyList()
    val activeGitChanges: List<com.codeforge.core.domain.model.GitLineChange>
        get() = if (gitGutterEnabled) activeFile?.let { gitChangesByPath[it.path] }.orEmpty() else emptyList()
    val activeBreakpoints: List<com.codeforge.core.domain.model.Breakpoint>
        get() = activeFile?.let { breakpointsByPath[it.path] } ?: emptyList()
    val activePausedLine: Int?
        get() = if (pausedPath != null && activeFile?.path == pausedPath) pausedLine else null

    val filteredOutlineSymbols: List<LspDocumentSymbol>
        get() = if (outlineFilter.isBlank()) outlineSymbols
        else filterSymbols(outlineSymbols, outlineFilter.trim().lowercase())

    private fun filterSymbols(symbols: List<LspDocumentSymbol>, query: String): List<LspDocumentSymbol> {
        val filtered = mutableListOf<LspDocumentSymbol>()
        for (sym in symbols) {
            val matchingChildren = filterSymbols(sym.children, query)
            if (sym.name.lowercase().contains(query) || matchingChildren.isNotEmpty()) {
                filtered.add(sym.copy(children = matchingChildren))
            }
        }
        return filtered
    }
}

sealed interface EditorUiEvent {
    data class OpenFile(val path: String) : EditorUiEvent
    data class CloseTab(val index: Int) : EditorUiEvent
    data class CloseOtherTabs(val keepIndex: Int) : EditorUiEvent
    data object CloseAllTabs : EditorUiEvent
    data class SelectTab(val index: Int) : EditorUiEvent
    data class TextChanged(val text: String) : EditorUiEvent
    data object RunLspFormat : EditorUiEvent
    data class FormatCode(val range: LspRange? = null) : EditorUiEvent
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
    data class InsertText(val text: String) : EditorUiEvent

    // Task 31 Events
    data class TriggerQuickFix(val line: Int, val col: Int) : EditorUiEvent
    data object DismissQuickFix : EditorUiEvent
    data class ApplyCodeAction(val action: LspCodeAction) : EditorUiEvent

    data class TriggerRename(val line: Int, val col: Int, val fallbackName: String) : EditorUiEvent
    data class RenameNameChanged(val newName: String) : EditorUiEvent
    data object RequestRenamePreview : EditorUiEvent
    data object ConfirmRename : EditorUiEvent
    data object DismissRename : EditorUiEvent
    data object UndoLastRename : EditorUiEvent

    data object ToggleOutline : EditorUiEvent
    data class OutlineFilterChanged(val query: String) : EditorUiEvent
    data class SelectOutlineSymbol(val symbol: LspDocumentSymbol) : EditorUiEvent

    data class GoToDefinition(val line: Int, val col: Int) : EditorUiEvent
    data class FindReferences(val line: Int, val col: Int, val symbolName: String) : EditorUiEvent
    data object DismissReferences : EditorUiEvent
    data class ShowHover(val line: Int, val col: Int) : EditorUiEvent
    data object DismissHover : EditorUiEvent
    data class GoToTypeDefinition(val line: Int, val col: Int) : EditorUiEvent
    data class GoToImplementation(val line: Int, val col: Int, val symbolName: String) : EditorUiEvent
    data class RequestSignatureHelp(val line: Int, val col: Int) : EditorUiEvent
    data object DismissSignatureHelp : EditorUiEvent
    data class SelectLocation(val location: LspLocation) : EditorUiEvent
    data class RunTestTarget(val target: com.codeforge.feature.editor.overlay.TestTarget) : EditorUiEvent
    data object ToggleLspSessionsDialog : EditorUiEvent
    data class RestartLspSession(val sessionId: String) : EditorUiEvent
    data class StopLspSession(val sessionId: String) : EditorUiEvent

    // Task 37b Breakpoints
    data class ToggleBreakpoint(val line: Int) : EditorUiEvent
    data class EditBreakpoint(val line: Int) : EditorUiEvent
    data class SaveBreakpoint(val breakpoint: com.codeforge.core.domain.model.Breakpoint) : EditorUiEvent
    data class DeleteBreakpoint(val breakpointId: String) : EditorUiEvent
    data object DeleteAllBreakpoints : EditorUiEvent
    data class ShowBreakpointsOverview(val show: Boolean) : EditorUiEvent
    data object DismissBreakpointEdit : EditorUiEvent

    // Task 38 KI-Assistent
    data class ToggleAiAssistant(val open: Boolean) : EditorUiEvent
    data class ToggleAiSettings(val open: Boolean) : EditorUiEvent
    data class ToggleAiQuickActions(val open: Boolean) : EditorUiEvent
    data class ToggleAiContextSource(val source: com.codeforge.core.domain.ai.AiContextSource) : EditorUiEvent
    data class SendAiPrompt(val prompt: String, val selectedText: String? = null) : EditorUiEvent
    data class TriggerAiQuickAction(
        val action: com.codeforge.feature.editor.ui.AiQuickActionType,
        val selectedText: String? = null,
        val cursorLine: Int? = null
    ) : EditorUiEvent
    data object StopAiStream : EditorUiEvent
    data object ClearAiHistory : EditorUiEvent
    data class SaveAiOptions(val options: com.codeforge.core.domain.ai.AiChatOptions) : EditorUiEvent
    data class SaveAiPrivacyPolicy(val policy: com.codeforge.core.domain.ai.AiPrivacyPolicy) : EditorUiEvent
    data class SaveAiDailyLimit(val limit: Int) : EditorUiEvent
    data object ConfirmAiPrivacyWarning : EditorUiEvent
    data object DismissAiPrivacyWarning : EditorUiEvent
    data object ConfirmAiDailyLimit : EditorUiEvent
    data object DismissAiDailyLimit : EditorUiEvent
    data class ShowAiDiff(val change: com.codeforge.core.domain.ai.AiProposedChange) : EditorUiEvent
    data class AcceptAiProposedChange(val change: com.codeforge.core.domain.ai.AiProposedChange, val forceOverwrite: Boolean = false) : EditorUiEvent
    data object DismissAiDiff : EditorUiEvent
    data object DismissAiFileConflict : EditorUiEvent

    // Task 55 Speichern beim Schließen
    data class RequestClose(val onProceed: () -> Unit) : EditorUiEvent
    data object SaveAllAndClose : EditorUiEvent
    data object DiscardAllAndClose : EditorUiEvent
    data object DismissSaveOnCloseDialog : EditorUiEvent
    data object DismissRecoveredNotice : EditorUiEvent
}

sealed interface EditorUiEffect {
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    ) : EditorUiEffect
    data class ApplyFormattedText(
        val formattedText: String,
        val sourceName: String,
        val isUnchanged: Boolean,
        val warning: String? = null
    ) : EditorUiEffect
    data class NavigateTo(val route: String) : EditorUiEffect
}
