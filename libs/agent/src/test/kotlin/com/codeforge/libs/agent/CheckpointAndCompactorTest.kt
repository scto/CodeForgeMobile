package com.codeforge.libs.agent

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckpointWorkspaceTest {
    @Test
    fun `revert stellt den Ausgangszustand her`() = runTest {
        val base = FakeWorkspace(mapOf("a.kt" to "v1", "b.kt" to "keep", "dir/c.kt" to "c", "dir/d.kt" to "d"))
        val ws = CheckpointWorkspace(base)
        val turn = ws.beginTurn()

        ws.writeFile("a.kt", "v2")
        ws.writeFile("a.kt", "v3") // erster Stand zählt
        ws.writeFile("neu.kt", "n")
        ws.deletePath("dir")

        assertEquals(setOf("a.kt", "neu.kt", "dir/c.kt", "dir/d.kt"), ws.changes(turn).map { it.path }.toSet())
        assertEquals(FileChange("a.kt", "v1", "v3"), ws.changes(turn).first { it.path == "a.kt" })

        val result = ws.revert(turn)
        assertTrue(result.failed.isEmpty())
        assertEquals(mapOf("a.kt" to "v1", "b.kt" to "keep", "dir/c.kt" to "c", "dir/d.kt" to "d"), base.files)
        assertTrue(ws.turnIds().isEmpty())
    }

    @Test
    fun `unveraenderte Dateien erscheinen nicht in changes`() = runTest {
        val base = FakeWorkspace(mapOf("a.kt" to "x"))
        val ws = CheckpointWorkspace(base)
        val t = ws.beginTurn()
        ws.writeFile("a.kt", "y")
        ws.writeFile("a.kt", "x")
        assertTrue(ws.changes(t).isEmpty())
    }

    @Test
    fun `Zuege sind getrennt, ohne beginTurn wird nichts aufgezeichnet`() = runTest {
        val base = FakeWorkspace(mapOf("a" to "0"))
        val ws = CheckpointWorkspace(base)
        ws.writeFile("a", "1") // kein Zug
        val t1 = ws.beginTurn(); ws.writeFile("a", "2")
        val t2 = ws.beginTurn(); ws.writeFile("a", "3")
        assertEquals("2", ws.changes(t2).single().before)
        assertEquals("1", ws.changes(t1).single().before)
        ws.revert(t2)
        assertEquals("2", base.files["a"])
    }

    @Test
    fun `Lesezugriffe laufen unveraendert durch`() = runTest {
        val ws = CheckpointWorkspace(FakeWorkspace(mapOf("a" to "x")))
        assertEquals("x", ws.readFile("a"))
        assertNull(ws.readFile("b"))
    }
}

class HistoryCompactorTest {
    private fun result(id: String, size: Int) = ContentPart.ToolResultPart(id, "x".repeat(size))
    private fun history(vararg sizes: Int): List<AgentMessage> = listOf(AgentMessage(AgentRole.USER, listOf(ContentPart.Text("frage")))) +
        sizes.mapIndexed { i, s -> AgentMessage(AgentRole.USER, listOf(result("t$i", s))) }

    private fun contents(m: List<AgentMessage>) = m.flatMap { it.parts }.filterIsInstance<ContentPart.ToolResultPart>().map { it.content }

    @Test
    fun `unterhalb der Schwelle bleibt alles identisch`() {
        val h = history(100, 100, 100)
        assertSame(h, HistoryCompactor(triggerChars = 1000, targetChars = 500).compact(h))
    }

    @Test
    fun `kuerzt die aeltesten Ergebnisse bis zum Ziel und schont die juengsten`() {
        val c = HistoryCompactor(triggerChars = 1000, targetChars = 500, keepRecentResults = 2)
        val h = history(400, 400, 400, 400, 400)
        val out = contents(c.compact(h))
        assertTrue(out[0].startsWith("[Ergebnis gekürzt"))
        assertTrue(out[1].startsWith("[Ergebnis gekürzt"))
        assertEquals(400, out[3].length)
        assertEquals(400, out[4].length)
        assertEquals(400, contents(h)[0].length) // Eingabe unverändert
    }

    @Test
    fun `gekuerzte Ergebnisse bleiben gekuerzt (stabiles Praefix)`() {
        val c = HistoryCompactor(triggerChars = 1000, targetChars = 500, keepRecentResults = 1)
        val first = contents(c.compact(history(400, 400, 400, 400)))
        // weniger Gesamtgröße als der Trigger – die frühere Kürzung bleibt trotzdem
        val again = contents(c.compact(history(400, 400, 400, 400)))
        assertEquals(first, again)
        assertTrue(again[0].startsWith("[Ergebnis gekürzt"))
    }
}
