/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.BuildSession
import com.codeforge.core.domain.model.BuildStatus
import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.domain.model.ProblemSource
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.navigation.JumpTarget
import com.codeforge.core.navigation.OpenFileRequestBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BuildViewModelTest {

    private lateinit var fakeRepository: FakeGradleBuildRepository
    private lateinit var fakeBridge: FakeOpenFileRequestBridge
    private lateinit var viewModel: BuildViewModel

    @Before
    fun setUp() {
        fakeRepository = FakeGradleBuildRepository()
        fakeBridge = FakeOpenFileRequestBridge()
        viewModel = BuildViewModel(
            fakeRepository,
            fakeBridge,
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default)
        )
    }

    @Test
    fun startBuildStreamsProblemsAndFinishes() = runBlocking {
        val problem = BuildProblem(
            severity = ProblemSeverity.ERROR,
            source = ProblemSource.KOTLIN,
            path = "/workspace/App.kt",
            line = 42,
            column = 5,
            message = "Unresolved reference"
        )

        fakeRepository.taskFlow = flowOf(
            BuildEvent.Output("> Task :app:compileDebugKotlin"),
            BuildEvent.Problem(problem),
            BuildEvent.BuildFinished(success = false)
        )

        viewModel.handleIntent(BuildUiIntent.StartBuild("assembleDebug"))
        delay(300)

        val state = viewModel.uiState.value
        assertEquals(1, state.problems.size)
        assertEquals(problem, state.problems[0])
        assertEquals(1, state.errorCount)
        assertFalse(state.isBuilding)
    }

    @Test
    fun cancelBuildInvokesRepository() = runBlocking {
        viewModel.handleIntent(BuildUiIntent.CancelBuild)
        delay(100)

        assertTrue(fakeRepository.cancelCalled)
        assertFalse(viewModel.uiState.value.isBuilding)
    }

    @Test
    fun rejectsConcurrentBuildWhenAlreadyRunning() = runBlocking {
        fakeRepository.setSession(
            BuildSession(
                taskName = "assembleDebug",
                startTimeMillis = System.currentTimeMillis(),
                status = BuildStatus.RUNNING
            )
        )
        delay(100)
        assertTrue(viewModel.uiState.value.isBuilding)

        // Try second build
        viewModel.handleIntent(BuildUiIntent.StartBuild("clean"))
        delay(100)

        // selectedTask must still be assembleDebug
        assertEquals("assembleDebug", viewModel.uiState.value.selectedTask)
    }

    @Test
    fun clickProblemNavigatesViaBridge() = runBlocking {
        val problem = BuildProblem(
            severity = ProblemSeverity.ERROR,
            source = ProblemSource.KOTLIN,
            path = "/workspace/MainActivity.kt",
            line = 10,
            column = 15,
            message = "Syntax error"
        )

        viewModel.handleIntent(BuildUiIntent.ClickProblem(problem))
        delay(100)

        assertEquals(1, fakeBridge.jumpTargets.size)
        val target = fakeBridge.jumpTargets[0]
        assertEquals("/workspace/MainActivity.kt", target.path)
        assertEquals(10, target.line)
        assertEquals(15, target.column)
    }

    private class FakeGradleBuildRepository : GradleBuildRepository {
        private val _currentSession = MutableStateFlow<BuildSession?>(null)
        override val currentSession: StateFlow<BuildSession?> = _currentSession.asStateFlow()

        var taskFlow: Flow<BuildEvent> = flowOf()
        var cancelCalled = false

        override suspend fun connect(projectRootPath: String): Result<Unit> = Result.success(Unit)
        override fun runTask(taskName: String): Flow<BuildEvent> = taskFlow
        override suspend fun cancelBuild() {
            cancelCalled = true
        }
        override suspend fun disconnect() {}

        fun setSession(session: BuildSession?) {
            _currentSession.value = session
        }
    }

    private class FakeOpenFileRequestBridge : OpenFileRequestBridge {
        val requestedPaths = mutableListOf<String>()
        val jumpTargets = mutableListOf<JumpTarget>()

        override val openRequests: SharedFlow<String> = MutableSharedFlow()
        override val jumpRequests: SharedFlow<JumpTarget> = MutableSharedFlow()

        override suspend fun requestOpen(path: String) {
            requestedPaths.add(path)
        }

        override suspend fun requestOpenAt(path: String, line: Int, column: Int, length: Int) {
            jumpTargets.add(JumpTarget(path, line, column, length))
        }
    }
}
