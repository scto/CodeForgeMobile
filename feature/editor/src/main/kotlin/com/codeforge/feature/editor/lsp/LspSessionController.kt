// Modul: :feature:editor
package com.codeforge.feature.editor.lsp

import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.repository.LspServerResolver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LspSessionController @Inject constructor(
    private val lspClient: LspClientRepository,
    private val serverResolver: LspServerResolver
) {
    private val _serverState = MutableStateFlow(LspServerState.STOPPED)
    val serverState: StateFlow<LspServerState> = _serverState.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var activeWorkspaceRoot: String? = null
    private var activeLanguageId: String? = null

    /**
     * Startet den LSP-Server für das gegebene [workspaceRoot] und [languageId],
     * falls nicht bereits für diese Kombination gestartet.
     * Wechselt [workspaceRoot] oder [languageId], wird der vorherige Server beendet.
     */
    suspend fun onFileOpened(languageId: String, workspaceRoot: String?) {
        if (workspaceRoot == null) return

        // Bereits aktiv für denselben Workspace und dieselbe Sprache
        if (activeWorkspaceRoot == workspaceRoot && activeLanguageId == languageId) {
            val current = _serverState.value
            if (current == LspServerState.RUNNING || current == LspServerState.STARTING) {
                return
            }
        }

        // Bei Sprach- oder Workspace-Wechsel vorherigen Server stoppen
        if (activeWorkspaceRoot != null || activeLanguageId != null) {
            stop()
        }

        val launch = try {
            serverResolver.resolve(languageId)
        } catch (e: Exception) {
            _serverState.value = LspServerState.ERROR
            _errors.tryEmit(e.message ?: "Failed to resolve LSP server")
            return
        }

        if (launch == null) {
            // Keine passende Extension installiert: Editor verhält sich normal (kein Server)
            _serverState.value = LspServerState.STOPPED
            return
        }

        activeWorkspaceRoot = workspaceRoot
        activeLanguageId = languageId
        _serverState.value = LspServerState.STARTING

        val result = runCatching {
            lspClient.start(
                serverCommand = launch.command,
                workspaceRootPath = workspaceRoot,
                extraProcessEnv = launch.env
            )
        }

        if (result.isFailure || result.getOrNull()?.isFailure == true) {
            _serverState.value = LspServerState.ERROR
            val ex = result.exceptionOrNull() ?: result.getOrNull()?.exceptionOrNull()
            _errors.tryEmit(ex?.message ?: "LSP server failed to start")
        } else {
            _serverState.value = LspServerState.RUNNING
        }
    }

    suspend fun onProjectClosed() {
        stop()
    }

    suspend fun stop() {
        activeWorkspaceRoot = null
        activeLanguageId = null
        runCatching { lspClient.stop() }
        _serverState.value = LspServerState.STOPPED
    }
}
