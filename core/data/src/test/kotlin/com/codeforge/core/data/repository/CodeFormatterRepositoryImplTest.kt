package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.LspDiagnostic
import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.LspServerCapabilities
import com.codeforge.core.domain.model.LspServerState
import com.codeforge.core.domain.model.UnifiedFormatSource
import com.codeforge.core.domain.repository.LspClientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking as runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeoutException

class CodeFormatterRepositoryImplTest {

    private lateinit var fakeLspClient: FakeLspClient
    private lateinit var fakeProcessExecutor: FakeProcessExecutor
    private lateinit var repository: CodeFormatterRepositoryImpl

    @Before
    fun setup() {
        fakeLspClient = FakeLspClient()
        fakeProcessExecutor = FakeProcessExecutor()
        repository = CodeFormatterRepositoryImpl(fakeLspClient, fakeProcessExecutor)
    }

    @Test
    fun `strategy 1 uses LSP when server is running and formattingProvider is true`() = runTest {
        fakeLspClient.setServerState(LspServerState.RUNNING)
        fakeLspClient.setCapabilities(LspServerCapabilities(formattingProvider = true))
        fakeLspClient.formatResult = "class LSPFormatted {}"

        val result = repository.formatFile("Main.kt", "class A{}")
        assertTrue(result.isSuccess)
        val formatted = result.getOrThrow()
        assertEquals(UnifiedFormatSource.LSP, formatted.source)
        assertEquals("LSP", formatted.sourceName)
        assertEquals("class LSPFormatted {}", formatted.text)
        assertFalse(formatted.isUnchanged)
    }

    @Test
    fun `strategy 1 uses LSP range format when rangeFormattingProvider is true`() = runTest {
        fakeLspClient.setServerState(LspServerState.RUNNING)
        fakeLspClient.setCapabilities(LspServerCapabilities(rangeFormattingProvider = true))
        fakeLspClient.rangeFormatResult = "fun foo() = 42"

        val range = LspRange(LspPosition(1, 0), LspPosition(1, 10))
        val result = repository.formatRange("Main.kt", "class A {\nfun foo()=42\n}", range)
        assertTrue(result.isSuccess)
        val formatted = result.getOrThrow()
        assertEquals(UnifiedFormatSource.LSP, formatted.source)
        assertEquals("fun foo() = 42", formatted.text)
    }

    @Test
    fun `strategy 1 resolves capabilities per path rather than globally`() = runTest {
        // Global state is STOPPED, but path-specific state for schema.yaml is RUNNING with formatting
        fakeLspClient.setServerState(LspServerState.STOPPED)
        fakeLspClient.setCapabilities(null)

        fakeLspClient.pathStates["schema.yaml"] = LspServerState.RUNNING
        fakeLspClient.pathCapabilities["schema.yaml"] = LspServerCapabilities(formattingProvider = true)
        fakeLspClient.formatResult = "name: codeforge\nversion: 1"

        val result = repository.formatFile("schema.yaml", "name:codeforge\nversion:1")
        assertTrue(result.isSuccess)
        val formatted = result.getOrThrow()
        assertEquals(UnifiedFormatSource.LSP, formatted.source)
        assertEquals("name: codeforge\nversion: 1", formatted.text)
    }

    @Test
    fun `strategy 2 uses ktlint process for kotlin when LSP is unavailable and ktlint exists`() = runTest {
        fakeLspClient.setServerState(LspServerState.STOPPED)
        fakeProcessExecutor.availableExecutables["ktlint"] = "/usr/bin/ktlint"
        fakeProcessExecutor.mockResult = ProcessExecutionResult(
            exitCode = 0,
            stdout = "fun calculate(): Int = 42\n",
            stderr = ""
        )

        val result = repository.formatFile("Main.kt", "fun calculate():Int=42")
        assertTrue(result.isSuccess)
        val formatted = result.getOrThrow()
        assertEquals(UnifiedFormatSource.KTLINT, formatted.source)
        assertEquals("ktlint", formatted.sourceName)
        assertEquals("fun calculate(): Int = 42\n", formatted.text)
    }

    @Test
    fun `strategy 2 returns failure on ktlint timeout without modifying file`() = runTest {
        fakeLspClient.setServerState(LspServerState.STOPPED)
        fakeProcessExecutor.availableExecutables["ktlint"] = "/usr/bin/ktlint"
        fakeProcessExecutor.mockResult = ProcessExecutionResult(
            exitCode = -1,
            stdout = "",
            stderr = "Timed out",
            timedOut = true
        )

        val result = repository.formatFile("Main.kt", "fun test() {}")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is TimeoutException)
    }

    @Test
    fun `strategy 2 returns failure on ktlint syntax error without modifying file`() = runTest {
        fakeLspClient.setServerState(LspServerState.STOPPED)
        fakeProcessExecutor.availableExecutables["ktlint"] = "/usr/bin/ktlint"
        fakeProcessExecutor.mockResult = ProcessExecutionResult(
            exitCode = 1,
            stdout = "",
            stderr = "Main.kt:1:1: Syntax error: Expecting a top level declaration"
        )

        val result = repository.formatFile("Main.kt", "class { invalid }")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Syntax") == true)
    }

    @Test
    fun `strategy 2 uses google-java-format for java when available`() = runTest {
        fakeLspClient.setServerState(LspServerState.STOPPED)
        fakeProcessExecutor.availableExecutables["google-java-format"] = "/usr/bin/google-java-format"
        fakeProcessExecutor.mockResult = ProcessExecutionResult(
            exitCode = 0,
            stdout = "class Main {\n  void foo() {}\n}\n",
            stderr = ""
        )

        val result = repository.formatFile("Main.java", "class Main{void foo(){}}")
        assertTrue(result.isSuccess)
        val formatted = result.getOrThrow()
        assertEquals(UnifiedFormatSource.GOOGLE_JAVA_FORMAT, formatted.source)
        assertEquals("google-java-format", formatted.sourceName)
        assertEquals("class Main {\n  void foo() {}\n}\n", formatted.text)
    }

    @Test
    fun `strategy 3 falls back to builtin simplified formatter when no LSP or process available`() = runTest {
        fakeLspClient.setServerState(LspServerState.STOPPED)
        // No executables registered in fakeProcessExecutor

        val input = "class A {\nfun f() {\nval x = 1\n}\n}"
        val result = repository.formatFile("Main.kt", input)
        assertTrue(result.isSuccess)
        val formatted = result.getOrThrow()
        assertEquals(UnifiedFormatSource.BUILTIN_SIMPLIFIED, formatted.source)
        assertTrue(formatted.text.contains("    fun f() {"))
        assertTrue(formatted.text.contains("        val x = 1"))
    }

    @Test
    fun `strategy 3 formats json with builtin pretty printer`() = runTest {
        fakeLspClient.setServerState(LspServerState.STOPPED)
        val result = repository.formatFile("test.json", "{\"a\":1,\"b\":[2,3]}")
        assertTrue(result.isSuccess)
        val formatted = result.getOrThrow()
        assertEquals(UnifiedFormatSource.BUILTIN_SIMPLIFIED, formatted.source)
        assertTrue(formatted.text.contains("    \"a\": 1"))
    }

    @Test
    fun `strategy 3 formats xml with builtin pretty printer`() = runTest {
        fakeLspClient.setServerState(LspServerState.STOPPED)
        val input = "<root><child name=\"x\"/></root>"
        val result = repository.formatFile("layout.xml", input)
        assertTrue(result.isSuccess)
        val formatted = result.getOrThrow()
        assertEquals(UnifiedFormatSource.BUILTIN_SIMPLIFIED, formatted.source)
        assertTrue(formatted.text.contains("    <child name=\"x\"/>"))
    }

    @Test
    fun `range formatting with fallback only modifies selected lines`() = runTest {
        fakeLspClient.setServerState(LspServerState.STOPPED)
        val input = "val top = 1\nfun calc() {\nreturn 42\n}\nval bottom = 2"
        val range = LspRange(LspPosition(1, 0), LspPosition(3, 1))

        val result = repository.formatRange("Main.kt", input, range)
        assertTrue(result.isSuccess)
        val formatted = result.getOrThrow()
        assertTrue(formatted.text.startsWith("val top = 1\n"))
        assertTrue(formatted.text.contains("    return 42"))
        assertTrue(formatted.text.endsWith("\nval bottom = 2"))
    }

    private class FakeLspClient : LspClientRepository {
        private val _serverState = MutableStateFlow(LspServerState.STOPPED)
        override val serverState: StateFlow<LspServerState> = _serverState.asStateFlow()
        fun setServerState(state: LspServerState) { _serverState.value = state }

        override val diagnostics: Flow<Pair<String, List<LspDiagnostic>>> = emptyFlow()

        private val _capabilities = MutableStateFlow<LspServerCapabilities?>(null)
        override val serverCapabilities: StateFlow<LspServerCapabilities?> = _capabilities.asStateFlow()
        fun setCapabilities(caps: LspServerCapabilities?) { _capabilities.value = caps }

        override var applyEditHandler: (suspend (com.codeforge.core.domain.model.LspWorkspaceEdit) -> Boolean)? = null

        var formatResult: String = ""
        var rangeFormatResult: String = ""
        val pathCapabilities = mutableMapOf<String, LspServerCapabilities?>()
        val pathStates = mutableMapOf<String, LspServerState>()

        override fun capabilitiesFor(path: String): LspServerCapabilities? =
            pathCapabilities[path] ?: serverCapabilities.value

        override fun stateFor(path: String): LspServerState =
            pathStates[path] ?: serverState.value

        override suspend fun start(serverCommand: List<String>, workspaceRootPath: String, extraProcessEnv: Map<String, String>) = Result.success(Unit)
        override suspend fun stop() {}
        override suspend fun didOpen(path: String, languageId: String, content: String) {}
        override suspend fun didChange(path: String, newContent: String, version: Int) {}
        override suspend fun didClose(path: String) {}
        override suspend fun requestCompletion(path: String, position: LspPosition) = Result.success(emptyList<com.codeforge.core.domain.model.LspCompletionItem>())
        override suspend fun requestHover(path: String, position: LspPosition) = Result.success(null)
        override suspend fun requestFormat(path: String, content: String): Result<String> = Result.success(formatResult)
        override suspend fun requestRangeFormat(path: String, content: String, range: LspRange): Result<String> = Result.success(rangeFormatResult)
        override suspend fun requestCodeActions(path: String, range: LspRange, diagnostics: List<LspDiagnostic>) = Result.success(emptyList<com.codeforge.core.domain.model.LspCodeAction>())
        override suspend fun requestPrepareRename(path: String, position: LspPosition) = Result.success(null)
        override suspend fun requestRename(path: String, position: LspPosition, newName: String) = Result.success(null)
        override suspend fun requestDocumentSymbols(path: String) = Result.success(emptyList<com.codeforge.core.domain.model.LspDocumentSymbol>())
        override suspend fun requestDefinition(path: String, position: LspPosition) = Result.success(emptyList<com.codeforge.core.domain.model.LspLocation>())
        override suspend fun requestReferences(path: String, position: LspPosition, includeDeclaration: Boolean) = Result.success(emptyList<com.codeforge.core.domain.model.LspLocation>())
        override suspend fun executeCommand(command: String, arguments: List<Any?>) = Result.success(Unit)
    }

    private class FakeProcessExecutor : ProcessExecutor {
        val availableExecutables = mutableMapOf<String, String>()
        var mockResult = ProcessExecutionResult(0, "", "")

        override suspend fun execute(command: List<String>, stdin: String?, timeoutSeconds: Long): ProcessExecutionResult {
            return mockResult
        }

        override fun findExecutable(name: String, extraPaths: List<String>): String? {
            return availableExecutables[name]
        }
    }
}
