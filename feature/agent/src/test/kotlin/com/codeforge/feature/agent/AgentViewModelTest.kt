package com.codeforge.feature.agent

import com.codeforge.core.domain.ai.AiChatMessage
import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.domain.ai.AiPrivacyPolicy
import com.codeforge.core.domain.ai.AiProviderType
import com.codeforge.core.domain.repository.AiRepository
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.navigation.FlushRequest
import com.codeforge.libs.agent.LlmClient
import com.codeforge.libs.agent.LlmRequest
import com.codeforge.libs.agent.LlmStreamEvent
import com.codeforge.libs.agent.PermissionMode
import com.codeforge.libs.agent.StopReason
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentViewModelTest {
    private val tmp = kotlin.io.path.createTempDirectory("agentvm").toFile()
    private val options = MutableStateFlow(AiChatOptions(AiProviderType.ANTHROPIC, "m", apiKey = "sk-ant-x"))

    private val repo = object : AiRepository {
        var count = 0
        override fun getAiOptions(): Flow<AiChatOptions> = options
        override suspend fun saveAiOptions(options: AiChatOptions) {}
        override suspend fun getProviderOptions(provider: AiProviderType) = options.value
        override suspend fun saveProviderOptions(options: AiChatOptions) {}
        override fun getPrivacyPolicy(): Flow<AiPrivacyPolicy> = flowOf(AiPrivacyPolicy.ALWAYS_ASK)
        override suspend fun savePrivacyPolicy(policy: AiPrivacyPolicy) {}
        override fun getDailyLimit(): Flow<Int> = flowOf(0)
        override suspend fun saveDailyLimit(limit: Int) {}
        override fun getTodayRequestCount(): Flow<Int> = flowOf(count)
        override suspend fun incrementTodayRequestCount() { count++ }
        override fun getHistory(projectId: String): Flow<List<AiChatMessage>> = flowOf(emptyList())
        override suspend fun saveHistory(projectId: String, history: List<AiChatMessage>) {}
        override suspend fun clearHistory(projectId: String) {}
    }

    // Nur `diagnostics` wird benutzt; alles andere wäre ein Fehler im Test.
    private val lsp: LspClientRepository = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(LspClientRepository::class.java),
    ) { _, method, _ ->
        if (method.name == "getDiagnostics") flow<Any> { } else error("unerwarteter Aufruf: ${method.name}")
    } as LspClientRepository

    private val sync = object : FileSyncBridge {
        override val flushRequests = MutableSharedFlow<FlushRequest>()
        override val externalChanges = MutableSharedFlow<Set<String>>()
        override val directoryChanges = MutableSharedFlow<String>()
        override suspend fun requestFlush(paths: Set<String>) {}
        override suspend fun notifyExternalChange(paths: Collection<String>) {}
        override suspend fun requestFlushDirectory(directory: String) {}
        override suspend fun notifyDirectoryChange(directory: String) {}
    }

    private val gradle = FakeGradle()
    private val connDir = File(tmp.parentFile, tmp.name + "_conn")
    private val connections = AgentConnections(connDir)
    private val mcp = com.codeforge.libs.mcp.McpManager(
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default),
    ) { throw java.io.IOException("kein Netz im Test") }
    private val store = com.codeforge.libs.agent.AgentHistoryStore(File(tmp.parentFile, tmp.name + "_hist"))

    /** Antwort 1: Datei anlegen; Antwort 2: Text. */
    private class ScriptedClient(private val steps: List<List<LlmStreamEvent>>) : LlmClient {
        private var i = 0
        override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flowOf(*steps[i++.coerceAtMost(steps.lastIndex)].toTypedArray())
    }

    private fun createFileThenAnswer() = ScriptedClient(
        listOf(
            listOf(
                LlmStreamEvent.ToolCallCompleted("t1", "create_file", buildJsonObject { put("path", "neu.txt"); put("content", "hallo") }),
                LlmStreamEvent.Completed(StopReason.TOOL_USE),
            ),
            listOf(LlmStreamEvent.TextDelta("Fertig."), LlmStreamEvent.Completed(StopReason.END_TURN)),
        ),
    )

    private fun vm(client: LlmClient) = AgentViewModel(repo, lsp, sync, gradle, store, mcp, connections) { client }.also {
        it.onEvent(AgentUiEvent.Initialize(tmp.path))
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        tmp.deleteRecursively()
        File(tmp.parentFile, tmp.name + "_hist").deleteRecursively()
        connDir.deleteRecursively()
    }

    /** Dateizugriffe laufen auf Dispatchers.IO: auf den Zielzustand warten (höchstens 10 s). */
    private fun AgentViewModel.await(condition: (AgentUiState) -> Boolean) {
        val end = System.nanoTime() + 10_000_000_000L
        while (!condition(state.value) && System.nanoTime() < end) Thread.sleep(10)
        assertTrue("Zustand nicht erreicht: ${state.value}", condition(state.value))
    }

    private fun AgentViewModel.awaitIdle() = await { !it.running && it.pendingApproval == null }

    private fun AgentViewModel.ask(text: String) {
        onEvent(AgentUiEvent.InputChanged(text))
        onEvent(AgentUiEvent.Send)
    }

    @Test
    fun `Auto-Modus schreibt Datei, zeigt Werkzeugaufruf, Antwort und Aenderungen`() {
        val vm = vm(createFileThenAnswer())
        vm.onEvent(AgentUiEvent.ModeChanged(PermissionMode.AUTO_ACCEPT))
        vm.ask("lege neu.txt an")
        vm.awaitIdle()
        val s = vm.state.value
        assertFalse(s.running)
        assertEquals("hallo", File(tmp, "neu.txt").readText())
        assertTrue(s.items.any { it is AgentChatItem.ToolCall && it.status == ToolCallStatus.OK })
        assertTrue(s.items.any { it is AgentChatItem.Assistant && it.text == "Fertig." })
        val changes = s.items.filterIsInstance<AgentChatItem.Changes>().single()
        assertEquals(listOf("neu.txt"), changes.paths)
        assertTrue(changes.files.single().diff.contains("+hallo"))
        assertEquals(1, repo.count)
    }

    @Test
    fun `Revert loescht die neu angelegte Datei wieder`() {
        val vm = vm(createFileThenAnswer())
        vm.onEvent(AgentUiEvent.ModeChanged(PermissionMode.AUTO_ACCEPT))
        vm.ask("x")
        vm.awaitIdle()
        val changes = vm.state.value.items.filterIsInstance<AgentChatItem.Changes>().single()
        vm.onEvent(AgentUiEvent.Revert(changes.turnId))
        vm.await { st -> st.items.filterIsInstance<AgentChatItem.Changes>().single().reverted }
        assertFalse(File(tmp, "neu.txt").exists())
        assertTrue(vm.state.value.items.filterIsInstance<AgentChatItem.Changes>().single().reverted)
    }

    @Test
    fun `Plan-Modus lehnt Schreiben ab und aendert nichts`() {
        val vm = vm(createFileThenAnswer())
        vm.onEvent(AgentUiEvent.ModeChanged(PermissionMode.PLAN_ONLY))
        vm.ask("x")
        vm.awaitIdle()
        assertFalse(File(tmp, "neu.txt").exists())
        assertTrue(vm.state.value.items.any { it is AgentChatItem.ToolCall && it.status == ToolCallStatus.DENIED })
        assertTrue(vm.state.value.items.none { it is AgentChatItem.Changes })
    }

    @Test
    fun `Fragen-Modus wartet auf Freigabe, Erlauben fuehrt aus`() {
        val vm = vm(createFileThenAnswer())
        vm.ask("x")
        vm.await { it.pendingApproval != null }
        assertTrue(vm.state.value.running)
        assertEquals("create_file", vm.state.value.pendingApproval?.toolName)
        assertTrue(vm.state.value.pendingApproval?.preview?.contains("+hallo") == true)
        assertFalse(File(tmp, "neu.txt").exists())
        vm.onEvent(AgentUiEvent.Decide(true))
        vm.awaitIdle()
        assertTrue(File(tmp, "neu.txt").exists())
        assertFalse(vm.state.value.running)
        assertEquals(null, vm.state.value.pendingApproval)
    }

    @Test
    fun `Fragen-Modus Ablehnen laesst die Datei weg`() {
        val vm = vm(createFileThenAnswer())
        vm.ask("x")
        vm.await { it.pendingApproval != null }
        vm.onEvent(AgentUiEvent.Decide(false))
        vm.awaitIdle()
        assertFalse(File(tmp, "neu.txt").exists())
        assertTrue(vm.state.value.items.any { it is AgentChatItem.ToolCall && it.status == ToolCallStatus.DENIED })
    }

    @Test
    fun `ohne Schluessel gibt es eine Fehlermeldung statt eines Laufs`() {
        options.value = options.value.copy(apiKey = "")
        val vm = vm(createFileThenAnswer())
        vm.ask("x")
        assertTrue(vm.state.value.items.single().let { it is AgentChatItem.Notice && it.isError })
        assertEquals(0, repo.count)
        // OpenAI-kompatibler lokaler Server ohne Schlüssel ist erlaubt, ohne Basis-URL nicht.
        options.value = AiChatOptions(AiProviderType.OPENAI, "m", apiKey = "")
        vm.ask("y")
        assertEquals(2, vm.state.value.items.size)
    }

    @Test
    fun `Neuer Chat verwirft Verlauf`() {
        val vm = vm(createFileThenAnswer())
        vm.onEvent(AgentUiEvent.ModeChanged(PermissionMode.AUTO_ACCEPT))
        vm.ask("x")
        vm.awaitIdle()
        vm.onEvent(AgentUiEvent.NewChat)
        assertTrue(vm.state.value.items.isEmpty())
        assertEquals(PermissionMode.AUTO_ACCEPT, vm.state.value.mode)
    }

    @Test
    fun `Verlauf wird gespeichert und beim Oeffnen des Projekts wiederhergestellt`() {
        val first = vm(createFileThenAnswer())
        first.onEvent(AgentUiEvent.ModeChanged(PermissionMode.AUTO_ACCEPT))
        first.ask("lege neu.txt an")
        first.awaitIdle()
        // Speichern läuft asynchron auf IO.
        val end = System.nanoTime() + 5_000_000_000L
        while (store.load(tmp.path).isEmpty() && System.nanoTime() < end) Thread.sleep(10)

        val second = vm(createFileThenAnswer())
        second.await { it.items.isNotEmpty() }
        val items = second.state.value.items
        assertTrue(items.first() is AgentChatItem.User)
        assertTrue(items.any { it is AgentChatItem.ToolCall && it.status == ToolCallStatus.OK })
        assertTrue(items.any { it is AgentChatItem.Assistant && it.text == "Fertig." })
        assertTrue(items.none { it is AgentChatItem.Changes })

        second.onEvent(AgentUiEvent.NewChat)
        val gone = System.nanoTime() + 5_000_000_000L
        while (store.load(tmp.path).isNotEmpty() && System.nanoTime() < gone) Thread.sleep(10)
        assertTrue(store.load(tmp.path).isEmpty())
    }

    @Test
    fun `MCP-Server hinzufuegen speichert, meldet Zustand, Entfernen loescht`() {
        val vm = vm(createFileThenAnswer())
        vm.onEvent(AgentUiEvent.AddMcpServer("https://example.com/mcp", "", " tok "))
        vm.await { st -> st.mcpServers.any { it.name == "example" && it.status == com.codeforge.libs.mcp.McpStatus.FAILED } }
        val saved = connections.appServers().single()
        assertEquals("Bearer tok", saved.headers["Authorization"])
        vm.onEvent(AgentUiEvent.AddMcpServer("npx -y @scope/files-server /data", "", ""))
        vm.await { st -> st.mcpServers.size == 2 }
        assertEquals("files-server", connections.appServers().last().name)
        vm.onEvent(AgentUiEvent.RemoveMcpServer("example"))
        vm.await { st -> st.mcpServers.size == 1 }
        vm.onEvent(AgentUiEvent.ToggleMcpServer("files-server"))
        vm.await { st -> st.mcpServers.single().status == com.codeforge.libs.mcp.McpStatus.DISABLED }
    }

    @Test
    fun `Projekt-Server aus mcp_json brauchen Freigabe`() {
        File(tmp, ".mcp.json").writeText("""{"mcpServers":{"proj":{"url":"https://proj.example/mcp"}}}""")
        val vm = vm(createFileThenAnswer())
        vm.await { st -> st.mcpServers.any { it.name == "proj" && it.status == com.codeforge.libs.mcp.McpStatus.NEEDS_TRUST } }
        vm.onEvent(AgentUiEvent.TrustMcpServer("proj"))
        vm.await { st -> st.mcpServers.single { it.name == "proj" }.status == com.codeforge.libs.mcp.McpStatus.FAILED }
    }

    @Test
    fun `Anhaenge werden gemerkt, entfernt und mit dem Senden geleert`() {
        val vm = vm(createFileThenAnswer())
        val a = com.codeforge.core.domain.agent.AgentAttachment("/p/A.kt", 1, 2, "code")
        vm.onEvent(AgentUiEvent.Attach(a))
        vm.onEvent(AgentUiEvent.Attach(a))
        vm.onEvent(AgentUiEvent.Attach(com.codeforge.core.domain.agent.AgentAttachment("/p/B.kt")))
        vm.await { st -> st.attachments.size == 2 }
        vm.onEvent(AgentUiEvent.RemoveAttachment(1))
        vm.await { st -> st.attachments.size == 1 }
        vm.onEvent(AgentUiEvent.InputChanged("erklaere"))
        vm.onEvent(AgentUiEvent.Send)
        vm.await { st -> st.attachments.isEmpty() && st.items.any { it is AgentChatItem.User && it.text.contains("[Auszug: /p/A.kt") } }
    }
}
