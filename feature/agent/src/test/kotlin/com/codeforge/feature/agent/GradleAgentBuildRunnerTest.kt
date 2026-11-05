package com.codeforge.feature.agent

import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.BuildSession
import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.domain.model.ProblemSource
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.libs.agent.DiagnosticSeverity
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fake mit vorbereiteten Ereignissen; `hang` lässt den Lauf nie enden. */
class FakeGradle(var events: List<BuildEvent> = emptyList(), var hang: Boolean = false, var connectError: Throwable? = null) : GradleBuildRepository {
    var cancelled = 0
    var lastTask: String? = null
    override val currentSession: StateFlow<BuildSession?> = MutableStateFlow(null)
    override suspend fun connect(projectRootPath: String): Result<Unit> =
        connectError?.let { Result.failure(it) } ?: Result.success(Unit)
    override fun runTask(taskName: String): Flow<BuildEvent> {
        lastTask = taskName
        return if (hang) flow { kotlinx.coroutines.awaitCancellation() } else flowOf(*events.toTypedArray())
    }
    override suspend fun cancelBuild() { cancelled++ }
    override suspend fun disconnect() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class GradleAgentBuildRunnerTest {
    private val root = File("/p")

    private fun problem(sev: ProblemSeverity, path: String?, line: Int?, msg: String) =
        BuildEvent.Problem(BuildProblem(sev, ProblemSource.KOTLIN, path, line, null, msg))

    @Test
    fun `sammelt Fehler mit relativen Pfaden, ignoriert Info und meldet Misserfolg`() = runBlocking {
        val gradle = FakeGradle(
            listOf(
                BuildEvent.Output("> Task :app:compileDebugKotlin"),
                problem(ProblemSeverity.ERROR, "/p/app/A.kt", 12, "Unresolved reference"),
                problem(ProblemSeverity.WARNING, "/fremd/B.kt", null, "w"),
                problem(ProblemSeverity.INFO, null, null, "i"),
                BuildEvent.BuildFinished(false),
            ),
        )
        val out = GradleAgentBuildRunner(gradle, root).run(":app:compileDebugKotlin")
        assertFalse(out.success)
        assertEquals(2, out.problems.size)
        assertEquals("app/A.kt", out.problems[0].path)
        assertEquals(12, out.problems[0].line)
        assertEquals(DiagnosticSeverity.ERROR, out.problems[0].severity)
        assertEquals("/fremd/B.kt", out.problems[1].path)
        assertEquals(listOf("> Task :app:compileDebugKotlin"), out.outputTail)
    }

    @Test
    fun `Erfolg, BuildFailed und Verbindungsfehler`() = runBlocking {
        assertTrue(GradleAgentBuildRunner(FakeGradle(listOf(BuildEvent.BuildFinished(true))), root).run("x").success)
        val failed = GradleAgentBuildRunner(FakeGradle(listOf(BuildEvent.BuildFailed("kein gradlew"))), root).run("x")
        assertFalse(failed.success)
        assertEquals("kein gradlew", failed.failureMessage)
        val conn = GradleAgentBuildRunner(FakeGradle(connectError = IllegalStateException("weg")), root).run("x")
        assertEquals("weg", conn.failureMessage)
    }

    @Test
    fun `Zeitueberschreitung bricht den Build ab`() = runBlocking {
        val gradle = FakeGradle(hang = true)
        val out = GradleAgentBuildRunner(gradle, root, timeoutMillis = 50).run("x")
        assertFalse(out.success)
        assertTrue(out.failureMessage!!.startsWith("Zeitüberschreitung"))
        assertEquals(1, gradle.cancelled)
    }
}
