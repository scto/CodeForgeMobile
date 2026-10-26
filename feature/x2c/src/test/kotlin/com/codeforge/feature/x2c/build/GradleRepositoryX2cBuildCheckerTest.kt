package com.codeforge.feature.x2c.build

import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.BuildSession
import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.domain.model.ProblemSource
import com.codeforge.core.domain.model.X2cBuildStepStatus
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GradleRepositoryX2cBuildCheckerTest {

    private class FakeGradle(
        private val connectResult: Result<Unit> = Result.success(Unit),
        private val events: Flow<BuildEvent> = flowOf()
    ) : GradleBuildRepository {
        private val _session = MutableStateFlow<BuildSession?>(null)
        override val currentSession: StateFlow<BuildSession?> = _session.asStateFlow()
        var connectedTo: String? = null
        var lastTask: String? = null
        var cancelCalls = 0

        override suspend fun connect(projectRootPath: String): Result<Unit> {
            connectedTo = projectRootPath
            return connectResult
        }

        override fun runTask(taskName: String): Flow<BuildEvent> {
            lastTask = taskName
            return events
        }

        override suspend fun cancelBuild() {
            cancelCalls++
        }

        override suspend fun disconnect() {}
    }

    private fun problem(severity: ProblemSeverity, message: String) =
        BuildProblem(severity, ProblemSource.KOTLIN, "app/src/Main.kt", 12, 3, message, "compileDebugKotlin")

    @Test
    fun `joins tasks into one gradle call and maps a successful build`() = runTest {
        val gradle = FakeGradle(
            events = flowOf(
                BuildEvent.Output("> Task :app:compileDebugKotlin"),
                BuildEvent.Problem(problem(ProblemSeverity.WARNING, "unused")),
                BuildEvent.BuildFinished(success = true)
            )
        )
        val lines = mutableListOf<String>()
        val result = GradleRepositoryX2cBuildChecker(gradle)
            .runTasks("/project", listOf(":app:compileDebugKotlin", ":app:assembleDebug"), onOutput = { lines += it })

        assertEquals("/project", gradle.connectedTo)
        assertEquals(":app:compileDebugKotlin :app:assembleDebug", gradle.lastTask)
        assertEquals(X2cBuildStepStatus.SUCCESS, result.status)
        assertEquals(listOf("> Task :app:compileDebugKotlin"), lines)
        assertEquals("warning", result.problems.single().severity)
    }

    @Test
    fun `failed build keeps errors with file and line, info is dropped`() = runTest {
        val gradle = FakeGradle(
            events = flowOf(
                BuildEvent.Problem(problem(ProblemSeverity.ERROR, "Unresolved reference: Foo")),
                BuildEvent.Problem(problem(ProblemSeverity.INFO, "note")),
                BuildEvent.BuildFinished(success = false)
            )
        )
        val result = GradleRepositoryX2cBuildChecker(gradle).runTasks("/p", listOf("assembleDebug"))

        assertEquals(X2cBuildStepStatus.FAILED, result.status)
        val error = result.problems.single()
        assertEquals("error", error.severity)
        assertEquals("app/src/Main.kt", error.path)
        assertEquals(12, error.line)
        assertEquals("compileDebugKotlin", error.task)
    }

    @Test
    fun `build failure message and missing finish event both count as failed`() = runTest {
        val failed = GradleRepositoryX2cBuildChecker(FakeGradle(events = flowOf(BuildEvent.BuildFailed("gradlew fehlt"))))
            .runTasks("/p", listOf("assembleDebug"))
        assertEquals(X2cBuildStepStatus.FAILED, failed.status)
        assertEquals("gradlew fehlt", failed.message)

        val silent = GradleRepositoryX2cBuildChecker(FakeGradle(events = flowOf(BuildEvent.Output("x"))))
            .runTasks("/p", listOf("assembleDebug"))
        assertEquals(X2cBuildStepStatus.FAILED, silent.status)
    }

    @Test
    fun `connect failure fails without starting a task`() = runTest {
        val gradle = FakeGradle(connectResult = Result.failure(IllegalStateException("kein Projekt")))
        val result = GradleRepositoryX2cBuildChecker(gradle).runTasks("/p", listOf("assembleDebug"))

        assertEquals(X2cBuildStepStatus.FAILED, result.status)
        assertTrue(result.message!!.contains("kein Projekt"))
        assertEquals(null, gradle.lastTask)
    }

    @Test
    fun `empty task list is skipped`() = runTest {
        val gradle = FakeGradle()
        val result = GradleRepositoryX2cBuildChecker(gradle).runTasks("/p", emptyList())
        assertEquals(X2cBuildStepStatus.SKIPPED, result.status)
        assertEquals(null, gradle.connectedTo)
    }

    @Test
    fun `secrets in output and messages are scrubbed`() = runTest {
        val gradle = FakeGradle(
            events = flowOf(
                BuildEvent.Output("Authorization: Bearer abc123secret"),
                BuildEvent.Problem(problem(ProblemSeverity.ERROR, "Authorization: Bearer abc123secret")),
                BuildEvent.BuildFinished(success = false)
            )
        )
        val seen = mutableListOf<String>()
        val result = GradleRepositoryX2cBuildChecker(gradle).runTasks("/p", listOf("t"), onOutput = { seen += it })

        assertTrue(seen.none { it.contains("abc123secret") })
        assertTrue(!result.outputTail.contains("abc123secret"))
        assertTrue(result.problems.none { it.message.contains("abc123secret") })
    }

    @Test
    fun `caller cancellation stops the gradle build`() = runTest {
        val gradle = FakeGradle(
            events = flow {
                emit(BuildEvent.Output("läuft"))
                // Wie ein laufender Build: endet erst, wenn der Prozess beendet wird.
                awaitCancellation()
            }
        )
        var cancelled = false
        // Der Beobachter fragt alle 500 ms ab; virtuelle Zeit genügt.
        val job = launch {
            GradleRepositoryX2cBuildChecker(gradle).runTasks("/p", listOf("t"), isCancelled = { cancelled })
        }
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        cancelled = true
        testScheduler.advanceTimeBy(600)
        testScheduler.runCurrent()
        assertEquals(1, gradle.cancelCalls)
        job.cancel()
    }
}
