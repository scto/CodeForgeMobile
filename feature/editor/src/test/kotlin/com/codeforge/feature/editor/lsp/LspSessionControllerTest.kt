// Modul: :feature:editor
package com.codeforge.feature.editor.lsp

import com.codeforge.core.domain.model.LspCompletionItem
import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspHoverInfo
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspServerLaunch
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.repository.LspClientRepository
import com.codeforge.core.domain.repository.LspServerResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LspSessionControllerTest {

    private class FakeLspClientRepository : LspClientRepository {
        var startCount = 0
        var stopCount = 0
        var lastCommand: List<String>? = null
        var lastWorkspaceRoot: String? = null
        var lastEnv: Map<String, String>? = null
        var shouldFailStart = false

        private val _serverState = MutableStateFlow(LspServerState.STOPPED)
        override val serverState: StateFlow<LspServerState> = _serverState.asStateFlow()

        private val _diagnostics = MutableSharedFlow<Pair<String, List<LspDiagnostic>>>()
        override val diagnostics: Flow<Pair<String, List<LspDiagnostic>>> = _diagnostics.asSharedFlow()

        override suspend fun start(
            serverCommand: List<String>,
            workspaceRootPath: String,
            extraProcessEnv: Map<String, String>
        ): Result<Unit> {
            startCount++
            lastCommand = serverCommand
            lastWorkspaceRoot = workspaceRootPath
            lastEnv = extraProcessEnv
            return if (shouldFailStart) {
                _serverState.value = LspServerState.FAILED
                Result.failure(RuntimeException("Start failed"))
            } else {
                _serverState.value = LspServerState.RUNNING
                Result.success(Unit)
            }
        }

        override suspend fun stop() {
            stopCount++
            _serverState.value = LspServerState.STOPPED
        }

        override suspend fun didOpen(path: String, languageId: String, content: String) {}
        override suspend fun didChange(path: String, newContent: String, version: Int) {}
        override suspend fun didClose(path: String) {}
        private val _serverCapabilities = MutableStateFlow<com.codeforge.core.domain.model.LspServerCapabilities?>(null)
        override val serverCapabilities: StateFlow<com.codeforge.core.domain.model.LspServerCapabilities?> = _serverCapabilities.asStateFlow()

        override var applyEditHandler: (suspend (com.codeforge.core.domain.model.LspWorkspaceEdit) -> Boolean)? = null

        override suspend fun requestCompletion(path: String, position: LspPosition): Result<List<LspCompletionItem>> =
            Result.success(emptyList())
        override suspend fun requestHover(path: String, position: LspPosition): Result<LspHoverInfo?> =
            Result.success(null)
        override suspend fun requestFormat(path: String, content: String): Result<String> =
            Result.success(content)
        override suspend fun requestCodeActions(
            path: String,
            range: com.codeforge.core.domain.model.LspRange,
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
        override suspend fun requestDefinition(path: String, position: LspPosition): Result<List<com.codeforge.core.domain.model.LspLocation>> =
            Result.success(emptyList())
        override suspend fun requestReferences(
            path: String,
            position: LspPosition,
            includeDeclaration: Boolean
        ): Result<List<com.codeforge.core.domain.model.LspLocation>> = Result.success(emptyList())
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
        val launch = LspServerLaunch(command = listOf("/bin/kotlin-ls"), env = mapOf("PATH" to "/bin"))
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
    fun `switching language or workspace stops previous server and starts new one`() = runBlocking {
        val client = FakeLspClientRepository()
        val launchKotlin = LspServerLaunch(command = listOf("/bin/kotlin-ls"))
        val launchJava = LspServerLaunch(command = listOf("/bin/jdtls"))
        val resolver = FakeLspServerResolver(mapOf("kotlin" to launchKotlin, "java" to launchJava))
        val controller = LspSessionController(client, resolver)

        controller.onFileOpened(languageId = "kotlin", workspaceRoot = "/workspace")
        assertEquals(1, client.startCount)
        assertEquals(0, client.stopCount)

        // Switch language to java
        controller.onFileOpened(languageId = "java", workspaceRoot = "/workspace")
        assertEquals("Previous server should be stopped", 1, client.stopCount)
        assertEquals("New server should be started", 2, client.startCount)
        assertEquals(listOf("/bin/jdtls"), client.lastCommand)
        assertEquals(LspServerState.RUNNING, controller.serverState.value)
    }

    @Test
    fun `closing project or stop resets controller state and stops client`() = runBlocking {
        val client = FakeLspClientRepository()
        val launch = LspServerLaunch(command = listOf("/bin/kotlin-ls"))
        val resolver = FakeLspServerResolver(mapOf("kotlin" to launch))
        val controller = LspSessionController(client, resolver)

        controller.onFileOpened(languageId = "kotlin", workspaceRoot = "/workspace")
        assertEquals(LspServerState.RUNNING, controller.serverState.value)

        controller.onProjectClosed()
        assertEquals(1, client.stopCount)
        assertEquals(LspServerState.STOPPED, controller.serverState.value)
    }

    @Test
    fun `start failure or resolver exception transitions to ERROR state without crashing`() = runBlocking {
        val client = FakeLspClientRepository().apply { shouldFailStart = true }
        val launch = LspServerLaunch(command = listOf("/bin/broken"))
        val resolver = FakeLspServerResolver(mapOf("broken" to launch))
        val controller = LspSessionController(client, resolver)

        controller.onFileOpened(languageId = "broken", workspaceRoot = "/workspace")
        assertEquals(LspServerState.ERROR, controller.serverState.value)

        // Resolver error
        resolver.shouldThrow = true
        controller.onFileOpened(languageId = "error", workspaceRoot = "/workspace")
        assertEquals(LspServerState.ERROR, controller.serverState.value)
    }
}
