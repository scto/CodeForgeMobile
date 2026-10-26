/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.domain.model.ProblemSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildTabTextTest {

    @Test
    fun `output text follows the search filter`() {
        val state = BuildUiState(outputLines = listOf("> Task :app:compile", "warning: x", "BUILD FAILED"), searchQuery = "task")
        assertEquals("> Task :app:compile", state.textFor(BuildTab.OUTPUT))
        assertEquals("> Task :app:compile\nwarning: x\nBUILD FAILED", state.copy(searchQuery = "").textFor(BuildTab.OUTPUT))
    }

    @Test
    fun `empty tabs give empty text so nothing is copied`() {
        val state = BuildUiState()
        BuildTab.entries.forEach { assertTrue(state.textFor(it).isBlank()) }
    }

    @Test
    fun `problems are written one per line with location`() {
        val state = BuildUiState(
            problems = listOf(
                BuildProblem(ProblemSeverity.ERROR, ProblemSource.KOTLIN, "app/Main.kt", 12, 3, "Unresolved reference: Foo", "compileDebugKotlin"),
                BuildProblem(ProblemSeverity.WARNING, ProblemSource.GRADLE, null, null, null, "Deprecated Gradle feature")
            )
        )
        assertEquals(
            "ERROR app/Main.kt:12:3: Unresolved reference: Foo [compileDebugKotlin]\nWARNING Deprecated Gradle feature",
            state.textFor(BuildTab.PROBLEMS)
        )
        val errorsOnly = state.copy(problemFilter = ProblemFilter.ERRORS_ONLY).textFor(BuildTab.PROBLEMS)
        assertFalse(errorsOnly.contains("WARNING"))
    }

    @Test
    fun `file names carry the tab and a timestamp`() {
        assertEquals("build-output-20261026-145300.txt", suggestedFileName(BuildTab.OUTPUT, "20261026-145300"))
        assertEquals("logcat-1.txt", suggestedFileName(BuildTab.LOGCAT, "1"))
        assertEquals("test-results-1.txt", suggestedFileName(BuildTab.TESTS, "1"))
        assertEquals("build-problems-1.txt", suggestedFileName(BuildTab.PROBLEMS, "1"))
    }

    @Test
    fun `long text keeps the end and starts at a line boundary`() {
        val text = (1..1000).joinToString("\n") { "line $it" }
        val trimmed = text.keepTail(max = 200)
        assertTrue(trimmed.truncated)
        assertTrue(trimmed.text.length <= 200)
        assertTrue(trimmed.text.endsWith("line 1000"))
        assertTrue(trimmed.text.startsWith("line "))
        assertEquals(TrimResult("short", false), "short".keepTail(max = 200))
    }
}
