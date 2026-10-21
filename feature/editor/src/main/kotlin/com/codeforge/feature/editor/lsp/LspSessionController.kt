// Modul: :feature:editor
package com.codeforge.feature.editor.lsp

import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.LspSessionInfo
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.repository.LspServerResolver
import com.codeforge.core.domain.repository.OpenDocumentsProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LspSessionController @Inject constructor(
    private val lspClient: LspClientRepository,
    private val serverResolver: LspServerResolver,
    private val settingsRepository: SettingsRepository? = null
) {
    var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var timeSource: () -> Long = System::currentTimeMillis
    var maxConcurrentSessions: Int = 3
    var idleTimeoutMinutes: Int = 10
    var errorCooldownMillis: Long = 30_000L

    private var openDocumentsProvider: OpenDocumentsProvider? = null

    val serverState: StateFlow<LspServerState> = lspClient.serverState

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var activeWorkspaceRoot: String? = null
    private var activeLanguageId: String? = null
    private var activePath: String? = null
    private var currentActiveSessionId: String? = null

    private val failedSessionTimestamps = ConcurrentHashMap<String, Long>()
    private val sessionToLanguageMap = ConcurrentHashMap<String, String>()

    val sessionInfos: StateFlow<List<LspSessionInfo>> = lspClient.sessionStates
        .map { states ->
            states.map { (sessionId, state) ->
                val serverId = sessionId.substringBefore('@')
                val workspace = sessionId.substringAfter('@', "")
                LspSessionInfo(
                    sessionId = sessionId,
                    serverId = serverId,
                    workspaceRoot = workspace,
                    state = state,
                    isHeavyweight = isHeavyweightServer(serverId),
                    openDocumentCount = lspClient.getOpenDocumentCount(sessionId)
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        observeSettings()
        startIdleTimer()
    }

    fun setOpenDocumentsProvider(provider: OpenDocumentsProvider?) {
        this.openDocumentsProvider = provider
    }

    private fun observeSettings() {
        if (settingsRepository == null) return
        scope.launch {
            settingsRepository.appSettings.collect { settings ->
                val cfg = settings.editor
                if (cfg.lspMaxConcurrentSessions > 0) {
                    maxConcurrentSessions = cfg.lspMaxConcurrentSessions
                }
                if (cfg.lspIdleTimeoutMinutes > 0) {
                    idleTimeoutMinutes = cfg.lspIdleTimeoutMinutes
                }
            }
        }
    }

    private fun startIdleTimer() {
        scope.launch {
            while (isActive) {
                delay(30_000L)
                checkIdleSessions()
            }
        }
    }

    /**
     * Stellt sicher, dass die LSP-Sitzung für die gegebene Sprache im Workspace läuft.
     * Wechselt das [workspaceRoot], werden alte Sitzungen beendet.
     */
    suspend fun onFileOpened(
        languageId: String,
        workspaceRoot: String?,
        path: String? = null,
        forceRestart: Boolean = false
    ) {
        if (workspaceRoot == null) return

        // Bei Wechsel der Projektwurzel alle Sitzungen des alten Workspaces beenden
        if (activeWorkspaceRoot != null && activeWorkspaceRoot != workspaceRoot) {
            lspClient.stopAll()
        }

        activeWorkspaceRoot = workspaceRoot
        activeLanguageId = languageId
        if (path != null) {
            activePath = path
            lspClient.setActiveDocument(path)
        }

        val launch = try {
            serverResolver.resolve(languageId)
        } catch (e: Exception) {
            _errors.tryEmit(e.message ?: "Failed to resolve LSP server")
            return
        }

        if (launch == null) {
            // Keine passende Extension installiert
            return
        }

        val sessionId = "${launch.serverId}@$workspaceRoot"
        sessionToLanguageMap[sessionId] = languageId

        if (path != null) {
            currentActiveSessionId = sessionId
        }

        // Bereits aktiv / wird gerade gestartet?
        val currentRunning = lspClient.getRunningSessionIds()
        val currentState = lspClient.sessionStates.value[sessionId]
        if (!forceRestart && (currentState == LspServerState.RUNNING || currentState == LspServerState.STARTING)) {
            return
        }

        // Sperrfrist nach Startfehler beachten (außer bei manuellem Neustart)
        if (!forceRestart) {
            val lastFailed = failedSessionTimestamps[sessionId]
            if (lastFailed != null && timeSource() - lastFailed < errorCooldownMillis) {
                return
            }
        }

        // Begrenzung gleichzeitiger Server: Am längsten ungenutzte Sitzung beenden
        val maxSessions = maxConcurrentSessions.coerceAtLeast(1)
        val activeSessions = currentRunning.filter { it != sessionId }
        if (activeSessions.size >= maxSessions) {
            val evictCount = activeSessions.size - maxSessions + 1
            // Schließe die am längsten ungenutzten Sitzungen ausgenommen die des aktiven Dokuments
            val protectedSessionId = currentActiveSessionId

            val candidates = activeSessions
                .filter { it != protectedSessionId }
                .sortedBy { lspClient.getSessionLastUsed(it) }

            for (i in 0 until evictCount.coerceAtMost(candidates.size)) {
                lspClient.stopSession(candidates[i])
            }
        }

        val languageSet = when (languageId.lowercase()) {
            "c", "cpp", "c_cpp" -> setOf("c", "cpp")
            else -> setOf(languageId.lowercase())
        }

        val result = runCatching {
            lspClient.startSession(
                sessionId = sessionId,
                languageIds = languageSet,
                serverCommand = launch.command,
                workspaceRootPath = workspaceRoot,
                extraProcessEnv = launch.env
            )
        }

        val outcome = result.getOrNull()
        if (result.isFailure || outcome?.isFailure == true) {
            failedSessionTimestamps[sessionId] = timeSource()
            val ex = result.exceptionOrNull() ?: outcome?.exceptionOrNull()
            _errors.tryEmit(ex?.message ?: "LSP server failed to start")
        } else {
            failedSessionTimestamps.remove(sessionId)
            reRegisterOpenDocuments(sessionId, launch.serverId, languageId)
        }
    }

    suspend fun restartSession(sessionId: String) {
        lspClient.stopSession(sessionId)
        failedSessionTimestamps.remove(sessionId)

        val serverId = sessionId.substringBefore('@')
        val workspace = sessionId.substringAfter('@', activeWorkspaceRoot ?: "")
        val lang = sessionToLanguageMap[sessionId] ?: languageForServerId(serverId)
        onFileOpened(languageId = lang, workspaceRoot = workspace, forceRestart = true)
    }

    suspend fun stopSession(sessionId: String) {
        lspClient.stopSession(sessionId)
    }

    suspend fun onProjectClosed() {
        stop()
    }

    suspend fun stop() {
        activeWorkspaceRoot = null
        activeLanguageId = null
        activePath = null
        currentActiveSessionId = null
        failedSessionTimestamps.clear()
        sessionToLanguageMap.clear()
        runCatching { lspClient.stopAll() }
    }

    suspend fun checkIdleSessions() {
        val timeoutMs = idleTimeoutMinutes * 60 * 1000L
        val now = timeSource()
        val running = lspClient.getRunningSessionIds()

        val activeSession = currentActiveSessionId

        for (sessionId in running) {
            if (sessionId == activeSession) continue
            val count = lspClient.getOpenDocumentCount(sessionId)
            if (count == 0) {
                val lastUsed = lspClient.getSessionLastUsed(sessionId)
                if (lastUsed > 0L && now - lastUsed >= timeoutMs) {
                    lspClient.stopSession(sessionId)
                }
            }
        }
    }

    private suspend fun reRegisterOpenDocuments(sessionId: String, serverId: String, languageId: String) {
        val provider = openDocumentsProvider ?: return
        val openDocs = provider.getOpenDocuments()
        for (doc in openDocs) {
            val matches = when {
                doc.languageId.equals(languageId, ignoreCase = true) -> true
                serverId == "clangd" && (doc.languageId.lowercase() in setOf("c", "cpp")) -> true
                else -> false
            }
            if (matches) {
                lspClient.didOpen(doc.path, doc.languageId, doc.content)
            }
        }
    }

    private fun isHeavyweightServer(serverId: String): Boolean = when (serverId.lowercase()) {
        "kotlin-language-server", "jdtls", "lemminx", "vscode-xml-language-server" -> true
        else -> false
    }

    private fun languageForServerId(serverId: String): String = when (serverId) {
        "kotlin-language-server" -> "kotlin"
        "jdtls" -> "java"
        "pyright" -> "python"
        "clangd" -> "cpp"
        "rust-analyzer" -> "rust"
        "vscode-html-language-server" -> "html"
        "vscode-css-language-server" -> "css"
        "vscode-json-language-server" -> "json"
        else -> serverId
    }

    private fun languageIdForPath(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "xml" -> "xml"
        "json" -> "json"
        "py" -> "python"
        "c", "h" -> "c"
        "cpp", "cc", "cxx", "hpp", "hh" -> "cpp"
        "rs" -> "rust"
        "html", "htm" -> "html"
        "css" -> "css"
        else -> "plaintext"
    }
}
