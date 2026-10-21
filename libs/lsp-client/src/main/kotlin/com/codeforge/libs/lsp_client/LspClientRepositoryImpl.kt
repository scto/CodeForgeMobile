// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

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
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-Session Router für LSP-Sitzungen.
 * Hält mehrere [LspServerSession]s gleichzeitig (je eine pro Server/Workspace-Kombination).
 * Leitet pfadbasierte Anfragen an die zuständige Sitzung weiter.
 */
@Singleton
class LspClientRepositoryImpl @Inject constructor() : LspClientRepository {

    private val sessions = ConcurrentHashMap<String, LspServerSession>()
    private val documentToSession = ConcurrentHashMap<String, String>()
    private val diagnosticsBySession = ConcurrentHashMap<String, ConcurrentHashMap<String, List<LspDiagnostic>>>()

    private val _sessionStates = MutableStateFlow<Map<String, LspServerState>>(emptyMap())
    override val sessionStates: StateFlow<Map<String, LspServerState>> = _sessionStates.asStateFlow()

    private val _activeDocumentPath = MutableStateFlow<String?>(null)

    private val _serverState = MutableStateFlow(LspServerState.STOPPED)
    override val serverState: StateFlow<LspServerState> = _serverState.asStateFlow()

    private val _serverCapabilities = MutableStateFlow<LspServerCapabilities?>(null)
    override val serverCapabilities: StateFlow<LspServerCapabilities?> = _serverCapabilities.asStateFlow()

    private val _diagnostics = MutableSharedFlow<Pair<String, List<LspDiagnostic>>>(extraBufferCapacity = 64)
    override val diagnostics: Flow<Pair<String, List<LspDiagnostic>>> = _diagnostics.asSharedFlow()

    override var applyEditHandler: (suspend (LspWorkspaceEdit) -> Boolean)? = null

    /** Ermöglicht Injection einer bestehenden Verbindung für Tests. */
    internal fun attachConnection(rpcConnection: LspRpcConnection, sessionId: String = "default") {
        val session = LspServerSession(
            sessionId = sessionId,
            serverId = sessionId,
            workspaceRootPath = "",
            onDiagnostics = { sId, path, diags ->
                val map = diagnosticsBySession.computeIfAbsent(sId) { ConcurrentHashMap() }
                map[path] = diags
                _diagnostics.tryEmit(path to diags)
            },
            onApplyEdit = { edit -> applyEditHandler?.invoke(edit) ?: false }
        )
        session.attachConnection(rpcConnection)
        sessions[sessionId] = session
        updateSessionStates()
        updateActiveSessionState()
    }

    internal fun attachSession(session: LspServerSession) {
        sessions[session.sessionId] = session
        updateSessionStates()
        updateActiveSessionState()
    }

    override fun setActiveDocument(path: String?) {
        _activeDocumentPath.value = path
        updateActiveSessionState()
    }

    override fun capabilitiesFor(path: String): LspServerCapabilities? =
        sessionForPath(path)?.serverCapabilities?.value

    override fun stateFor(path: String): LspServerState =
        sessionForPath(path)?.serverState?.value ?: LspServerState.STOPPED

    override suspend fun startSession(
        sessionId: String,
        languageIds: Set<String>,
        serverCommand: List<String>,
        workspaceRootPath: String,
        extraProcessEnv: Map<String, String>
    ): Result<Unit> {
        val existing = sessions[sessionId]
        if (existing != null && (existing.serverState.value == LspServerState.RUNNING || existing.serverState.value == LspServerState.STARTING)) {
            updateActiveSessionState()
            return Result.success(Unit)
        }

        val serverId = sessionId.substringBefore('@')
        val session = LspServerSession(
            sessionId = sessionId,
            serverId = serverId,
            workspaceRootPath = workspaceRootPath,
            languageIds = languageIds,
            onDiagnostics = { sId, path, diags ->
                val map = diagnosticsBySession.computeIfAbsent(sId) { ConcurrentHashMap() }
                map[path] = diags
                _diagnostics.tryEmit(path to diags)
            },
            onApplyEdit = { edit -> applyEditHandler?.invoke(edit) ?: false }
        )
        sessions[sessionId] = session
        updateSessionStates()

        val result = session.start(serverCommand, extraProcessEnv)
        updateSessionStates()
        updateActiveSessionState()
        return result
    }

    override suspend fun stopSession(sessionId: String) {
        val session = sessions.remove(sessionId) ?: return
        session.stop()

        // Diagnosen dieser Sitzung leeren
        val sessionDiags = diagnosticsBySession.remove(sessionId)
        if (sessionDiags != null) {
            for (path in sessionDiags.keys) {
                _diagnostics.tryEmit(path to emptyList())
            }
        }

        // Dokumentzuordnungen dieser Sitzung entfernen
        val iterator = documentToSession.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value == sessionId) {
                iterator.remove()
            }
        }

        updateSessionStates()
        updateActiveSessionState()
    }

    override suspend fun stopAll() {
        val allIds = sessions.keys.toList()
        for (id in allIds) {
            stopSession(id)
        }
    }

    override suspend fun start(
        serverCommand: List<String>,
        workspaceRootPath: String,
        extraProcessEnv: Map<String, String>
    ): Result<Unit> = startSession(
        sessionId = "default",
        languageIds = emptySet(),
        serverCommand = serverCommand,
        workspaceRootPath = workspaceRootPath,
        extraProcessEnv = extraProcessEnv
    )

    override suspend fun stop() {
        stopAll()
    }

    override suspend fun didOpen(path: String, languageId: String, content: String) {
        val session = findOrCreateRoutingForOpen(path, languageId)
        if (session != null) {
            documentToSession[path] = session.sessionId
            session.didOpen(path, languageId, content)
            updateActiveSessionState()
        }
    }

    override suspend fun didChange(path: String, newContent: String, version: Int) {
        val session = sessionForPath(path)
        session?.didChange(path, newContent, version)
    }

    override suspend fun didClose(path: String) {
        val session = sessionForPath(path)
        session?.didClose(path)
        documentToSession.remove(path)
        updateActiveSessionState()
    }

    override suspend fun requestCompletion(path: String, position: LspPosition): Result<List<LspCompletionItem>> {
        val session = sessionForPath(path)
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet)))
        return session.requestCompletion(path, position)
    }

    override suspend fun requestHover(path: String, position: LspPosition): Result<LspHoverInfo?> {
        val session = sessionForPath(path)
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet)))
        return session.requestHover(path, position)
    }

    override suspend fun requestFormat(path: String, content: String): Result<String> {
        val session = sessionForPath(path)
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet)))
        return session.requestFormat(path, content)
    }

    override suspend fun requestRangeFormat(path: String, content: String, range: LspRange): Result<String> {
        val session = sessionForPath(path)
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet)))
        return session.requestRangeFormat(path, content, range)
    }

    override suspend fun requestCodeActions(
        path: String,
        range: LspRange,
        diagnostics: List<LspDiagnostic>
    ): Result<List<LspCodeAction>> {
        val session = sessionForPath(path)
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet)))
        return session.requestCodeActions(path, range, diagnostics)
    }

    override suspend fun requestPrepareRename(path: String, position: LspPosition): Result<LspPrepareRenameResult?> {
        val session = sessionForPath(path)
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet)))
        return session.requestPrepareRename(path, position)
    }

    override suspend fun requestRename(path: String, position: LspPosition, newName: String): Result<LspWorkspaceEdit?> {
        val session = sessionForPath(path)
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet)))
        return session.requestRename(path, position, newName)
    }

    override suspend fun requestDocumentSymbols(path: String): Result<List<LspDocumentSymbol>> {
        val session = sessionForPath(path)
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet)))
        return session.requestDocumentSymbols(path)
    }

    override suspend fun requestDefinition(path: String, position: LspPosition): Result<List<LspLocation>> {
        val session = sessionForPath(path)
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet)))
        return session.requestDefinition(path, position)
    }

    override suspend fun requestReferences(
        path: String,
        position: LspPosition,
        includeDeclaration: Boolean
    ): Result<List<LspLocation>> {
        val session = sessionForPath(path)
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet)))
        return session.requestReferences(path, position, includeDeclaration)
    }

    override suspend fun executeCommand(command: String, arguments: List<Any?>): Result<Unit> {
        val session = sessionForPath(_activeDocumentPath.value ?: "")
            ?: sessions.values.firstOrNull()
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet)))
        return session.executeCommand(command, arguments)
    }

    override fun getOpenDocumentCount(sessionId: String): Int =
        sessions[sessionId]?.openDocumentCount ?: 0

    override fun getSessionLastUsed(sessionId: String): Long =
        sessions[sessionId]?.lastUsedTimestamp ?: 0L

    override fun getRunningSessionIds(): List<String> =
        sessions.keys.toList()

    fun getSession(sessionId: String): LspServerSession? = sessions[sessionId]

    fun getAllSessions(): List<LspServerSession> = sessions.values.toList()

    private fun findOrCreateRoutingForOpen(path: String, languageId: String): LspServerSession? {
        val existingSessionId = documentToSession[path]
        if (existingSessionId != null) {
            val existing = sessions[existingSessionId]
            if (existing != null) return existing
        }

        val langLower = languageId.lowercase()
        // 1. Suche nach passender Sprache
        val byLanguage = sessions.values.firstOrNull { it.languageIds.contains(langLower) }
        if (byLanguage != null) return byLanguage

        // 2. Spezialfälle (C/C++ teilen sich clangd)
        if (langLower == "c" || langLower == "cpp") {
            val clangd = sessions.values.firstOrNull { it.serverId == "clangd" }
            if (clangd != null) return clangd
        }

        // 3. Wenn genau eine Sitzung existiert
        if (sessions.size == 1) {
            return sessions.values.first()
        }

        // 4. Fallback auf aktive Sitzung
        val activePath = _activeDocumentPath.value
        if (activePath != null) {
            val activeSessionId = documentToSession[activePath]
            if (activeSessionId != null) {
                val activeSession = sessions[activeSessionId]
                if (activeSession != null) return activeSession
            }
        }

        return sessions.values.firstOrNull()
    }

    private fun sessionForPath(path: String): LspServerSession? {
        val sessionId = documentToSession[path]
        if (sessionId != null) {
            val session = sessions[sessionId]
            if (session != null) return session
        }
        for (session in sessions.values) {
            if (session.hasDocument(path)) {
                documentToSession[path] = session.sessionId
                return session
            }
        }
        if (sessions.size == 1) {
            return sessions.values.first()
        }
        val activePath = _activeDocumentPath.value
        if (activePath != null && activePath != path) {
            val activeSessionId = documentToSession[activePath]
            if (activeSessionId != null) {
                val activeSession = sessions[activeSessionId]
                if (activeSession != null) return activeSession
            }
        }
        return null
    }

    private fun updateSessionStates() {
        _sessionStates.value = sessions.mapValues { it.value.serverState.value }
    }

    private fun updateActiveSessionState() {
        val activePath = _activeDocumentPath.value
        val activeSession = if (activePath != null) {
            val sId = documentToSession[activePath]
            if (sId != null) sessions[sId] else null
        } else null

        if (activeSession != null) {
            _serverState.value = activeSession.serverState.value
            _serverCapabilities.value = activeSession.serverCapabilities.value
        } else {
            val single = if (sessions.size == 1) sessions.values.first() else null
            if (single != null) {
                _serverState.value = single.serverState.value
                _serverCapabilities.value = single.serverCapabilities.value
            } else {
                _serverState.value = LspServerState.STOPPED
                _serverCapabilities.value = null
            }
        }
    }
}
