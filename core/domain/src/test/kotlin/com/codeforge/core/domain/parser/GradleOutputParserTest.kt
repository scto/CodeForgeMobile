/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.domain.model.ProblemSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GradleOutputParserTest {

    private val parser = GradleOutputParser()

    private fun readFixture(fileName: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream("fixtures/$fileName")
            ?: error("Fixture fixtures/$fileName not found")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun parseKotlinErrorsAndWarnings() {
        val content = readFixture("kotlin_error.log")
        val problems = parser.parse(content)

        assertEquals(3, problems.size)

        val prob1 = problems[0]
        assertEquals(ProblemSeverity.ERROR, prob1.severity)
        assertEquals(ProblemSource.KOTLIN, prob1.source)
        assertEquals("/workspace/app/src/main/kotlin/com/example/MainActivity.kt", prob1.path)
        assertEquals(14, prob1.line)
        assertEquals(23, prob1.column)
        assertEquals("Unresolved reference: calculateTotal", prob1.message)
        assertEquals(":app:compileDebugKotlin", prob1.taskName)

        val prob2 = problems[1]
        assertEquals(ProblemSeverity.WARNING, prob2.severity)
        assertEquals(ProblemSource.KOTLIN, prob2.source)
        assertEquals("/workspace/app/src/main/kotlin/com/example/MainActivity.kt", prob2.path)
        assertEquals(28, prob2.line)
        assertEquals(9, prob2.column)
        assertEquals("Parameter 'flag' is never used", prob2.message)

        val prob3 = problems[2]
        assertEquals(ProblemSeverity.ERROR, prob3.severity)
        assertEquals(ProblemSource.KOTLIN, prob3.source)
        assertEquals("/workspace/core/domain/src/main/kotlin/com/example/DomainModel.kt", prob3.path)
        assertEquals(42, prob3.line)
        assertEquals(10, prob3.column)
        assertTrue(prob3.message.contains("Type mismatch"))
    }

    @Test
    fun parseJavacMultilineErrors() {
        val content = readFixture("javac_multiline.log")
        val problems = parser.parse(content)

        assertEquals(2, problems.size)

        val err = problems[0]
        assertEquals(ProblemSeverity.ERROR, err.severity)
        assertEquals(ProblemSource.JAVA, err.source)
        assertEquals("/workspace/app/src/main/java/com/example/LegacyUtil.java", err.path)
        assertEquals(18, err.line)
        assertTrue(err.message.contains("cannot find symbol"))
        assertTrue(err.message.contains("class MissingClass"))
        assertTrue(err.message.contains("package com.example.model"))
        assertTrue(err.message.contains("^"))

        val warn = problems[1]
        assertEquals(ProblemSeverity.WARNING, warn.severity)
        assertEquals(ProblemSource.JAVA, warn.source)
        assertEquals("/workspace/app/src/main/java/com/example/LegacyUtil.java", warn.path)
        assertEquals(35, warn.line)
        assertTrue(warn.message.contains("[deprecation] oldMethod() in OldClass has been deprecated"))
    }

    @Test
    fun parseAaptErrors() {
        val content = readFixture("aapt_error.log")
        val problems = parser.parse(content)

        assertEquals(2, problems.size)

        val p1 = problems[0]
        assertEquals(ProblemSeverity.ERROR, p1.severity)
        assertEquals(ProblemSource.AAPT, p1.source)
        assertEquals("/workspace/app/src/main/res/layout/activity_main.xml", p1.path)
        assertEquals(12, p1.line)
        assertTrue(p1.message.contains("resource color/missing_accent"))

        val p2 = problems[1]
        assertEquals(ProblemSeverity.ERROR, p2.severity)
        assertEquals(ProblemSource.AAPT, p2.source)
        assertEquals("/workspace/app/src/main/res/values/strings.xml", p2.path)
        assertEquals(24, p2.line)
        assertTrue(p2.message.contains("string 'app_title' has already been defined"))
    }

    @Test
    fun parseGradleFailureSection() {
        val content = readFixture("gradle_failure.log")
        val problems = parser.parse(content)

        assertEquals(1, problems.size)
        val p = problems[0]
        assertEquals(ProblemSeverity.ERROR, p.severity)
        assertEquals(ProblemSource.GRADLE, p.source)
        assertEquals(":app:compileDebugKotlin", p.taskName)
        assertTrue(p.message.contains("Execution failed for task ':app:compileDebugKotlin'"))
        assertTrue(p.message.contains("Compilation error. See log for more details"))
    }

    @Test
    fun parseAnsiStyledOutput() {
        val content = readFixture("ansi_styled.log")
        val problems = parser.parse(content)

        assertEquals(2, problems.size)
        assertEquals("Unresolved reference: calculateTotal", problems[0].message)
        assertEquals(ProblemSeverity.ERROR, problems[0].severity)
        assertEquals("Parameter 'flag' is never used", problems[1].message)
        assertEquals(ProblemSeverity.WARNING, problems[1].severity)
    }

    @Test
    fun streamingSessionMatchesBatchParse() {
        val content = readFixture("javac_multiline.log")
        val session = parser.createSession()
        val streamingProblems = mutableListOf<com.codeforge.core.domain.model.BuildProblem>()

        content.lineSequence().forEach { line ->
            streamingProblems.addAll(session.feedLine(line))
        }
        streamingProblems.addAll(session.finish())

        val batchProblems = parser.parse(content)
        assertEquals(batchProblems, streamingProblems)
    }
}
