/**
 * Modul: :libs:debug-client
 * @author Thomas Schmid
 */
package com.codeforge.libs.debug_client.adb

import com.codeforge.core.domain.model.AndroidDebugTarget
import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.domain.model.DebugEvent
import com.codeforge.core.domain.model.DebugSessionState
import com.codeforge.core.domain.model.DebugStackFrame
import com.codeforge.core.domain.model.Scope
import com.codeforge.core.domain.model.Variable
import com.codeforge.core.domain.repository.DebugRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidDebugManagerTest {

    private lateinit var fakeAdbRunner: FakeAdbRunner
    private lateinit var fakeDebugRepository: FakeDebugRepository
    private lateinit var debugManager: AndroidDebugManagerImpl

    @Before
    fun setUp() {
        fakeAdbRunner = FakeAdbRunner()
        fakeDebugRepository = FakeDebugRepository()
        debugManager = AndroidDebugManagerImpl(
            adbRunner = fakeAdbRunner,
            debugRepository = fakeDebugRepository
        )
    }

    @Test
    fun `getDebuggablePids parses real adb jdwp fixture`() = runTest {
        fakeAdbRunner.commandHandlers["jdwp"] = {
            AdbResult(exitCode = 0, stdout = "1045\n2890\n17432\n", stderr = "")
        }

        val result = debugManager.getDebuggablePids()
        assertTrue(result.isSuccess)
        val pids = result.getOrThrow()
        assertEquals(listOf(1045, 2890, 17432), pids)
    }

    @Test
    fun `isAppDebuggable returns true when dumpsys package contains DEBUGGABLE`() = runTest {
        fakeAdbRunner.commandHandlers["shell dumpsys package com.example.app"] = {
            AdbResult(
                exitCode = 0,
                stdout = """
                    Package [com.example.app] (73b4e9f):
                      userId=10156
                      pkg=Package{8a9c2b com.example.app}
                      codePath=/data/app/~~h81==/com.example.app-1
                      flags=[ DEBUGGABLE HAS_CODE ALLOW_BACKUP ]
                """.trimIndent(),
                stderr = ""
            )
        }

        val result = debugManager.isAppDebuggable("com.example.app")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
    }

    @Test
    fun `isAppDebuggable returns false when package exists but has no DEBUGGABLE flag`() = runTest {
        fakeAdbRunner.commandHandlers["shell dumpsys package com.example.release"] = {
            AdbResult(
                exitCode = 0,
                stdout = """
                    Package [com.example.release] (38b2a1):
                      userId=10189
                      flags=[ HAS_CODE ALLOW_BACKUP ]
                """.trimIndent(),
                stderr = ""
            )
        }

        val result = debugManager.isAppDebuggable("com.example.release")
        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow())
    }

    @Test
    fun `startAppWaitingForDebugger executes am start -D and returns process PID`() = runTest {
        fakeAdbRunner.commandHandlers["shell am start -D -n com.example/.MainActivity"] = {
            AdbResult(exitCode = 0, stdout = "Starting: Intent { cmp=com.example/.MainActivity }", stderr = "")
        }
        fakeAdbRunner.commandHandlers["shell pidof -s com.example"] = {
            AdbResult(exitCode = 0, stdout = "4512\n", stderr = "")
        }
        fakeAdbRunner.commandHandlers["jdwp"] = {
            AdbResult(exitCode = 0, stdout = "4512\n", stderr = "")
        }

        val result = debugManager.startAppWaitingForDebugger("com.example", ".MainActivity", timeoutMs = 2_000L)
        assertTrue(result.isSuccess)
        assertEquals(4512, result.getOrThrow())
    }

    @Test
    fun `forwardPort and removeForward execute expected adb commands`() = runTest {
        fakeAdbRunner.commandHandlers["forward tcp:5005 jdwp:4512"] = {
            AdbResult(exitCode = 0, stdout = "5005", stderr = "")
        }
        fakeAdbRunner.commandHandlers["forward --remove tcp:5005"] = {
            AdbResult(exitCode = 0, stdout = "", stderr = "")
        }

        val fwdResult = debugManager.forwardPort(5005, 4512)
        assertTrue(fwdResult.isSuccess)
        assertTrue(fakeAdbRunner.executedCommands.contains("forward tcp:5005 jdwp:4512"))

        val rmResult = debugManager.removeForward(5005)
        assertTrue(rmResult.isSuccess)
        assertTrue(fakeAdbRunner.executedCommands.contains("forward --remove tcp:5005"))
    }

    @Test
    fun `attach sets up forward and starts debug session`() = runTest {
        fakeAdbRunner.commandHandlers["shell dumpsys package com.example.demo"] = {
            AdbResult(exitCode = 0, stdout = "flags=[ DEBUGGABLE ]", stderr = "")
        }
        fakeAdbRunner.commandHandlers["shell am start -D -n com.example.demo/com.example.demo.MainActivity"] = {
            AdbResult(exitCode = 0, stdout = "Starting: Intent", stderr = "")
        }
        fakeAdbRunner.commandHandlers["shell pidof -s com.example.demo"] = {
            AdbResult(exitCode = 0, stdout = "7890", stderr = "")
        }
        fakeAdbRunner.commandHandlers["jdwp"] = {
            AdbResult(exitCode = 0, stdout = "7890\n", stderr = "")
        }
        fakeAdbRunner.commandHandlers["forward tcp:5005 jdwp:7890"] = {
            AdbResult(exitCode = 0, stdout = "5005", stderr = "")
        }

        val target = AndroidDebugTarget(
            packageName = "com.example.demo",
            activityName = "com.example.demo.MainActivity",
            port = 5005,
            waitForDebugger = true
        )

        val attachResult = debugManager.attach(target)
        assertTrue(attachResult.isSuccess)
        assertTrue(fakeDebugRepository.startCalled)
        assertEquals(5005, fakeDebugRepository.lastConfig?.port)
        assertTrue(fakeDebugRepository.lastConfig?.attachOnly == true)
    }

    @Test
    fun `attach cleans up port forward immediately when debugRepository fails`() = runTest {
        fakeAdbRunner.commandHandlers["shell dumpsys package com.example.fail"] = {
            AdbResult(exitCode = 0, stdout = "flags=[ DEBUGGABLE ]", stderr = "")
        }
        fakeAdbRunner.commandHandlers["shell am start -D -n com.example.fail/com.example.fail.MainActivity"] = {
            AdbResult(exitCode = 0, stdout = "Starting: Intent", stderr = "")
        }
        fakeAdbRunner.commandHandlers["shell pidof -s com.example.fail"] = {
            AdbResult(exitCode = 0, stdout = "8888", stderr = "")
        }
        fakeAdbRunner.commandHandlers["jdwp"] = {
            AdbResult(exitCode = 0, stdout = "8888\n", stderr = "")
        }
        fakeAdbRunner.commandHandlers["forward tcp:5005 jdwp:8888"] = {
            AdbResult(exitCode = 0, stdout = "5005", stderr = "")
        }
        fakeAdbRunner.commandHandlers["forward --remove tcp:5005"] = {
            AdbResult(exitCode = 0, stdout = "", stderr = "")
        }

        fakeDebugRepository.shouldFailStart = true

        val target = AndroidDebugTarget(
            packageName = "com.example.fail",
            activityName = "com.example.fail.MainActivity",
            port = 5005,
            waitForDebugger = true
        )

        val attachResult = debugManager.attach(target)
        assertTrue(attachResult.isFailure)
        assertTrue(fakeAdbRunner.executedCommands.contains("forward --remove tcp:5005"))
    }

    @Test
    fun `detach stops debug repository, removes forward and force-stops app if requested`() = runTest {
        fakeAdbRunner.commandHandlers["forward --remove tcp:5005"] = {
            AdbResult(exitCode = 0, stdout = "", stderr = "")
        }
        fakeAdbRunner.commandHandlers["shell am force-stop com.example.stop"] = {
            AdbResult(exitCode = 0, stdout = "", stderr = "")
        }

        val target = AndroidDebugTarget(
            packageName = "com.example.stop",
            port = 5005,
            stopAppOnDetach = true
        )

        val detachResult = debugManager.detach(target)
        assertTrue(detachResult.isSuccess)
        assertTrue(fakeDebugRepository.stopCalled)
        assertTrue(fakeAdbRunner.executedCommands.contains("forward --remove tcp:5005"))
        assertTrue(fakeAdbRunner.executedCommands.contains("shell am force-stop com.example.stop"))
    }

    private class FakeAdbRunner : AdbRunner {
        var available: Boolean = true
        val executedCommands = mutableListOf<String>()
        val commandHandlers = mutableMapOf<String, () -> AdbResult>()

        override fun isAvailable(): Boolean = available
        override fun getAdbPath(): String? = if (available) "/bin/adb" else null

        override suspend fun execute(vararg args: String): AdbResult {
            val cmd = args.joinToString(" ")
            executedCommands.add(cmd)
            val handler = commandHandlers[cmd]
            return handler?.invoke() ?: AdbResult(exitCode = 0, stdout = "", stderr = "")
        }
    }

    private class FakeDebugRepository : DebugRepository {
        var startCalled = false
        var stopCalled = false
        var shouldFailStart = false
        var lastConfig: DebugConfig? = null

        private val _state = MutableStateFlow<DebugSessionState>(DebugSessionState.Idle)
        override val state: StateFlow<DebugSessionState> = _state.asStateFlow()

        private val _events = MutableSharedFlow<DebugEvent>()
        override val events: Flow<DebugEvent> = _events.asSharedFlow()

        private val _activeBreakpoints = MutableStateFlow<Map<String, List<Breakpoint>>>(emptyMap())
        override val activeBreakpoints: StateFlow<Map<String, List<Breakpoint>>> = _activeBreakpoints.asStateFlow()

        override suspend fun start(config: DebugConfig): Result<Unit> {
            startCalled = true
            lastConfig = config
            if (shouldFailStart) {
                return Result.failure(RuntimeException("DAP Verbindung fehlgeschlagen"))
            }
            _state.value = DebugSessionState.Running
            return Result.success(Unit)
        }

        override suspend fun stop(): Result<Unit> {
            stopCalled = true
            _state.value = DebugSessionState.Terminated(0)
            return Result.success(Unit)
        }

        override suspend fun resume(): Result<Unit> = Result.success(Unit)
        override suspend fun pause(): Result<Unit> = Result.success(Unit)
        override suspend fun stepOver(threadId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun stepInto(threadId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun stepOut(threadId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun setBreakpoints(path: String, breakpoints: List<Breakpoint>): Result<List<Breakpoint>> = Result.success(breakpoints)
        override suspend fun stackTrace(threadId: Long): Result<List<DebugStackFrame>> = Result.success(emptyList())
        override suspend fun scopes(frameId: Long): Result<List<Scope>> = Result.success(emptyList())
        override suspend fun variables(variablesReference: Long): Result<List<Variable>> = Result.success(emptyList())
        override suspend fun evaluate(frameId: Long, expression: String): Result<String> = Result.success("")
    }
}
