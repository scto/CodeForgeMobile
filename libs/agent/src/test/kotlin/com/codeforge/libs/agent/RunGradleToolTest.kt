package com.codeforge.libs.agent

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RunGradleToolTest {
    private fun call(runner: AgentBuildRunner, task: String) =
        runBlocking { RunGradleTool(runner).execute(buildJsonObject { put("task", task) }) }

    @Test
    fun `Erfolg wird gemeldet und Task bereinigt`() {
        var seen = ""
        val r = call({ seen = it; BuildOutcome(true) }, "  :app:compileDebugKotlin   --stacktrace ")
        assertEquals(":app:compileDebugKotlin --stacktrace", seen)
        assertEquals("BUILD SUCCESSFUL: :app:compileDebugKotlin --stacktrace", r.content)
        assertFalse(r.isError)
    }

    @Test
    fun `Fehler stehen zuerst, Ausgabeende nur ohne strukturierte Fehler`() {
        val problems = listOf(
            AgentDiagnostic("A.kt", 3, DiagnosticSeverity.WARNING, "w"),
            AgentDiagnostic("B.kt", 7, DiagnosticSeverity.ERROR, "Unresolved reference: x"),
        )
        val r = call({ BuildOutcome(false, problems, listOf("tail1")) }, "test")
        assertTrue(r.isError)
        val lines = r.content.lines()
        assertEquals("B.kt:7: error: Unresolved reference: x", lines[1])
        assertEquals("A.kt:3: warning: w", lines[2])
        assertFalse(r.content.contains("tail1"))

        val noStructured = call({ BuildOutcome(false, emptyList(), listOf("FAILURE: Build failed", "> Task :x FAILED")) }, "test")
        assertTrue(noStructured.content.contains("> Task :x FAILED"))
    }

    @Test
    fun `viele Fehler werden gekuerzt`() {
        val many = List(45) { AgentDiagnostic("F$it.kt", 1, DiagnosticSeverity.ERROR, "e") }
        val r = call({ BuildOutcome(false, many) }, "x")
        assertTrue(r.content.endsWith("[15 weitere]"))
    }

    @Test
    fun `nicht ausfuehrbarer Build ist ein Fehler mit Grund`() {
        val r = call({ BuildOutcome(false, failureMessage = "gradlew fehlt") }, "x")
        assertTrue(r.isError)
        assertEquals("Build nicht ausgeführt: gradlew fehlt", r.content)
    }

    @Test
    fun `Shell-Zeichen und leere Tasks werden abgelehnt`() {
        for (bad in listOf("", "  ", "a; rm -rf /", "a && b", "\$(x)", "a|b", "a`b`", "x".repeat(201), "a\nb")) {
            try { call({ error("darf nicht laufen") }, bad); fail("erwartet: '$bad'") } catch (_: ToolArgumentException) {}
        }
        GradleTaskSupport.validate(":core:domain:test --tests *Foo*".replace("*", "x"))
    }

    @Test
    fun `Tool ist nur mit Runner registriert und gilt als Ausfuehrung`() {
        val without = BuiltinTools.create(FakeWorkspace())
        assertNull(without.firstOrNull { it.name == "run_gradle" })
        val with = BuiltinTools.create(FakeWorkspace(), buildRunner = { BuildOutcome(true) })
        assertEquals(ToolKind.EXECUTE, with.single { it.name == "run_gradle" }.kind)
    }
}
