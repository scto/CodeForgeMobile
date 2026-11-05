package com.codeforge.libs.agent

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentLoopTest {
    private fun loop(
        llm: LlmClient,
        ws: AgentWorkspace,
        mode: PermissionMode = PermissionMode.AUTO_ACCEPT,
        approver: ToolApprover = ToolApprover { true },
        maxSteps: Int = 25,
    ) = AgentLoop(llm, AgentToolRegistry(BuiltinTools.create(ws)), AgentPermissionPolicy(mode, approver), "sys", maxSteps = maxSteps)

    @Test
    fun `Werkzeugaufruf, Ergebnis und Schlussantwort`() = runTest {
        val ws = FakeWorkspace(mapOf("a.txt" to "hallo"))
        val llm = ScriptedLlm(
            listOf(
                listOf(LlmStreamEvent.TextDelta("Ich lese. "), toolCall("t1", "read_file", "path" to "a.txt"), toolUse),
                listOf(LlmStreamEvent.TextDelta("Fertig."), endTurn),
            ),
        )
        val events = loop(llm, ws).run(emptyList(), "lies a.txt").toList()

        val finished = events.last() as AgentEvent.Finished
        assertEquals(FinishReason.END_TURN, finished.reason)
        assertEquals(listOf("USER", "ASSISTANT", "USER", "ASSISTANT"), finished.messages.map { it.role.name })
        val result = finished.messages[2].parts.single() as ContentPart.ToolResultPart
        assertEquals("t1", result.toolUseId)
        assertEquals("hallo", result.content)
        assertEquals("Fertig.", finished.messages.last().text)
        assertTrue(events.any { it is AgentEvent.ToolCallStarted && it.name == "read_file" })
        // zweiter Modellaufruf sieht das Ergebnis
        assertEquals(3, llm.requests[1].messages.size)
    }

    @Test
    fun `Plan-Modus lehnt Schreiben ab und meldet es dem Modell`() = runTest {
        val ws = FakeWorkspace()
        val llm = ScriptedLlm(
            listOf(listOf(toolCall("t1", "create_file", "path" to "x.kt", "content" to "x"), toolUse), listOf(endTurn)),
        )
        val events = loop(llm, ws, PermissionMode.PLAN_ONLY).run(emptyList(), "schreibe").toList()
        val fin = events.filterIsInstance<AgentEvent.ToolCallFinished>().single()
        assertTrue(fin.denied)
        assertTrue(fin.result.isError)
        assertTrue(ws.files.isEmpty())
    }

    @Test
    fun `Rueckfrage im ASK_EACH-Modus entscheidet`() = runTest {
        val asked = mutableListOf<ApprovalRequest>()
        for (allow in listOf(true, false)) {
            val ws = FakeWorkspace()
            val llm = ScriptedLlm(
                listOf(listOf(toolCall("t1", "create_file", "path" to "n.txt", "content" to "x"), toolUse), listOf(endTurn)),
            )
            loop(llm, ws, PermissionMode.ASK_EACH, { asked += it; allow }).run(emptyList(), "go").toList()
            assertEquals(allow, ws.files.containsKey("n.txt"))
        }
        assertEquals("Datei anlegen: n.txt", asked.first().summary)
        assertEquals(ToolKind.WRITE, asked.first().kind)
    }

    @Test
    fun `Lesen braucht keine Freigabe`() = runTest {
        val ws = FakeWorkspace(mapOf("a" to "1"))
        val llm = ScriptedLlm(listOf(listOf(toolCall("t1", "read_file", "path" to "a"), toolUse), listOf(endTurn)))
        val events = loop(llm, ws, PermissionMode.ASK_EACH, { error("darf nicht gefragt werden") }).run(emptyList(), "x").toList()
        assertFalse(events.filterIsInstance<AgentEvent.ToolCallFinished>().single().result.isError)
    }

    @Test
    fun `unbekanntes Werkzeug und fehlerhafte Argumente werden als Fehlerergebnis gemeldet`() = runTest {
        val llm = ScriptedLlm(
            listOf(
                listOf(toolCall("t1", "gibt_es_nicht"), toolCall("t2", "read_file", "path" to "../geheim"), toolUse),
                listOf(endTurn),
            ),
        )
        val events = loop(llm, FakeWorkspace()).run(emptyList(), "x").toList()
        val finished = events.filterIsInstance<AgentEvent.ToolCallFinished>()
        assertEquals(2, finished.size)
        assertTrue(finished.all { it.result.isError })
        assertTrue(finished[0].result.content.contains("Unbekanntes Werkzeug"))
        assertTrue(finished[1].result.content.contains("verlässt das Projekt"))
        assertTrue(events.last() is AgentEvent.Finished)
    }

    @Test
    fun `Schrittgrenze beendet die Schleife`() = runTest {
        val llm = ScriptedLlm(listOf(listOf(toolCall("t", "list_dir"), toolUse)))
        val events = loop(llm, FakeWorkspace(mapOf("a" to "1")), maxSteps = 3).run(emptyList(), "x").toList()
        assertEquals(FinishReason.MAX_STEPS, (events.last() as AgentEvent.Finished).reason)
        assertEquals(3, llm.requests.size)
    }

    @Test
    fun `Modellfehler endet mit Failed und liefert den Verlauf`() = runTest {
        val llm = ScriptedLlm(listOf(listOf(LlmStreamEvent.Failed("HTTP 500"))))
        val last = loop(llm, FakeWorkspace()).run(emptyList(), "x").toList().last() as AgentEvent.Failed
        assertEquals("HTTP 500", last.message)
        assertEquals(1, last.messages.size)
    }

    @Test
    fun `abgeschnittene Antwort wird als TRUNCATED gemeldet`() = runTest {
        val llm = ScriptedLlm(listOf(listOf(LlmStreamEvent.TextDelta("halb"), LlmStreamEvent.Completed(StopReason.MAX_TOKENS))))
        val fin = loop(llm, FakeWorkspace()).run(emptyList(), "x").toList().last() as AgentEvent.Finished
        assertEquals(FinishReason.TRUNCATED, fin.reason)
    }

    @Test
    fun `bisheriger Verlauf wird fortgesetzt`() = runTest {
        val first = AgentMessage(AgentRole.USER, listOf(ContentPart.Text("frueher")))
        val llm = ScriptedLlm(listOf(listOf(endTurn)))
        loop(llm, FakeWorkspace()).run(listOf(first), "jetzt").toList()
        assertEquals(listOf("frueher", "jetzt"), llm.requests.single().messages.map { it.text })
        assertEquals(BuiltinTools.create(FakeWorkspace()).map { it.name }.toSet(), llm.requests.single().tools.map { it.name }.toSet())
    }
}
