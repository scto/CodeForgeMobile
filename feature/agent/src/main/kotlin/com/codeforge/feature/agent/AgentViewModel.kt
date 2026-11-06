/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 */
package com.codeforge.feature.agent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.ai.AiProviderType
import com.codeforge.core.domain.repository.AiRepository
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.domain.agent.AgentAttachment
import com.codeforge.libs.agent.AgentEvent
import com.codeforge.libs.agent.AgentHistoryStore
import com.codeforge.libs.agent.AgentLoop
import com.codeforge.libs.agent.ProjectContext
import com.codeforge.libs.agent.AgentMessage
import com.codeforge.libs.agent.AgentPermissionPolicy
import com.codeforge.libs.agent.AgentSystemPrompt
import com.codeforge.libs.agent.AgentTool
import com.codeforge.libs.agent.AgentToolRegistry
import com.codeforge.libs.agent.BuiltinTools
import com.codeforge.libs.agent.CheckpointWorkspace
import com.codeforge.libs.agent.FileAgentWorkspace
import com.codeforge.libs.agent.FinishReason
import com.codeforge.libs.agent.PermissionMode
import com.codeforge.libs.agent.TextDiff
import com.codeforge.libs.agent.TodoStore
import com.codeforge.libs.agent.ToolApprover
import com.codeforge.libs.mcp.McpManager
import com.codeforge.libs.mcp.McpServerConfig
import com.codeforge.libs.mcp.McpSource
import com.codeforge.libs.mcp.McpTransportKind
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ViewModel des Agenten-Panels: startet Läufe, fragt Freigaben ab und macht Änderungen rückgängig. */
@HiltViewModel
class AgentViewModel @Inject constructor(
    private val aiRepository: AiRepository,
    lspClient: LspClientRepository,
    private val fileSync: FileSyncBridge,
    private val gradle: GradleBuildRepository,
    private val historyStore: AgentHistoryStore,
    private val mcp: McpManager,
    private val connections: AgentConnections,
    private val llmFactory: AgentLlmFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(AgentUiState())

    /** UI-Zustand. */
    val state: StateFlow<AgentUiState> = _state.asStateFlow()

    private val diagnostics = AgentDiagnosticsStore()
    private var approval: CompletableDeferred<Boolean>? = null
    private val policy = AgentPermissionPolicy(
        initialMode = PermissionMode.ASK_EACH,
        approver = ToolApprover { request ->
            val decision = CompletableDeferred<Boolean>()
            approval = decision
            _state.update { it.copy(pendingApproval = PendingApproval(request.toolName, request.summary, request.preview)) }
            try {
                decision.await()
            } finally {
                approval = null
                _state.update { it.copy(pendingApproval = null) }
            }
        },
    )

    private var session: Session? = null
    private var history: List<AgentMessage> = emptyList()
    private var job: Job? = null
    private var nextId = 1L

    /** Alles, was an einem Chat hängt: Checkpoints, Werkzeuge, Aufgabenliste. */
    private class Session(val root: File, val checkpoint: CheckpointWorkspace, val builtins: List<AgentTool>, val todos: TodoStore)

    init {
        viewModelScope.launch { lspClient.diagnostics.collect { (path, list) -> diagnostics.update(path, list) } }
        viewModelScope.launch {
            mcp.servers.collect { list ->
                _state.update { st ->
                    st.copy(
                        mcpServers = list.map {
                            McpServerUi(
                                name = it.entry.config.name,
                                target = it.entry.config.url ?: (listOfNotNull(it.entry.config.command) + it.entry.config.args).joinToString(" "),
                                status = it.status,
                                toolCount = it.toolCount,
                                message = it.message,
                                fromProject = it.entry.source == McpSource.PROJECT,
                            )
                        },
                    )
                }
            }
        }
    }

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: AgentUiEvent) {
        when (event) {
            is AgentUiEvent.Initialize -> if (_state.value.rootPath != event.rootPath) startChat(event.rootPath, restore = true)
            is AgentUiEvent.InputChanged -> _state.update { it.copy(input = event.value) }
            is AgentUiEvent.Attach -> _state.update { st ->
                st.copy(attachments = (st.attachments.filter { it != event.attachment } + event.attachment).takeLast(MAX_ATTACHMENTS))
            }
            is AgentUiEvent.RemoveAttachment -> _state.update { st ->
                st.copy(attachments = st.attachments.filterIndexed { i, _ -> i != event.index })
            }
            AgentUiEvent.TrustContext -> trustContext()
            AgentUiEvent.Send -> send()
            AgentUiEvent.Stop -> job?.cancel()
            is AgentUiEvent.ModeChanged -> {
                policy.mode = event.mode
                _state.update { it.copy(mode = event.mode) }
            }
            is AgentUiEvent.Decide -> approval?.complete(event.allow)
            is AgentUiEvent.Revert -> revert(event.turnId)
            is AgentUiEvent.ShowConnections -> _state.update { it.copy(showConnections = event.show) }
            is AgentUiEvent.AddMcpServer -> addMcpServer(event)
            is AgentUiEvent.RemoveMcpServer -> changeAppServers { l -> l.filter { it.name != event.name } }
            is AgentUiEvent.ToggleMcpServer -> changeAppServers { l -> l.map { if (it.name == event.name) it.copy(disabled = !it.disabled) else it } }
            is AgentUiEvent.TrustMcpServer -> trustMcpServer(event.name)
            is AgentUiEvent.ReconnectMcpServer -> viewModelScope.launch(Dispatchers.IO) { mcp.reconnect(event.name) }
            AgentUiEvent.NewChat -> {
                val root = _state.value.rootPath
                startChat(root, restore = false)
                if (root.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { historyStore.clear(root) }
            }
        }
    }

    /** Eingebaute Werkzeuge plus die der verbundenen MCP-Server (Stand zum Zeitpunkt des Aufrufs). */
    private fun registryFor(session: Session?): AgentToolRegistry? =
        session?.let { AgentToolRegistry(it.builtins + mcp.tools.value) }

    private fun syncMcp(rootPath: String = _state.value.rootPath) {
        viewModelScope.launch(Dispatchers.IO) {
            mcp.setEntries(connections.entries(rootPath.takeIf { it.isNotEmpty() }?.let(::File)))
        }
    }

    private fun addMcpServer(event: AgentUiEvent.AddMcpServer) {
        val target = event.target.trim()
        if (target.isEmpty()) return
        val isUrl = target.startsWith("http://") || target.startsWith("https://")
        val token = event.token.trim()
        val config = if (isUrl) {
            val host = runCatching { java.net.URI(target).host }.getOrNull().orEmpty()
            McpServerConfig(
                name = event.name.trim().ifEmpty { host.removePrefix("www.").substringBefore('.').ifEmpty { "server" } },
                transport = McpTransportKind.HTTP,
                url = target,
                headers = if (token.isEmpty()) emptyMap() else mapOf("Authorization" to if (token.startsWith("Bearer ", ignoreCase = true)) token else "Bearer $token"),
            )
        } else {
            val parts = target.split(Regex("\\s+"))
            McpServerConfig(
                name = event.name.trim().ifEmpty { parts.drop(1).firstOrNull { !it.startsWith("-") }?.substringAfterLast('/')?.ifEmpty { null } ?: parts[0] },
                transport = McpTransportKind.STDIO,
                command = parts[0],
                args = parts.drop(1),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            connections.saveAppServers(connections.appServers().filter { it.name != config.name } + config)
            syncMcp()
        }
    }

    private fun changeAppServers(transform: (List<McpServerConfig>) -> List<McpServerConfig>) {
        viewModelScope.launch(Dispatchers.IO) {
            connections.saveAppServers(transform(connections.appServers()))
            syncMcp()
        }
    }

    private fun trustMcpServer(name: String) {
        val root = _state.value.rootPath.takeIf { it.isNotEmpty() }?.let(::File) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            connections.projectServers(root).firstOrNull { it.name == name }?.let(connections::trust)
            syncMcp()
        }
    }

    private fun startChat(rootPath: String, restore: Boolean) {
        job?.cancel()
        approval?.complete(false)
        history = emptyList()
        nextId = 1L
        session = if (rootPath.isEmpty()) null else newSession(File(rootPath))
        _state.value = AgentUiState(rootPath = rootPath, mode = policy.mode, mcpServers = _state.value.mcpServers, showConnections = _state.value.showConnections)
        if (restore && rootPath.isNotEmpty()) restoreHistory(rootPath)
        syncMcp(rootPath)
        if (rootPath.isNotEmpty()) viewModelScope.launch { loadContext(File(rootPath)) }
    }

    /** Lädt Regeln/Skills; unfreigegebener Inhalt wird gemeldet und für diesen Zug ignoriert. */
    private suspend fun loadContext(root: File): ProjectContext = withContext(Dispatchers.IO) {
        val ctx = ProjectContext.load(root)
        val trusted = ctx.isEmpty || connections.isContextTrusted(root, ctx.fingerprint())
        val pending = if (trusted) null else ctx.summary()
        if (_state.value.rootPath == root.path && _state.value.untrustedContext != pending) _state.update { it.copy(untrustedContext = pending) }
        if (trusted) ctx else ProjectContext.EMPTY
    }

    private fun trustContext() {
        val root = session?.root ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = ProjectContext.load(root)
            connections.trustContext(root, ctx.fingerprint())
            _state.update { it.copy(untrustedContext = null) }
        }
    }

    /** Lädt den gespeicherten Verlauf im Hintergrund; ein inzwischen gestarteter Lauf oder Projektwechsel hat Vorrang. */
    private fun restoreHistory(rootPath: String) {
        val current = session
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) { historyStore.load(rootPath) }
            if (saved.isEmpty() || session !== current || _state.value.items.isNotEmpty() || _state.value.running) return@launch
            val items = AgentTranscript.toItems(saved) { name, args -> registryFor(current)?.get(name)?.describeCall(args) ?: name }
            history = saved
            nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
            _state.update { it.copy(items = items) }
        }
    }

    private fun persist(rootPath: String, messages: List<AgentMessage>) {
        viewModelScope.launch(Dispatchers.IO + NonCancellable) { historyStore.save(rootPath, messages) }
    }

    private fun newSession(root: File): Session {
        val files = FileAgentWorkspace(root) { path -> diagnostics.query(root, path) }
        val checkpoint = CheckpointWorkspace(SyncingAgentWorkspace(files, root, fileSync))
        val todos = TodoStore()
        return Session(root, checkpoint, BuiltinTools.create(checkpoint, todos, GradleAgentBuildRunner(gradle, root)), todos)
    }

    private fun send() {
        val s = _state.value
        val text = s.input.trim()
        val current = session
        if (text.isEmpty() || s.running || current == null) return
        val message = AgentAttachment.compose(s.attachments, text)
        _state.update { it.copy(running = true, input = "", attachments = emptyList()) }
        job = viewModelScope.launch { runTurn(current, message) }
    }

    private suspend fun runTurn(current: Session, text: String) {
        var turnId: Long? = null
        try {
            val options = aiRepository.getAiOptions().first()
            val problem = when {
                // Lokale OpenAI-kompatible Server (eigene Basis-URL) laufen auch ohne Schlüssel.
                options.apiKey.isBlank() && !(options.provider == AiProviderType.OPENAI && !options.baseUrl.isNullOrBlank()) ->
                    Res.string(R.string.agent_error_no_key)
                else -> dailyLimitProblem()
            }
            if (problem != null) {
                addItem { AgentChatItem.Notice(it, problem, isError = true) }
                return
            }
            aiRepository.incrementTodayRequestCount()
            addItem { AgentChatItem.User(it, text) }
            turnId = current.checkpoint.beginTurn()

            val context = loadContext(current.root)
            val registry = AgentToolRegistry(current.builtins + mcp.tools.value + context.tools())
            val notes = mcp.serverNotes()
            val loop = AgentLoop(
                llm = llmFactory.create(options),
                tools = registry,
                policy = policy,
                systemPrompt = AgentSystemPrompt.default(current.root.name) +
                    (if (notes.isBlank()) "" else "\n\n<mcp-server-notes>\n$notes\n</mcp-server-notes>") +
                    context.promptSection(),
            )
            var streamingId: Long? = null
            loop.run(history, text).collect { event ->
                when (event) {
                    is AgentEvent.TextDelta -> {
                        val id = streamingId ?: addItem { AgentChatItem.Assistant(it, "") }.also { id -> streamingId = id }
                        updateItem(id) { item -> (item as AgentChatItem.Assistant).copy(text = item.text + event.text) }
                    }
                    is AgentEvent.ToolCallStarted -> {
                        streamingId = null
                        val summary = registry.get(event.name)?.describeCall(event.arguments) ?: event.name
                        addItem { AgentChatItem.ToolCall(it, event.id, summary, ToolCallStatus.RUNNING) }
                    }
                    is AgentEvent.ToolCallFinished -> {
                        val status = when {
                            event.denied -> ToolCallStatus.DENIED
                            event.result.isError -> ToolCallStatus.ERROR
                            else -> ToolCallStatus.OK
                        }
                        _state.update { st ->
                            st.copy(
                                items = st.items.map { item ->
                                    if (item is AgentChatItem.ToolCall && item.callId == event.id) {
                                        item.copy(status = status, detail = event.result.content.take(DETAIL_MAX))
                                    } else {
                                        item
                                    }
                                },
                                todos = current.todos.items().map { AgentTodo(it.content, it.status) },
                            )
                        }
                    }
                    is AgentEvent.Usage -> Unit
                    is AgentEvent.Finished -> {
                        history = event.messages
                        persist(current.root.path, history)
                        when (event.reason) {
                            FinishReason.END_TURN -> Unit
                            FinishReason.TRUNCATED -> addItem { AgentChatItem.Notice(it, Res.string(R.string.agent_finish_truncated), false) }
                            FinishReason.MAX_STEPS -> addItem { AgentChatItem.Notice(it, Res.string(R.string.agent_finish_max_steps), false) }
                        }
                    }
                    is AgentEvent.Failed -> {
                        history = event.messages
                        persist(current.root.path, history)
                        addItem { AgentChatItem.Notice(it, event.message, isError = true) }
                    }
                }
            }
        } catch (e: CancellationException) {
            withContext(NonCancellable) { addItem { AgentChatItem.Notice(it, Res.string(R.string.agent_cancelled), false) } }
            throw e
        } catch (e: Exception) {
            addItem { AgentChatItem.Notice(it, Res.string(R.string.agent_error_generic, e.message ?: e.toString()), true) }
        } finally {
            withContext(NonCancellable) {
                if (turnId != null) publishChanges(current, turnId)
                _state.update { it.copy(running = false, pendingApproval = null) }
            }
        }
    }

    private suspend fun dailyLimitProblem(): String? {
        val limit = aiRepository.getDailyLimit().first()
        val used = aiRepository.getTodayRequestCount().first()
        return if (limit > 0 && used >= limit) Res.string(R.string.agent_error_daily_limit, limit) else null
    }

    private suspend fun publishChanges(current: Session, turnId: Long) {
        val changed = runCatching { current.checkpoint.changes(turnId) }.getOrDefault(emptyList())
        if (changed.isNotEmpty()) {
            addItem { AgentChatItem.Changes(it, turnId, changed.map { c -> ChangedFile(c.path, TextDiff.unified(c.before, c.after)) }) }
        }
    }

    private fun revert(turnId: Long) {
        val current = session ?: return
        if (_state.value.running) return
        viewModelScope.launch {
            val result = current.checkpoint.revert(turnId)
            _state.update { st ->
                st.copy(
                    items = st.items.map { item ->
                        if (item is AgentChatItem.Changes && item.turnId == turnId && result.failed.isEmpty()) item.copy(reverted = true) else item
                    },
                )
            }
            if (result.failed.isNotEmpty()) {
                addItem { AgentChatItem.Notice(it, Res.string(R.string.agent_revert_failed, result.failed.joinToString()), true) }
            }
        }
    }

    private inline fun addItem(build: (Long) -> AgentChatItem): Long {
        val id = nextId++
        val item = build(id)
        _state.update { it.copy(items = it.items + item) }
        return id
    }

    private fun updateItem(id: Long, transform: (AgentChatItem) -> AgentChatItem) {
        _state.update { st -> st.copy(items = st.items.map { if (it.id == id) transform(it) else it }) }
    }

    override fun onCleared() {
        approval?.complete(false)
        super.onCleared()
    }

    private companion object {
        const val DETAIL_MAX = 600
        const val MAX_ATTACHMENTS = 5
    }
}
