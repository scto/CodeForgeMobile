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

/** Eine geöffnete Datei im Editor (Pfad, Text, Änderungsstand). */
data class OpenFile(
    val path: String,
    val content: String,
    val isDirty: Boolean = false
)

/** Zustand des Umbenennen-Dialogs (Name, Vorschau, Fehler). */
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

/** Zustand des Editors: Tabs, Suche, LSP, Haltepunkte, KI-Assistent und Dialoge. */
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
    /** Gewählte Kontextquellen für den KI-Assistenten. */
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
    /** Optionen des KI-Chats. */
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
    /** Dateien mit ungespeicherten Änderungen. */
    val dirtyFiles: List<OpenFile> get() = openFiles.filter { it.isDirty }
    /** Ob ungespeicherte Änderungen existieren. */
    val hasDirtyFiles: Boolean get() = dirtyFiles.isNotEmpty()
    /** Ob der Language-Server läuft. */
    val isLspConnected: Boolean get() = lspServerState == LspServerState.RUNNING

    /** Update-Hinweise der aktiven Datei. */
    val activeUpdateAnnotations: List<FileUpdateAnnotation>
        get() = if (updateAnnotationsPath != null && updateAnnotationsPath == activeFile?.path) updateAnnotations else emptyList()

    /** Aktive Datei oder `null`. */
    val activeFile: OpenFile? get() = openFiles.getOrNull(activeFileIndex)
    /** Diagnosen der aktiven Datei. */
    val activeDiagnostics: List<LspDiagnostic> get() = activeFile?.let { diagnosticsByPath[it.path] } ?: emptyList()
    /** Git-Zeilenänderungen der aktiven Datei. */
    val activeGitChanges: List<com.codeforge.core.domain.model.GitLineChange>
        get() = if (gitGutterEnabled) activeFile?.let { gitChangesByPath[it.path] }.orEmpty() else emptyList()
    /** Haltepunkte der aktiven Datei. */
    val activeBreakpoints: List<com.codeforge.core.domain.model.Breakpoint>
        get() = activeFile?.let { breakpointsByPath[it.path] } ?: emptyList()
    /** Zeile, in der die Fehlersuche pausiert (falls in der aktiven Datei). */
    val activePausedLine: Int?
        get() = if (pausedPath != null && activeFile?.path == pausedPath) pausedLine else null

    /** Gliederungssymbole nach Filtertext. */
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

/** Aktionen im Editor. */
sealed interface EditorUiEvent {
    /** Datei öffnen. */
    data class OpenFile(val path: String) : EditorUiEvent
    /** Tab schließen. */
    data class CloseTab(val index: Int) : EditorUiEvent
    /** Alle anderen Tabs schließen. */
    data class CloseOtherTabs(val keepIndex: Int) : EditorUiEvent
    /** Alle Tabs schließen. */
    data object CloseAllTabs : EditorUiEvent
    /** Tab wählen. */
    data class SelectTab(val index: Int) : EditorUiEvent
    /** Text geändert. */
    data class TextChanged(val text: String) : EditorUiEvent
    /** Mit dem Language-Server formatieren. */
    data object RunLspFormat : EditorUiEvent
    /** Code formatieren (optional nur ein Bereich). */
    data class FormatCode(val range: LspRange? = null) : EditorUiEvent
    /** Speichern. */
    data object Save : EditorUiEvent

    /** Führt einen von einem Code-Plugin registrierten Befehl mit der aktiven Datei aus. */
    data class RunPluginCommand(val pluginId: String, val commandId: String) : EditorUiEvent

    /** Projektwurzel des Workspaces (für Dependency-Update-Chips); `null` = kein Projektkontext. */
    data class SetProjectRoot(val rootPath: String?) : EditorUiEvent
    /** Update-Hinweis angetippt. */
    data class UpdateChipClicked(val update: DependencyUpdate) : EditorUiEvent
    /** Update-Dialog abgebrochen. */
    data object UpdateDialogCancel : EditorUiEvent
    /** Abhängigkeits-Update anwenden. */
    data class ApplyUpdate(val update: DependencyUpdate) : EditorUiEvent
    /** Alle Updates anwenden. */
    data object ApplyAllUpdates : EditorUiEvent

    /** Suchleiste ein-/ausblenden. */
    data object ToggleSearchBar : EditorUiEvent
    /** Suchtext geändert. */
    data class SearchQueryChanged(val value: String) : EditorUiEvent
    /** Ersetzungstext geändert. */
    data class ReplaceQueryChanged(val value: String) : EditorUiEvent
    /** Groß-/Kleinschreibung beachten. */
    data object ToggleCaseSensitiveSearch : EditorUiEvent
    /** Regulären Ausdruck verwenden. */
    data object ToggleRegexSearch : EditorUiEvent
    /** Nur ganze Wörter. */
    data object ToggleWholeWordSearch : EditorUiEvent
    /** Sprung wurde ausgeführt. */
    data object JumpHandled : EditorUiEvent
    /** Text einfügen. */
    data class InsertText(val text: String) : EditorUiEvent

    // Task 31 Events
    /** Schnellkorrekturen an Position anfordern. */
    data class TriggerQuickFix(val line: Int, val col: Int) : EditorUiEvent
    /** Schnellkorrekturen schließen. */
    data object DismissQuickFix : EditorUiEvent
    /** Code-Aktion anwenden. */
    data class ApplyCodeAction(val action: LspCodeAction) : EditorUiEvent

    /** Umbenennen an Position beginnen. */
    data class TriggerRename(val line: Int, val col: Int, val fallbackName: String) : EditorUiEvent
    /** Neuer Name geändert. */
    data class RenameNameChanged(val newName: String) : EditorUiEvent
    /** Vorschau der Umbenennung anfordern. */
    data object RequestRenamePreview : EditorUiEvent
    /** Umbenennen bestätigen. */
    data object ConfirmRename : EditorUiEvent
    /** Umbenennen abbrechen. */
    data object DismissRename : EditorUiEvent
    /** Letzte Umbenennung rückgängig machen. */
    data object UndoLastRename : EditorUiEvent

    /** Gliederung ein-/ausblenden. */
    data object ToggleOutline : EditorUiEvent
    /** Gliederungsfilter geändert. */
    data class OutlineFilterChanged(val query: String) : EditorUiEvent
    /** Gliederungssymbol wählen (springt dorthin). */
    data class SelectOutlineSymbol(val symbol: LspDocumentSymbol) : EditorUiEvent

    /** Zur Definition springen. */
    data class GoToDefinition(val line: Int, val col: Int) : EditorUiEvent
    /** Verwendungen suchen. */
    data class FindReferences(val line: Int, val col: Int, val symbolName: String) : EditorUiEvent
    /** Verwendungen schließen. */
    data object DismissReferences : EditorUiEvent
    /** Hover-Info anzeigen. */
    data class ShowHover(val line: Int, val col: Int) : EditorUiEvent
    /** Hover-Info schließen. */
    data object DismissHover : EditorUiEvent
    /** Zur Typdefinition springen. */
    data class GoToTypeDefinition(val line: Int, val col: Int) : EditorUiEvent
    /** Zur Implementierung springen. */
    data class GoToImplementation(val line: Int, val col: Int, val symbolName: String) : EditorUiEvent
    /** Signaturhilfe anfordern. */
    data class RequestSignatureHelp(val line: Int, val col: Int) : EditorUiEvent
    /** Signaturhilfe schließen. */
    data object DismissSignatureHelp : EditorUiEvent
    /** Fundstelle öffnen. */
    data class SelectLocation(val location: LspLocation) : EditorUiEvent
    /** Test ausführen. */
    data class RunTestTarget(val target: com.codeforge.feature.editor.overlay.TestTarget) : EditorUiEvent
    /** LSP-Sitzungen anzeigen/verbergen. */
    data object ToggleLspSessionsDialog : EditorUiEvent
    /** LSP-Sitzung neu starten. */
    data class RestartLspSession(val sessionId: String) : EditorUiEvent
    /** LSP-Sitzung beenden. */
    data class StopLspSession(val sessionId: String) : EditorUiEvent

    // Task 37b Breakpoints
    /** Haltepunkt setzen/entfernen. */
    data class ToggleBreakpoint(val line: Int) : EditorUiEvent
    /** Haltepunkt bearbeiten. */
    data class EditBreakpoint(val line: Int) : EditorUiEvent
    /** Haltepunkt speichern. */
    data class SaveBreakpoint(val breakpoint: com.codeforge.core.domain.model.Breakpoint) : EditorUiEvent
    /** Haltepunkt löschen. */
    data class DeleteBreakpoint(val breakpointId: String) : EditorUiEvent
    /** Alle Haltepunkte löschen. */
    data object DeleteAllBreakpoints : EditorUiEvent
    /** Haltepunkt-Übersicht ein-/ausblenden. */
    data class ShowBreakpointsOverview(val show: Boolean) : EditorUiEvent
    /** Haltepunkt-Dialog schließen. */
    data object DismissBreakpointEdit : EditorUiEvent

    // Task 38 KI-Assistent
    /** KI-Assistent öffnen/schließen. */
    data class ToggleAiAssistant(val open: Boolean) : EditorUiEvent
    /** KI-Einstellungen öffnen/schließen. */
    data class ToggleAiSettings(val open: Boolean) : EditorUiEvent
    /** KI-Schnellaktionen öffnen/schließen. */
    data class ToggleAiQuickActions(val open: Boolean) : EditorUiEvent
    /** Kontextquelle ein-/ausschalten. */
    data class ToggleAiContextSource(val source: com.codeforge.core.domain.ai.AiContextSource) : EditorUiEvent
    /** Anfrage an die KI senden. */
    data class SendAiPrompt(val prompt: String, val selectedText: String? = null) : EditorUiEvent
    /** KI-Schnellaktion auslösen. */
    data class TriggerAiQuickAction(
        val action: com.codeforge.feature.editor.ui.AiQuickActionType,
        val selectedText: String? = null,
        val cursorLine: Int? = null
    ) : EditorUiEvent
    /** KI-Antwort abbrechen. */
    data object StopAiStream : EditorUiEvent
    /** KI-Verlauf löschen. */
    data object ClearAiHistory : EditorUiEvent
    /** KI-Optionen speichern. */
    data class SaveAiOptions(val options: com.codeforge.core.domain.ai.AiChatOptions) : EditorUiEvent
    /** Datenschutzrichtlinie der KI speichern. */
    data class SaveAiPrivacyPolicy(val policy: com.codeforge.core.domain.ai.AiPrivacyPolicy) : EditorUiEvent
    /** Tageslimit der KI speichern. */
    data class SaveAiDailyLimit(val limit: Int) : EditorUiEvent
    /** Datenschutzwarnung bestätigen. */
    data object ConfirmAiPrivacyWarning : EditorUiEvent
    /** Datenschutzwarnung schließen. */
    data object DismissAiPrivacyWarning : EditorUiEvent
    /** Tageslimit-Hinweis bestätigen. */
    data object ConfirmAiDailyLimit : EditorUiEvent
    /** Tageslimit-Hinweis schließen. */
    data object DismissAiDailyLimit : EditorUiEvent
    /** Vorgeschlagene Änderung der KI als Diff zeigen. */
    data class ShowAiDiff(val change: com.codeforge.core.domain.ai.AiProposedChange) : EditorUiEvent
    /** Vorgeschlagene Änderung übernehmen. */
    data class AcceptAiProposedChange(val change: com.codeforge.core.domain.ai.AiProposedChange, val forceOverwrite: Boolean = false) : EditorUiEvent
    /** Diff schließen. */
    data object DismissAiDiff : EditorUiEvent
    /** Dateikonflikt-Hinweis schließen. */
    data object DismissAiFileConflict : EditorUiEvent

    // Task 55 Speichern beim Schließen
    /** Schließen anfragen; [onProceed] läuft, wenn nichts mehr offen ist. */
    data class RequestClose(val onProceed: () -> Unit) : EditorUiEvent
    /** Alles speichern und schließen. */
    data object SaveAllAndClose : EditorUiEvent
    /** Änderungen verwerfen und schließen. */
    data object DiscardAllAndClose : EditorUiEvent
    /** Dialog „Speichern beim Schließen“ schließen. */
    data object DismissSaveOnCloseDialog : EditorUiEvent
    /** Hinweis auf wiederhergestellte Dateien schließen. */
    data object DismissRecoveredNotice : EditorUiEvent
}

/** Einmalige Effekte des Editors. */
sealed interface EditorUiEffect {
    /** Meldung anzeigen. */
    data class ShowSnackbar(
        val message: String,
        val actionLabel: String? = null,
        val onAction: (() -> Unit)? = null
    ) : EditorUiEffect
    /** Formatierten Text in den Editor übernehmen. */
    data class ApplyFormattedText(
        val formattedText: String,
        val sourceName: String,
        val isUnchanged: Boolean,
        val warning: String? = null
    ) : EditorUiEffect
    /** Zu einer Route navigieren. */
    data class NavigateTo(val route: String) : EditorUiEffect
}
