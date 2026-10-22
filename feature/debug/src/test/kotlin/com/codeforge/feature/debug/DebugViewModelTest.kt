package com.codeforge.feature.debug

import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.domain.model.DebugEvent
import com.codeforge.core.domain.model.DebugSessionState
import com.codeforge.core.domain.model.DebugStackFrame
import com.codeforge.core.domain.model.Scope
import com.codeforge.core.domain.model.Variable
import com.codeforge.core.domain.repository.BreakpointRepository
import com.codeforge.core.domain.repository.DebugRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebugViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeState = MutableStateFlow<DebugSessionState>(DebugSessionState.Idle)
    private val fakeEvents = MutableSharedFlow<DebugEvent>()
    private val fakeActiveBreakpoints = MutableStateFlow<Map<String, List<Breakpoint>>>(emptyMap())

    private var stepOverCalls = 0
    private var resumeCalls = 0
    private var stopCalls = 0
    private var evaluateCalls = 0

    private val fakeDebugRepository = object : DebugRepository {
        override val state: StateFlow<DebugSessionState> = fakeState
        override val events: Flow<DebugEvent> = fakeEvents
        override val activeBreakpoints: StateFlow<Map<String, List<Breakpoint>>> = fakeActiveBreakpoints

        override suspend fun start(config: DebugConfig): Result<Unit> {
            fakeState.value = DebugSessionState.Running
            return Result.success(Unit)
        }

        override suspend fun stop(): Result<Unit> {
            stopCalls++
            fakeState.value = DebugSessionState.Terminated(null)
            return Result.success(Unit)
        }

        override suspend fun setBreakpoints(path: String, breakpoints: List<Breakpoint>): Result<List<Breakpoint>> {
            return Result.success(breakpoints)
        }

        override suspend fun resume(): Result<Unit> {
            resumeCalls++
            fakeState.value = DebugSessionState.Running
            return Result.success(Unit)
        }

        override suspend fun pause(): Result<Unit> {
            fakeState.value = DebugSessionState.Paused(1L, "pause")
            return Result.success(Unit)
        }

        override suspend fun stepOver(threadId: Long): Result<Unit> {
            stepOverCalls++
            return Result.success(Unit)
        }

        override suspend fun stepInto(threadId: Long): Result<Unit> = Result.success(Unit)
        override suspend fun stepOut(threadId: Long): Result<Unit> = Result.success(Unit)

        override suspend fun stackTrace(threadId: Long): Result<List<DebugStackFrame>> {
            return Result.success(listOf(DebugStackFrame(101L, "main()", "/src/Main.kt", 15, 1)))
        }

        override suspend fun scopes(frameId: Long): Result<List<Scope>> {
            return Result.success(listOf(Scope("Locals", 201L)))
        }

        override suspend fun variables(variablesReference: Long): Result<List<Variable>> {
            return when (variablesReference) {
                201L -> Result.success(listOf(Variable(name = "user", value = "UserObject", variablesReference = 301L)))
                301L -> Result.success(listOf(Variable(name = "name", value = "\"Alice\"", variablesReference = 0L)))
                else -> Result.success(emptyList())
            }
        }

        override suspend fun evaluate(frameId: Long, expression: String): Result<String> {
            evaluateCalls++
            return if (expression == "valid") Result.success("42") else Result.failure(RuntimeException("Invalid expression"))
        }
    }

    private val fakeBreakpointRepository = object : BreakpointRepository {
        val breakpointsMap = MutableStateFlow<Map<String, List<Breakpoint>>>(emptyMap())
        override fun getBreakpoints(projectRoot: String): Flow<Map<String, List<Breakpoint>>> = breakpointsMap
        override suspend fun setBreakpoints(projectRoot: String, path: String, breakpoints: List<Breakpoint>) {}
        override suspend fun toggleBreakpoint(projectRoot: String, path: String, line: Int): Breakpoint? = null
        override suspend fun updateBreakpoint(projectRoot: String, breakpoint: Breakpoint) {}
        override suspend fun removeBreakpoint(projectRoot: String, breakpointId: String) {}
        override suspend fun removeAll(projectRoot: String) {}
        override suspend fun adjustBreakpointsOnEdit(projectRoot: String, path: String, changeLine: Int, lineDelta: Int) {}
    }

    private lateinit var viewModel: DebugViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DebugViewModel(fakeDebugRepository, fakeBreakpointRepository)
        viewModel.setProjectRoot("/test/project")
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun startTransitionsToRunning() = runTest {
        viewModel.handleIntent(DebugUiIntent.Start(DebugConfig(mainClass = "MainKt")))
        advanceUntilIdle()

        assertEquals(DebugSessionState.Running, viewModel.uiState.value.sessionState)
        assertTrue(viewModel.uiState.value.isRunning)
    }

    @Test
    fun pausedSessionLoadsFramesAndVariables() = runTest {
        val frames = listOf(DebugStackFrame(101L, "main()", "/src/Main.kt", 15, 1))
        fakeState.value = DebugSessionState.Paused(1L, "breakpoint", frames)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPaused)
        assertEquals(1, viewModel.uiState.value.frames.size)
        assertEquals(101L, viewModel.uiState.value.selectedFrameId)
        assertEquals(1, viewModel.uiState.value.scopes.size)
        assertNotNull(viewModel.uiState.value.variables[201L])
        assertEquals("user", viewModel.uiState.value.variables[201L]?.first()?.name)
    }

    @Test
    fun stepOverBlockedWhenNotPaused() = runTest {
        fakeState.value = DebugSessionState.Running
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canStep)
        viewModel.handleIntent(DebugUiIntent.StepOver)
        advanceUntilIdle()

        assertEquals(0, stepOverCalls)
    }

    @Test
    fun stepOverExecutedWhenPaused() = runTest {
        fakeState.value = DebugSessionState.Paused(1L, "breakpoint", listOf(DebugStackFrame(101L, "main()")))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.canStep)
        viewModel.handleIntent(DebugUiIntent.StepOver)
        advanceUntilIdle()

        assertEquals(1, stepOverCalls)
    }

    @Test
    fun toggleExpandVariableLoadsChildren() = runTest {
        fakeState.value = DebugSessionState.Paused(1L, "breakpoint", listOf(DebugStackFrame(101L, "main()")))
        advanceUntilIdle()

        val parentVar = Variable(name = "user", value = "UserObject", variablesReference = 301L)
        viewModel.handleIntent(DebugUiIntent.ToggleExpandVariable(parentVar))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.expandedVariables.contains(301L))
        val children = viewModel.uiState.value.variables[301L]
        assertNotNull(children)
        assertEquals("name", children?.first()?.name)
        assertEquals("\"Alice\"", children?.first()?.value)

        // Zweites Toggle klappt wieder zu
        viewModel.handleIntent(DebugUiIntent.ToggleExpandVariable(parentVar))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.expandedVariables.contains(301L))
    }

    @Test
    fun evaluateExpressionPopulatesResult() = runTest {
        fakeState.value = DebugSessionState.Paused(1L, "breakpoint", listOf(DebugStackFrame(101L, "main()")))
        advanceUntilIdle()

        viewModel.handleIntent(DebugUiIntent.SetEvaluationExpression("valid"))
        viewModel.handleIntent(DebugUiIntent.Evaluate)
        advanceUntilIdle()

        assertEquals(1, evaluateCalls)
        assertEquals("42", viewModel.uiState.value.evaluationResult)
    }

    @Test
    fun evaluateExpressionHandlesFailure() = runTest {
        fakeState.value = DebugSessionState.Paused(1L, "breakpoint", listOf(DebugStackFrame(101L, "main()")))
        advanceUntilIdle()

        viewModel.handleIntent(DebugUiIntent.SetEvaluationExpression("invalid"))
        viewModel.handleIntent(DebugUiIntent.Evaluate)
        advanceUntilIdle()

        assertEquals(1, evaluateCalls)
        assertTrue(viewModel.uiState.value.evaluationResult?.startsWith("Fehler:") == true)
    }

    @Test
    fun stopCallsRepositoryStop() = runTest {
        fakeState.value = DebugSessionState.Running
        advanceUntilIdle()

        viewModel.handleIntent(DebugUiIntent.Stop)
        advanceUntilIdle()

        assertEquals(1, stopCalls)
        assertEquals(DebugSessionState.Terminated(null), viewModel.uiState.value.sessionState)
    }
}
