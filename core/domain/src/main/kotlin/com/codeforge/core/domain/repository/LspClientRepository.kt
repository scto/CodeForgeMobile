// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspServerState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * JSON-RPC-Bridge zum Language Server (LSP, Text-Document-Synchronisation-Subset).
 * Implementiert in :libs:lsp-client, konsumiert von :feature:editor (EditorViewModel).
 *
 * Ein Server-Prozess pro Workspace/Sprache; der Server läuft als Subprozess innerhalb
 * der PRoot-Rootfs (siehe :libs:terminal-engine), die Kommunikation erfolgt über dessen
 * stdin/stdout gemäß LSP-Spec (Content-Length-Header + JSON-RPC 2.0 Body).
 */
interface LspClientRepository {
    val serverState: StateFlow<LspServerState>
    val diagnostics: Flow<Pair<String, List<LspDiagnostic>>>

    suspend fun start(serverCommand: List<String>, workspaceRootPath: String): Result<Unit>
    suspend fun stop()

    suspend fun didOpen(path: String, languageId: String, content: String)
    suspend fun didChange(path: String, newContent: String, version: Int)
    suspend fun didClose(path: String)

    suspend fun requestCompletion(path: String, position: LspPosition): Result<List<LspCompletionItem>>
    suspend fun requestHover(path: String, position: LspPosition): Result<LspHoverInfo?>
    suspend fun requestFormat(path: String, content: String): Result<String>
}
