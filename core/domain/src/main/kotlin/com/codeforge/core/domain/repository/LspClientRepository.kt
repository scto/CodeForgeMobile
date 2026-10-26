// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspSignatureHelp
import com.codeforge.core.domain.model.LspPrepareRenameResult
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.LspWorkspaceEdit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * JSON-RPC-Bridge zum Language Server (LSP).
 * Implementiert in :libs:lsp-client, konsumiert von :feature:editor (EditorViewModel).
 *
 * Ein Server-Prozess pro Workspace/Sprache; der Server läuft als Subprozess,
 * die Kommunikation erfolgt über dessen stdin/stdout gemäß LSP-Spec
 * (Content-Length-Header + JSON-RPC 2.0 Body).
 */
interface LspClientRepository {
    val serverState: StateFlow<LspServerState>
    val diagnostics: Flow<Pair<String, List<LspDiagnostic>>>
    val serverCapabilities: StateFlow<LspServerCapabilities?>

    /**
     * Zustand aller laufenden Sitzungen nach Sitzungs-ID.
     */
    val sessionStates: StateFlow<Map<String, LspServerState>>
        get() = kotlinx.coroutines.flow.MutableStateFlow(emptyMap())

    /**
     * Liefert die Capabilities der für diesen Dateipfad zuständigen Sitzung.
     */
    fun capabilitiesFor(path: String): LspServerCapabilities? = serverCapabilities.value

    /**
     * Liefert den Zustand der für diesen Dateipfad zuständigen Sitzung.
     */
    fun stateFor(path: String): LspServerState = serverState.value

    /**
     * Setzt das aktuell im Editor aktive Dokument, wodurch [serverState] und [serverCapabilities]
     * auf die entsprechende Sitzung ausgerichtet werden.
     */
    fun setActiveDocument(path: String?) {}

    /**
     * Startet eine gezielte LSP-Sitzung für die gegebene [sessionId] und den [workspaceRootPath].
     */
    suspend fun startSession(
        sessionId: String,
        languageIds: Set<String>,
        serverCommand: List<String>,
        workspaceRootPath: String,
        extraProcessEnv: Map<String, String> = emptyMap()
    ): Result<Unit> = start(serverCommand, workspaceRootPath, extraProcessEnv)

    /**
     * Beendet die LSP-Sitzung mit der [sessionId].
     */
    suspend fun stopSession(sessionId: String) = stop()

    /**
     * Beendet alle aktiven LSP-Sitzungen.
     */
    suspend fun stopAll() = stop()

    /**
     * Liefert die Anzahl offener Dokumente in der Sitzung [sessionId].
     */
    fun getOpenDocumentCount(sessionId: String): Int = 0

    /**
     * Liefert den Zeitstempel (ms) der letzten Nutzung der Sitzung [sessionId].
     */
    fun getSessionLastUsed(sessionId: String): Long = 0L

    /**
     * Liefert alle aktuell registrierten Sitzungs-IDs.
     */
    fun getRunningSessionIds(): List<String> = emptyList()

    /**
     * Handler für eingehende `workspace/applyEdit`-Anfragen vom Server.
     * Liefert true, wenn der WorkspaceEdit erfolgreich angewendet wurde.
     */
    var applyEditHandler: (suspend (LspWorkspaceEdit) -> Boolean)?

    /**
     * [extraProcessEnv] wird zusätzlich zur ererbten Prozessumgebung gesetzt — u.a. nötig
     * für die Termux-Umgebung (PREFIX, LD_LIBRARY_PATH, PATH).
     */
    suspend fun start(
        serverCommand: List<String>,
        workspaceRootPath: String,
        extraProcessEnv: Map<String, String> = emptyMap()
    ): Result<Unit>
    suspend fun stop()

    suspend fun didOpen(path: String, languageId: String, content: String)
    suspend fun didChange(path: String, newContent: String, version: Int)
    suspend fun didClose(path: String)

    suspend fun requestCompletion(path: String, position: LspPosition): Result<List<LspCompletionItem>>
    suspend fun requestHover(path: String, position: LspPosition): Result<LspHoverInfo?>
    suspend fun requestFormat(path: String, content: String): Result<String>
    suspend fun requestRangeFormat(path: String, content: String, range: LspRange): Result<String>

    suspend fun requestCodeActions(
        path: String,
        range: LspRange,
        diagnostics: List<LspDiagnostic> = emptyList()
    ): Result<List<LspCodeAction>>

    suspend fun requestPrepareRename(
        path: String,
        position: LspPosition
    ): Result<LspPrepareRenameResult?>

    suspend fun requestRename(
        path: String,
        position: LspPosition,
        newName: String
    ): Result<LspWorkspaceEdit?>

    suspend fun requestDocumentSymbols(path: String): Result<List<LspDocumentSymbol>>

    suspend fun requestDefinition(path: String, position: LspPosition): Result<List<LspLocation>>

    /** `textDocument/typeDefinition` — Standard: nicht unterstützt (leere Liste). */
    suspend fun requestTypeDefinition(path: String, position: LspPosition): Result<List<LspLocation>> =
        Result.success(emptyList())

    /** `textDocument/implementation` — Standard: nicht unterstützt (leere Liste). */
    suspend fun requestImplementation(path: String, position: LspPosition): Result<List<LspLocation>> =
        Result.success(emptyList())

    /** `textDocument/signatureHelp` — Standard: nicht unterstützt (null). */
    suspend fun requestSignatureHelp(path: String, position: LspPosition): Result<LspSignatureHelp?> =
        Result.success(null)

    suspend fun requestReferences(
        path: String,
        position: LspPosition,
        includeDeclaration: Boolean = true
    ): Result<List<LspLocation>>

    suspend fun executeCommand(
        command: String,
        arguments: List<Any?> = emptyList()
    ): Result<Unit>
}
