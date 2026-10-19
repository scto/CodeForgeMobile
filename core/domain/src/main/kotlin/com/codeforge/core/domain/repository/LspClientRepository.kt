// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
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
