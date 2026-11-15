// Modul: :feature:editor
package com.codeforge.feature.editor.lsp

import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.model.LspServerLaunch
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
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

/** Verwaltet LSP-Sitzungen: Start, Leerlauf-Ende, Fehlerpause und Obergrenze. */
@Singleton
class LspSessionController @Inject constructor(
    private val lspClient: LspClientRepository,
    private val serverResolver: LspServerResolver,
    private val settingsRepository: SettingsRepository? = null
) {
    /** Coroutine-Scope. */
    var scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    /** Zeitquelle (Tests). */
    var timeSource: () -> Long = System::currentTimeMillis
    /** Höchstzahl gleichzeitiger Sitzungen. */
    var maxConcurrentSessions: Int = 3
    /** Leerlaufzeit in Minuten bis zum Beenden. */
    var idleTimeoutMinutes: Int = 10
    /** Pause nach einem Fehler in ms. */
    var errorCooldownMillis: Long = 30_000L

    private var openDocumentsProvider: OpenDocumentsProvider? = null

    /** Zustand des Servers. */
    val serverState: StateFlow<LspServerState> = lspClient.serverState

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Fehlermeldungen. */
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private var activeWorkspaceRoot: String? = null
    private var activeLanguageId: String? = null
    private var activePath: String? = null
    private var currentActiveSessionId: String? = null

    private val failedSessionTimestamps = ConcurrentHashMap<String, Long>()
    private val sessionLaunches = ConcurrentHashMap<String, SessionLaunch>()
    private val crashTimes = ConcurrentHashMap<String, List<Long>>()

    private val _notices = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** Hinweise für den Nutzer (z. B. Server nach Absturz neu gestartet). */
    val notices: SharedFlow<String> = _notices.asSharedFlow()
    private val sessionToLanguageMap = ConcurrentHashMap<String, String>()

    /** Informationen zu den Sitzungen. */
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
        watchCrashes()
    }

    /** Setzt die Quelle der offenen Dokumente. */
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

        val launches = try {
            serverResolver.resolveAll(languageId)
        } catch (e: Exception) {
            _errors.tryEmit(e.message ?: "Failed to resolve LSP server")
            return
        }
        // Hauptserver zuerst, dann die zusätzlichen (je Funktion antwortet der erste, der sie darf).
        launches.forEachIndexed { index, launch ->
            val root = rootFor(path, launch.rootMarkers, workspaceRoot)
            val sessionId = "${launch.serverId}@$root"
            if (index == 0 && path != null) currentActiveSessionId = sessionId
            sessionLaunches[sessionId] = SessionLaunch(launch, languageId, root)
            ensureSession(sessionId, forceRestart)
        }
    }

    private class SessionLaunch(val launch: LspServerLaunch, val languageId: String, val root: String)

    private suspend fun ensureSession(sessionId: String, forceRestart: Boolean) {
        val spec = sessionLaunches[sessionId] ?: return
        val launch = spec.launch
        val languageId = spec.languageId
        sessionToLanguageMap[sessionId] = languageId
        lspClient.setSessionAlongside(sessionId, launch.alongside)

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

        evictForNewSession(currentRunning, sessionId)

        val languageSet = when (languageId.lowercase()) {
            "c", "cpp", "c_cpp" -> setOf("c", "cpp")
            "typescript", "javascript" -> setOf("typescript", "javascript")
            else -> setOf(languageId.lowercase())
        }

        val result = runCatching {
            lspClient.startSession(
                sessionId = sessionId,
                languageIds = languageSet,
                serverCommand = launch.command,
                workspaceRootPath = spec.root,
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

    // Begrenzung gleichzeitiger Server: am längsten ungenutzte Sitzungen beenden (nie die des aktiven Dokuments).
    private suspend fun evictForNewSession(currentRunning: List<String>, sessionId: String) {
        val maxSessions = maxConcurrentSessions.coerceAtLeast(1)
        val activeSessions = currentRunning.filter { it != sessionId }
        if (activeSessions.size < maxSessions) return
        val evictCount = activeSessions.size - maxSessions + 1
        val candidates = activeSessions
            .filter { it != currentActiveSessionId }
            .sortedBy { lspClient.getSessionLastUsed(it) }
        candidates.take(evictCount).forEach { lspClient.stopSession(it) }
    }

    // ---- Automatischer Neustart nach Absturz ----

    private fun watchCrashes() {
        scope.launch {
            var previous = emptyMap<String, LspServerState>()
            lspClient.sessionStates.collect { states ->
                states.forEach { (id, state) ->
                    if (state == LspServerState.FAILED && previous[id] == LspServerState.RUNNING) onCrashed(id)
                }
                previous = states
            }
        }
    }

    private suspend fun onCrashed(sessionId: String) {
        val now = timeSource()
        val recent = (crashTimes[sessionId].orEmpty() + now).filter { now - it <= CRASH_WINDOW_MS }
        crashTimes[sessionId] = recent
        val serverId = sessionId.substringBefore('@')
        if (recent.size > MAX_CRASHES || sessionLaunches[sessionId] == null) {
            _notices.tryEmit(Res.string(R.string.lsp_server_crash_loop, serverId))
            return
        }
        delay(RESTART_DELAY_MS)
        lspClient.stopSession(sessionId)
        failedSessionTimestamps.remove(sessionId)
        ensureSession(sessionId, forceRestart = true)
        if (lspClient.sessionStates.value[sessionId] == LspServerState.RUNNING) {
            _notices.tryEmit(Res.string(R.string.lsp_server_restarted, serverId))
        }
    }

    /** Startet die Sitzung neu. */
    suspend fun restartSession(sessionId: String) {
        lspClient.stopSession(sessionId)
        failedSessionTimestamps.remove(sessionId)
        crashTimes.remove(sessionId)
        if (sessionLaunches[sessionId] != null) {
            ensureSession(sessionId, forceRestart = true)
            return
        }

        val serverId = sessionId.substringBefore('@')
        val workspace = sessionId.substringAfter('@', activeWorkspaceRoot ?: "")
        val lang = sessionToLanguageMap[sessionId] ?: languageForServerId(serverId)
        onFileOpened(languageId = lang, workspaceRoot = workspace, forceRestart = true)
    }

    /** Beendet die Sitzung. */
    suspend fun stopSession(sessionId: String) {
        lspClient.stopSession(sessionId)
    }

    /** Beendet alle Sitzungen beim Schließen des Projekts. */
    suspend fun onProjectClosed() {
        stop()
    }

    /** Beendet alles. */
    suspend fun stop() {
        activeWorkspaceRoot = null
        activeLanguageId = null
        activePath = null
        currentActiveSessionId = null
        failedSessionTimestamps.clear()
        sessionToLanguageMap.clear()
        sessionLaunches.clear()
        crashTimes.clear()
        runCatching { lspClient.stopAll() }
    }

    /** Beendet Sitzungen im Leerlauf. */
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
        "lemminx" -> "xml"
        "yaml-language-server" -> "yaml"
        "taplo" -> "toml"
        "bash-language-server" -> "shellscript"
        else -> serverId
    }

    private fun languageIdForPath(path: String): String = when (path.substringAfterLast('.', "").lowercase()) {
        "kt", "kts" -> "kotlin"
        "java" -> "java"
        "xml", "xsd", "xsl", "xslt", "svg", "plist" -> "xml"
        "json" -> "json"
        "py" -> "python"
        "c", "h" -> "c"
        "cpp", "cc", "cxx", "hpp", "hh" -> "cpp"
        "rs" -> "rust"
        "html", "htm" -> "html"
        "css" -> "css"
        "yaml", "yml" -> "yaml"
        "toml" -> "toml"
        "sh", "bash", "zsh" -> "shellscript"
        "go" -> "go"
        "ts", "tsx", "mts", "cts" -> "typescript"
        "js", "jsx", "mjs", "cjs" -> "javascript"
        "dart" -> "dart"
        "cmake" -> "cmake"
        else -> if (path.substringAfterLast('/').equals("CMakeLists.txt", ignoreCase = true)) "cmake" else "plaintext"
    }

    companion object {
        private const val MAX_CRASHES = 3
        private const val CRASH_WINDOW_MS = 3 * 60_000L
        private const val RESTART_DELAY_MS = 1_000L

        /**
         * Arbeitsordner eines Servers für [path]: der nächste Ordner aufwärts (höchstens bis [projectRoot]), der eine
         * der [markers] enthält; ohne Treffer oder ohne Marker der Projektordner.
         */
        fun rootFor(path: String?, markers: List<String>, projectRoot: String): String {
            if (path == null || markers.isEmpty()) return projectRoot
            val rootFile = java.io.File(projectRoot).absoluteFile
            var dir = java.io.File(path).absoluteFile.parentFile
            while (dir != null && dir.path.startsWith(rootFile.path)) {
                if (markers.any { java.io.File(dir, it).exists() }) return dir.path
                if (dir == rootFile) break
                dir = dir.parentFile
            }
            return projectRoot
        }
    }
}
