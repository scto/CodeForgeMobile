// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import com.codeforge.core.domain.model.LspCodeAction
import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspFileRename
import com.codeforge.core.domain.model.LspDocumentSymbol
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspFoldingRange
import com.codeforge.core.domain.model.LspInlayHint
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspSignatureHelp
import com.codeforge.core.domain.model.LspPrepareRenameResult
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.LspWorkspaceEdit
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import com.codeforge.core.domain.model.LspFeature
import com.codeforge.core.domain.repository.LspSettingsRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-Session Router für LSP-Sitzungen.
 * Hält mehrere [LspServerSession]s gleichzeitig (je eine pro Server/Workspace-Kombination).
 * Leitet pfadbasierte Anfragen an die zuständige Sitzung weiter.
 */
@Singleton
class LspClientRepositoryImpl(
    /** Ob eine Fähigkeit für einen Server (Server-ID) genutzt werden darf. */
    private val allows: (serverId: String, feature: LspFeature) -> Boolean
) : LspClientRepository {

    @Inject constructor(settings: LspSettingsRepository) : this(settings::allows)

    /** Ohne Einstellungen: alles erlaubt (Tests). */
    constructor() : this({ _, _ -> true })

    private fun LspServerSession.allows(feature: LspFeature): Boolean = allows(serverId, feature)

    /** Diagnosen nur weiterreichen, wenn sie für den Server eingeschaltet sind (sonst leeren). */
    private fun publish(sessionId: String, path: String, diags: List<LspDiagnostic>) {
        val serverId = sessions[sessionId]?.serverId ?: sessionId.substringBefore('@')
        val shown = if (allows(serverId, LspFeature.DIAGNOSTICS)) diags else emptyList()
        diagnosticsBySession.computeIfAbsent(sessionId) { ConcurrentHashMap() }[path] = shown
        // Mehrere Server je Datei: Diagnosen aller Sitzungen zusammen melden, sonst überschreibt einer den anderen.
        _diagnostics.tryEmit(path to diagnosticsBySession.values.flatMap { it[path].orEmpty() })
    }

    private val sessions = ConcurrentHashMap<String, LspServerSession>()
    // Legt gleiche Anfragen zusammen und merkt sich Antworten, bis sich ein Dokument ändert.
    private val coalescer = LspRequestCoalescer()
    private val documentToSession = ConcurrentHashMap<String, String>()
    private val documentSessions = ConcurrentHashMap<String, List<String>>()
    private val alongsideSessions: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val startOrder = java.util.concurrent.atomic.AtomicLong()
    private val startedAt = ConcurrentHashMap<String, Long>()
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
            onDiagnostics = ::publish,
            onApplyEdit = { edit -> applyEditHandler?.invoke(edit) ?: false }
        )
        session.attachConnection(rpcConnection)
        sessions[sessionId] = session
        updateSessionStates()
        updateActiveSessionState()
    }

    /**
     * Hängt eine bereits erzeugte Sitzung ein (Tests und Wiederverwendung).
     *
     * @param session die zu verwaltende Sitzung.
     */
    internal fun attachSession(session: LspServerSession) {
        coalescer.invalidate()
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
            onDiagnostics = ::publish,
            onApplyEdit = { edit -> applyEditHandler?.invoke(edit) ?: false }
        )
        sessions[sessionId] = session
        startedAt[sessionId] = startOrder.incrementAndGet()
        updateSessionStates()

        val result = session.start(serverCommand, extraProcessEnv)
        updateSessionStates()
        updateActiveSessionState()
        return result
    }

    override suspend fun stopSession(sessionId: String) {
        val session = sessions.remove(sessionId) ?: return
        coalescer.invalidate()
        startedAt.remove(sessionId)
        session.stop()
        documentSessions.replaceAll { _, ids -> ids - sessionId }

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

    override fun isDocumentOpen(path: String): Boolean = documentToSession.containsKey(path)

    override suspend fun didOpen(path: String, languageId: String, content: String) {
        val targets = sessionsForOpen(path, languageId)
        if (targets.isEmpty()) return
        documentToSession[path] = targets.first().sessionId
        documentSessions[path] = targets.map { it.sessionId }
        coalescer.invalidate()
        targets.forEach { it.didOpen(path, languageId, content) }
        updateActiveSessionState()
    }

    override suspend fun didChange(path: String, newContent: String, version: Int) {
        coalescer.invalidate()
        sessionsForPath(path).forEach { it.didChange(path, newContent, version) }
    }

    override suspend fun didClose(path: String) {
        coalescer.invalidate()
        sessionsForPath(path).forEach { it.didClose(path) }
        documentToSession.remove(path)
        documentSessions.remove(path)
        updateActiveSessionState()
    }

    override fun setSessionAlongside(sessionId: String, alongside: Boolean) {
        if (alongside) alongsideSessions += sessionId else alongsideSessions -= sessionId
    }

    override suspend fun requestCompletion(path: String, position: LspPosition): Result<List<LspCompletionItem>> =
        merged(path, LspFeature.COMPLETION, { it.completionProvider }) { it.requestCompletion(path, position) }
            .map { items -> items.distinctBy { it.label to it.insertText } }

    override suspend fun requestHover(path: String, position: LspPosition): Result<LspHoverInfo?> =
        first(path, LspFeature.HOVER, { it.hoverProvider }, null, memo = "hover:$position") { it.requestHover(path, position) }

    override suspend fun requestFormat(path: String, content: String): Result<String> {
        if (sessionsForPath(path).isEmpty()) return Result.failure(noServer())
        val session = route(path, LspFeature.FORMATTING) { it.formattingProvider }
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_formatting_disabled)))
        return session.requestFormat(path, content)
    }

    override suspend fun requestRangeFormat(path: String, content: String, range: LspRange): Result<String> {
        if (sessionsForPath(path).isEmpty()) return Result.failure(noServer())
        val session = route(path, LspFeature.FORMATTING) { it.rangeFormattingProvider }
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_formatting_disabled)))
        return session.requestRangeFormat(path, content, range)
    }

    override suspend fun requestCodeActions(
        path: String,
        range: LspRange,
        diagnostics: List<LspDiagnostic>
    ): Result<List<LspCodeAction>> =
        merged(path, LspFeature.CODE_ACTIONS, { it.codeActionProvider }) { it.requestCodeActions(path, range, diagnostics) }

    override suspend fun requestCodeActionsOfKind(
        path: String,
        range: LspRange,
        only: List<String>
    ): Result<List<LspCodeAction>> =
        // `only` ist für Server nur ein Hinweis – manche liefern trotzdem alles, daher zusätzlich filtern.
        merged(path, LspFeature.CODE_ACTIONS, { it.codeActionProvider }) { it.requestCodeActions(path, range, emptyList(), only) }
            .map { actions -> actions.filter { a -> val k = a.kind.orEmpty(); only.any { k == it || k.startsWith("$it.") } } }

    override fun supportsWillRenameFiles(): Boolean =
        sessions.values.any { it.serverCapabilities.value?.willRenameFiles == true }

    override suspend fun willRenameFiles(renames: List<LspFileRename>): Result<LspWorkspaceEdit?> = runCatching {
        val edits = sessions.values
            .filter { it.serverCapabilities.value?.willRenameFiles == true }
            .mapNotNull { it.willRenameFiles(renames).getOrNull() }
        if (edits.isEmpty()) {
            null
        } else {
            LspWorkspaceEdit(
                changes = edits.flatMap { it.changes.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.flatten() },
                documentChanges = edits.flatMap { it.documentChanges }
            ).takeIf { it.changes.isNotEmpty() || it.documentChanges.isNotEmpty() }
        }
    }

    override suspend fun didRenameFiles(renames: List<LspFileRename>) {
        coalescer.invalidate()
        sessions.values.filter { it.serverCapabilities.value?.didRenameFiles == true }.forEach { it.didRenameFiles(renames) }
    }

    override suspend fun requestPrepareRename(path: String, position: LspPosition): Result<LspPrepareRenameResult?> =
        first(path, LspFeature.RENAME, { it.renameProvider }, null, memo = "prepareRename:$position") { it.requestPrepareRename(path, position) }

    override suspend fun requestRename(path: String, position: LspPosition, newName: String): Result<LspWorkspaceEdit?> =
        first(path, LspFeature.RENAME, { it.renameProvider }, null) { it.requestRename(path, position, newName) }

    override suspend fun requestDocumentSymbols(path: String): Result<List<LspDocumentSymbol>> =
        first(path, LspFeature.NAVIGATION, { it.documentSymbolProvider }, emptyList(), memo = "symbols") { it.requestDocumentSymbols(path) }

    override suspend fun requestDefinition(path: String, position: LspPosition): Result<List<LspLocation>> =
        first(path, LspFeature.NAVIGATION, { it.definitionProvider }, emptyList(), memo = "definition:$position") { it.requestDefinition(path, position) }

    override suspend fun requestTypeDefinition(path: String, position: LspPosition): Result<List<LspLocation>> =
        first(path, LspFeature.NAVIGATION, { it.typeDefinitionProvider }, emptyList(), memo = "typeDefinition:$position") { it.requestTypeDefinition(path, position) }

    override suspend fun requestImplementation(path: String, position: LspPosition): Result<List<LspLocation>> =
        first(path, LspFeature.NAVIGATION, { it.implementationProvider }, emptyList(), memo = "implementation:$position") { it.requestImplementation(path, position) }

    override suspend fun requestSignatureHelp(path: String, position: LspPosition): Result<LspSignatureHelp?> =
        first(path, LspFeature.SIGNATURE_HELP, { it.signatureHelpProvider }, null, memo = "signature:$position", cacheable = false) { it.requestSignatureHelp(path, position) }

    override suspend fun requestFoldingRanges(path: String): Result<List<LspFoldingRange>> =
        first(path, null, { it.foldingRangeProvider }, emptyList(), memo = "folding") { it.requestFoldingRanges(path) }

    override suspend fun requestInlayHints(path: String, range: LspRange): Result<List<LspInlayHint>> =
        first(path, LspFeature.INLAY_HINTS, { it.inlayHintProvider }, emptyList(), memo = "inlay:$range") { it.requestInlayHints(path, range) }

    override suspend fun requestReferences(
        path: String,
        position: LspPosition,
        includeDeclaration: Boolean
    ): Result<List<LspLocation>> =
        first(path, LspFeature.NAVIGATION, { it.referencesProvider }, emptyList(), memo = "references:$position:$includeDeclaration") {
            it.requestReferences(path, position, includeDeclaration)
        }

    // ---- Routing auf mehrere Server je Datei ----
    // Hauptserver zuerst, dann die zusätzlichen; je Funktion antwortet der erste Server, der sie darf (Einstellungen)
    // und kann (Fähigkeiten; unbekannt = kann). Vervollständigung und Code-Actions werden zusammengeführt.

    private fun noServer() = IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet))

    private fun sessionsForPath(path: String): List<LspServerSession> {
        val listed = documentSessions[path]?.mapNotNull { sessions[it] }.orEmpty()
        return listed.ifEmpty { listOfNotNull(sessionForPath(path)) }
    }

    private fun capable(session: LspServerSession, feature: LspFeature?, capability: (LspServerCapabilities) -> Boolean) =
        (feature == null || session.allows(feature)) && session.serverCapabilities.value?.let(capability) != false

    private fun route(
        path: String,
        feature: LspFeature?,
        capability: (LspServerCapabilities) -> Boolean
    ): LspServerSession? = sessionsForPath(path).firstOrNull { capable(it, feature, capability) }

    private suspend fun <T> first(
        path: String,
        feature: LspFeature?,
        capability: (LspServerCapabilities) -> Boolean,
        disabled: T,
        memo: String? = null,
        cacheable: Boolean = true,
        call: suspend (LspServerSession) -> Result<T>
    ): Result<T> {
        if (sessionsForPath(path).isEmpty()) return Result.failure(noServer())
        val session = route(path, feature, capability) ?: return Result.success(disabled)
        if (memo == null) return call(session)
        return coalescer.run("${session.sessionId}|$path|$memo", cache = cacheable) { call(session) }
    }

    private suspend fun <T> merged(
        path: String,
        feature: LspFeature,
        capability: (LspServerCapabilities) -> Boolean,
        call: suspend (LspServerSession) -> Result<List<T>>
    ): Result<List<T>> {
        val all = sessionsForPath(path)
        if (all.isEmpty()) return Result.failure(noServer())
        val targets = all.filter { capable(it, feature, capability) }
        val results = coroutineScope { targets.map { s -> async { call(s) } }.awaitAll() }
        // Fehlschlag nur, wenn alle scheitern; sonst zählen die Antworten der übrigen.
        val ok = results.filter { it.isSuccess }
        return if (ok.isEmpty() && results.isNotEmpty()) results.first() else Result.success(ok.flatMap { it.getOrThrow() })
    }

    override suspend fun executeCommand(command: String, arguments: List<Any?>): Result<Unit> {
        // Zuerst der Server, der den Befehl gemeldet hat (bei mehreren Sitzungen nicht zwingend der aktive).
        val session = sessions.values.firstOrNull { command in it.serverCapabilities.value?.executeCommands.orEmpty() }
            ?: sessionForPath(_activeDocumentPath.value ?: "")
            ?: sessions.values.firstOrNull()
            ?: return Result.failure(IllegalStateException(Res.string(R.string.lsp_lsp_server_nicht_gestartet)))
        coalescer.invalidate() // Befehle können Dateien oder den Serverzustand ändern
        return session.executeCommand(command, arguments)
    }

    /**
     * Beendet Sitzungen, die gerade nicht gebraucht werden, und liefert ihre Anzahl. Die Sitzung des aktiven Dokuments
     * bleibt immer. Ohne [includeOpenDocuments] nur Sitzungen ohne offene Datei; sonst auch solche mit offenen, aber
     * nicht aktiven Dateien (der Editor startet sie beim Wechsel auf die Datei neu).
     */
    suspend fun releaseUnusedSessions(includeOpenDocuments: Boolean): Int {
        val activeSessionId = _activeDocumentPath.value?.let { documentToSession[it] }
        val victims = sessions.values.filter { session ->
            session.sessionId != activeSessionId && (includeOpenDocuments || getOpenDocumentCount(session.sessionId) == 0)
        }
        victims.forEach { stopSession(it.sessionId) }
        return victims.size
    }

    override fun getOpenDocumentCount(sessionId: String): Int =
        sessions[sessionId]?.openDocumentCount ?: 0

    override fun getSessionLastUsed(sessionId: String): Long =
        sessions[sessionId]?.lastUsedTimestamp ?: 0L

    override fun getRunningSessionIds(): List<String> =
        sessions.keys.toList()

    /**
     * Liefert eine Sitzung nach ID.
     *
     * @param sessionId Sitzungs-ID (Server und Workspace-Wurzel).
     * @return die Sitzung oder `null`.
     */
    fun getSession(sessionId: String): LspServerSession? = sessions[sessionId]

    /** Liefert alle aktuell verwalteten Sitzungen. */
    fun getAllSessions(): List<LspServerSession> = sessions.values.toList()

    // Alle Sitzungen für eine neu geöffnete Datei: passende Sprache, Hauptserver vor zusätzlichen, sonst Startreihenfolge.
    private fun sessionsForOpen(path: String, languageId: String): List<LspServerSession> {
        val lang = languageId.lowercase()
        val matching = sessions.values.filter { s ->
            lang in s.languageIds || (s.serverId == "clangd" && (lang == "c" || lang == "cpp"))
        }
        if (matching.isEmpty()) return listOfNotNull(findOrCreateRoutingForOpen(path, languageId))
        return matching.sortedWith(compareBy({ it.sessionId in alongsideSessions }, { startedAt[it.sessionId] ?: Long.MAX_VALUE }))
    }

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
