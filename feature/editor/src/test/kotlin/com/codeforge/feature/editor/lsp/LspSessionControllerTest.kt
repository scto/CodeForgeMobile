// Modul: :feature:editor
package com.codeforge.feature.editor.lsp

import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspLocation
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerLaunch
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.repository.LspServerResolver
import com.codeforge.core.domain.repository.OpenDocumentInfo
import com.codeforge.core.domain.repository.OpenDocumentsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LspSessionControllerTest {

    private class FakeLspClientRepository : LspClientRepository {
        var startCount = 0
        var stopCount = 0
        var stopSessionCount = 0
        var stopAllCount = 0
        var lastCommand: List<String>? = null
        var lastWorkspaceRoot: String? = null
        var lastEnv: Map<String, String>? = null
        var shouldFailStart = false

        val activeSessions = mutableMapOf<String, LspServerState>()
        val sessionLastUsedMap = mutableMapOf<String, Long>()
        val sessionOpenDocCounts = mutableMapOf<String, Int>()
        val openedDocuments = mutableListOf<Triple<String, String, String>>()

        private val _serverState = MutableStateFlow(LspServerState.STOPPED)
        override val serverState: StateFlow<LspServerState> = _serverState.asStateFlow()

        private val _sessionStates = MutableStateFlow<Map<String, LspServerState>>(emptyMap())
        override val sessionStates: StateFlow<Map<String, LspServerState>> = _sessionStates.asStateFlow()

        private val _diagnostics = MutableSharedFlow<Pair<String, List<LspDiagnostic>>>()
        override val diagnostics: Flow<Pair<String, List<LspDiagnostic>>> = _diagnostics.asSharedFlow()

        override suspend fun startSession(
            sessionId: String,
            languageIds: Set<String>,
            serverCommand: List<String>,
            workspaceRootPath: String,
            extraProcessEnv: Map<String, String>
        ): Result<Unit> {
            startCount++
            lastCommand = serverCommand
            lastWorkspaceRoot = workspaceRootPath
            lastEnv = extraProcessEnv
            return if (shouldFailStart) {
                activeSessions[sessionId] = LspServerState.FAILED
                _sessionStates.value = activeSessions.toMap()
                _serverState.value = LspServerState.FAILED
                Result.failure(RuntimeException("Start failed"))
            } else {
                activeSessions[sessionId] = LspServerState.RUNNING
                _sessionStates.value = activeSessions.toMap()
                _serverState.value = LspServerState.RUNNING
                Result.success(Unit)
            }
        }

        override suspend fun stopSession(sessionId: String) {
            stopCount++
            stopSessionCount++
            activeSessions.remove(sessionId)
            _sessionStates.value = activeSessions.toMap()
            _serverState.value = LspServerState.STOPPED
        }

        override suspend fun stopAll() {
            stopCount++
            stopAllCount++
            activeSessions.clear()
            _sessionStates.value = emptyMap()
            _serverState.value = LspServerState.STOPPED
        }

        override suspend fun start(
            serverCommand: List<String>,
            workspaceRootPath: String,
            extraProcessEnv: Map<String, String>
        ): Result<Unit> = startSession("default", emptySet(), serverCommand, workspaceRootPath, extraProcessEnv)

        override suspend fun stop() = stopAll()

        override fun getRunningSessionIds(): List<String> = activeSessions.keys.toList()

        override fun getSessionLastUsed(sessionId: String): Long = sessionLastUsedMap[sessionId] ?: 0L

        override fun getOpenDocumentCount(sessionId: String): Int = sessionOpenDocCounts[sessionId] ?: 0

        override suspend fun didOpen(path: String, languageId: String, content: String) {
            openedDocuments.add(Triple(path, languageId, content))
        }

        override suspend fun didChange(path: String, newContent: String, version: Int) {}
        override suspend fun didClose(path: String) {}

        private val _serverCapabilities = MutableStateFlow<LspServerCapabilities?>(null)
        override val serverCapabilities: StateFlow<LspServerCapabilities?> = _serverCapabilities.asStateFlow()

        override var applyEditHandler: (suspend (com.codeforge.core.domain.model.LspWorkspaceEdit) -> Boolean)? = null

        override suspend fun requestCompletion(path: String, position: LspPosition): Result<List<LspCompletionItem>> =
            Result.success(emptyList())
        override suspend fun requestHover(path: String, position: LspPosition): Result<LspHoverInfo?> =
            Result.success(null)
        override suspend fun requestFormat(path: String, content: String): Result<String> =
            Result.success(content)
        override suspend fun requestRangeFormat(path: String, content: String, range: LspRange): Result<String> =
            Result.success(content)
        override suspend fun requestCodeActions(
            path: String,
            range: LspRange,
            diagnostics: List<LspDiagnostic>
        ): Result<List<com.codeforge.core.domain.model.LspCodeAction>> = Result.success(emptyList())
        override suspend fun requestPrepareRename(
            path: String,
            position: LspPosition
        ): Result<com.codeforge.core.domain.model.LspPrepareRenameResult?> = Result.success(null)
        override suspend fun requestRename(
            path: String,
            position: LspPosition,
            newName: String
        ): Result<com.codeforge.core.domain.model.LspWorkspaceEdit?> = Result.success(null)
        override suspend fun requestDocumentSymbols(path: String): Result<List<com.codeforge.core.domain.model.LspDocumentSymbol>> =
            Result.success(emptyList())
        override suspend fun requestDefinition(path: String, position: LspPosition): Result<List<LspLocation>> =
            Result.success(emptyList())
        override suspend fun requestReferences(
            path: String,
            position: LspPosition,
            includeDeclaration: Boolean
        ): Result<List<LspLocation>> = Result.success(emptyList())
        override suspend fun executeCommand(command: String, arguments: List<Any?>): Result<Unit> =
            Result.success(Unit)
    }

    private class FakeLspServerResolver(
        private val mapping: Map<String, LspServerLaunch?> = emptyMap()
    ) : LspServerResolver {
        var shouldThrow = false

        override suspend fun resolve(languageId: String): LspServerLaunch? {
            if (shouldThrow) throw RuntimeException("Resolver error")
            return mapping[languageId]
        }
    }

    @Test
    fun `resolver returns null - server does not start and state is STOPPED`() = runBlocking {
        val client = FakeLspClientRepository()
        val resolver = FakeLspServerResolver(emptyMap())
        val controller = LspSessionController(client, resolver)

        controller.onFileOpened(languageId = "plaintext", workspaceRoot = "/workspace")

        assertEquals("start should not be called", 0, client.startCount)
        assertEquals(LspServerState.STOPPED, controller.serverState.value)
    }

    @Test
    fun `resolver returns launch - server starts exactly once for same workspace and language`() = runBlocking {
        val client = FakeLspClientRepository()
        val launch = LspServerLaunch(command = listOf("/bin/kotlin-ls"), env = mapOf("PATH" to "/bin"), serverId = "kotlin-ls")
        val resolver = FakeLspServerResolver(mapOf("kotlin" to launch))
        val controller = LspSessionController(client, resolver)

        controller.onFileOpened(languageId = "kotlin", workspaceRoot = "/workspace")

        assertEquals(1, client.startCount)
        assertEquals(listOf("/bin/kotlin-ls"), client.lastCommand)
        assertEquals("/workspace", client.lastWorkspaceRoot)
        assertEquals(LspServerState.RUNNING, controller.serverState.value)

        // Second call with same language and workspace: must not start again
        controller.onFileOpened(languageId = "kotlin", workspaceRoot = "/workspace")
        assertEquals(1, client.startCount)
    }

    @Test
    fun `switching language with maxSessions 1 stops previous server and starts new one`() = runBlocking {
        val client = FakeLspClientRepository()
        val launchKotlin = LspServerLaunch(command = listOf("/bin/kotlin-ls"), serverId = "kotlin-ls")
        val launchJava = LspServerLaunch(command = listOf("/bin/jdtls"), serverId = "jdtls")
        val resolver = FakeLspServerResolver(mapOf("kotlin" to launchKotlin, "java" to launchJava))
        val controller = LspSessionController(client, resolver).apply {
            maxConcurrentSessions = 1 // Reproduces legacy single-server behavior
        }

        controller.onFileOpened(languageId = "kotlin", workspaceRoot = "/workspace")
        assertEquals(1, client.startCount)
        assertEquals(0, client.stopCount)

        // Switch language to java with limit 1: evicts kotlin
        controller.onFileOpened(languageId = "java", workspaceRoot = "/workspace")
        assertEquals("Previous server should be stopped", 1, client.stopCount)
        assertEquals("New server should be started", 2, client.startCount)
        assertEquals(listOf("/bin/jdtls"), client.lastCommand)
        assertEquals(LspServerState.RUNNING, controller.serverState.value)
    }

    @Test
    fun `tab switch Kotlin to YAML to Kotlin starts each server exactly once when maxSessions greater than 1`() = runBlocking {
        val client = FakeLspClientRepository()
        val launchKotlin = LspServerLaunch(command = listOf("/bin/kotlin-ls"), serverId = "kotlin-ls")
        val launchYaml = LspServerLaunch(command = listOf("/bin/yaml-ls"), serverId = "yaml-ls")
        val resolver = FakeLspServerResolver(mapOf("kotlin" to launchKotlin, "yaml" to launchYaml))
        val controller = LspSessionController(client, resolver).apply {
            maxConcurrentSessions = 3
        }

        // Open Kotlin tab
        controller.onFileOpened(languageId = "kotlin", workspaceRoot = "/workspace", path = "/workspace/App.kt")
        assertEquals(1, client.startCount)
        assertEquals(0, client.stopCount)

        // Switch to YAML tab: YAML starts, Kotlin is NOT stopped
        controller.onFileOpened(languageId = "yaml", workspaceRoot = "/workspace", path = "/workspace/spec.yaml")
        assertEquals(2, client.startCount)
        assertEquals(0, client.stopCount)
        assertEquals(2, client.getRunningSessionIds().size)

        // Switch back to Kotlin tab: nothing restarts, nothing stops
        controller.onFileOpened(languageId = "kotlin", workspaceRoot = "/workspace", path = "/workspace/App.kt")
        assertEquals(2, client.startCount)
        assertEquals(0, client.stopCount)
    }

    @Test
    fun `changing workspace root stops all previous sessions`() = runBlocking {
        val client = FakeLspClientRepository()
        val launch = LspServerLaunch(command = listOf("/bin/kotlin-ls"), serverId = "kotlin-ls")
        val resolver = FakeLspServerResolver(mapOf("kotlin" to launch))
        val controller = LspSessionController(client, resolver)

        controller.onFileOpened(languageId = "kotlin", workspaceRoot = "/workspace1")
        assertEquals(1, client.startCount)

        // Change workspace root to /workspace2
        controller.onFileOpened(languageId = "kotlin", workspaceRoot = "/workspace2")
        assertEquals(1, client.stopAllCount)
        assertEquals(2, client.startCount)
    }

    @Test
    fun `closing project or stop resets controller state and stops all clients`() = runBlocking {
        val client = FakeLspClientRepository()
        val launch = LspServerLaunch(command = listOf("/bin/kotlin-ls"), serverId = "kotlin-ls")
        val resolver = FakeLspServerResolver(mapOf("kotlin" to launch))
        val controller = LspSessionController(client, resolver)

        controller.onFileOpened(languageId = "kotlin", workspaceRoot = "/workspace")
        assertEquals(LspServerState.RUNNING, controller.serverState.value)

        controller.onProjectClosed()
        assertEquals(1, client.stopCount)
        assertEquals(LspServerState.STOPPED, controller.serverState.value)
    }

    @Test
    fun `start failure records cooldown period and manual restart bypasses it`() = runBlocking {
        var virtualTime = 1000L
        val client = FakeLspClientRepository().apply { shouldFailStart = true }
        val launch = LspServerLaunch(command = listOf("/bin/broken"), serverId = "broken")
        val resolver = FakeLspServerResolver(mapOf("broken" to launch))
        val controller = LspSessionController(client, resolver).apply {
            timeSource = { virtualTime }
            errorCooldownMillis = 30_000L
        }

        // First attempt fails
        controller.onFileOpened(languageId = "broken", workspaceRoot = "/workspace")
        assertEquals(1, client.startCount)

        // Second attempt 5 seconds later is suppressed by cooldown
        virtualTime += 5_000L
        controller.onFileOpened(languageId = "broken", workspaceRoot = "/workspace")
        assertEquals(1, client.startCount)

        // Manual restart bypasses cooldown
        client.shouldFailStart = false
        controller.restartSession("broken@/workspace")
        assertEquals(2, client.startCount)
        assertEquals(LspServerState.RUNNING, controller.serverState.value)
    }

    @Test
    fun `eviction with maxSessions 2 evicts least recently used session but never active document`() = runBlocking {
        val client = FakeLspClientRepository()
        val launch1 = LspServerLaunch(command = listOf("/bin/s1"), serverId = "s1")
        val launch2 = LspServerLaunch(command = listOf("/bin/s2"), serverId = "s2")
        val launch3 = LspServerLaunch(command = listOf("/bin/s3"), serverId = "s3")
        val resolver = FakeLspServerResolver(mapOf("l1" to launch1, "l2" to launch2, "l3" to launch3))

        val controller = LspSessionController(client, resolver).apply {
            maxConcurrentSessions = 2
        }

        // Session 1 started and active with /workspace/doc1.l1
        controller.onFileOpened(languageId = "l1", workspaceRoot = "/workspace", path = "/workspace/doc1.l1")
        client.sessionLastUsedMap["s1@/workspace"] = 100L

        // Session 2 started with /workspace/doc2.l2
        controller.onFileOpened(languageId = "l2", workspaceRoot = "/workspace", path = "/workspace/doc2.l2")
        client.sessionLastUsedMap["s2@/workspace"] = 200L

        // Now set active document back to doc1.l1
        controller.onFileOpened(languageId = "l1", workspaceRoot = "/workspace", path = "/workspace/doc1.l1")
        // Now open 3rd session for l3 while doc1 remains the active document.
        // Limit is 2: s1 is active document, so s2 must be evicted!
        controller.onFileOpened(languageId = "l3", workspaceRoot = "/workspace")

        val running = client.getRunningSessionIds()
        assertEquals(2, running.size)
        assertTrue("Active document session s1 must not be evicted", running.contains("s1@/workspace"))
        assertTrue("New session s3 must be running", running.contains("s3@/workspace"))
        assertFalse("Least recently used session s2 must be evicted", running.contains("s2@/workspace"))
    }

    @Test
    fun `idle timeout stops session with zero open documents using virtual time`() = runBlocking {
        var virtualTime = 10_000L
        val client = FakeLspClientRepository()
        val launch = LspServerLaunch(command = listOf("/bin/kotlin-ls"), serverId = "kotlin-ls")
        val resolver = FakeLspServerResolver(mapOf("kotlin" to launch))

        val controller = LspSessionController(client, resolver).apply {
            timeSource = { virtualTime }
            idleTimeoutMinutes = 10
        }

        controller.onFileOpened(languageId = "kotlin", workspaceRoot = "/workspace")
        client.sessionLastUsedMap["kotlin-ls@/workspace"] = virtualTime
        client.sessionOpenDocCounts["kotlin-ls@/workspace"] = 0

        // 5 minutes later: not idle yet
        virtualTime += 5 * 60 * 1000L
        controller.checkIdleSessions()
        assertEquals(1, client.getRunningSessionIds().size)

        // 11 minutes later: timeout exceeded
        virtualTime += 6 * 60 * 1000L
        controller.checkIdleSessions()
        assertEquals(0, client.getRunningSessionIds().size)
    }

    @Test
    fun `re-registering open documents on server start sends didOpen with unsaved content`() = runBlocking {
        val client = FakeLspClientRepository()
        val launch = LspServerLaunch(command = listOf("/bin/kotlin-ls"), serverId = "kotlin-ls")
        val resolver = FakeLspServerResolver(mapOf("kotlin" to launch))

        val controller = LspSessionController(client, resolver)
        controller.setOpenDocumentsProvider(object : OpenDocumentsProvider {
            override fun getOpenDocuments(): List<OpenDocumentInfo> = listOf(
                OpenDocumentInfo("/workspace/Main.kt", "kotlin", "val modified = 42"),
                OpenDocumentInfo("/workspace/App.kt", "kotlin", "class App"),
                OpenDocumentInfo("/workspace/notes.txt", "plaintext", "notes")
            )
        })

        controller.onFileOpened(languageId = "kotlin", workspaceRoot = "/workspace")

        assertEquals(2, client.openedDocuments.size)
        assertEquals("/workspace/Main.kt", client.openedDocuments[0].first)
        assertEquals("val modified = 42", client.openedDocuments[0].third)
        assertEquals("/workspace/App.kt", client.openedDocuments[1].first)
    }
}
