package com.codeforge.libs.agent

import java.io.File
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentHistoryStoreTest {
    private val dir = kotlin.io.path.createTempDirectory("hist").toFile()
    private val store = AgentHistoryStore(dir)

    @After
    fun tearDown() { dir.deleteRecursively() }

    private val sample = listOf(
        AgentMessage(AgentRole.USER, listOf(ContentPart.Text("lies a.kt\nmit \"Anführungszeichen\""))),
        AgentMessage(AgentRole.ASSISTANT, listOf(ContentPart.Text("ok"), ContentPart.ToolUse("t1", "read_file", buildJsonObject { put("path", "a.kt") }))),
        AgentMessage(AgentRole.USER, listOf(ContentPart.ToolResultPart("t1", "inhalt", isError = true))),
    )

    @Test
    fun `Codec ist verlustfrei`() {
        assertEquals(sample, AgentHistoryCodec.decode(AgentHistoryCodec.encode(sample)))
    }

    @Test
    fun `Speichern, Laden getrennt je Projekt, Loeschen`() {
        store.save("/p/a", sample)
        assertEquals(sample, store.load("/p/a"))
        assertTrue(store.load("/p/b").isEmpty())
        store.clear("/p/a")
        assertTrue(store.load("/p/a").isEmpty())
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(".tmp") })
    }

    @Test
    fun `kaputte oder fremde Dateien ergeben leeren Verlauf`() {
        store.save("/p", sample)
        dir.listFiles()!!.single().writeText("{kaputt")
        assertTrue(store.load("/p").isEmpty())
        assertNull(AgentHistoryCodec.decode("""{"version":99,"messages":[]}"""))
    }

    @Test
    fun `alte Werkzeugergebnisse werden beim Speichern gekuerzt`() {
        val big = List(40) {
            AgentMessage(AgentRole.USER, listOf(ContentPart.ToolResultPart("t$it", "x".repeat(10_000))))
        }
        store.save("/p", big)
        assertTrue(dir.listFiles()!!.single().length() < 300_000)
        assertEquals(40, store.load("/p").size)
    }
}
