package com.codeforge.feature.agent

import com.codeforge.core.domain.agent.AgentAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentAttachmentTest {
    @Test
    fun `Label zeigt Zeilenbereich`() {
        assertEquals("Foo.kt", AgentAttachment("/p/Foo.kt").label)
        assertEquals("Foo.kt:3", AgentAttachment("/p/Foo.kt", 3, 3, "x").label)
        assertEquals("Foo.kt:3-9", AgentAttachment("/p/Foo.kt", 3, 9, "x").label)
    }

    @Test
    fun `Prompt enthaelt Auszug mit Pfad und Zeilen`() {
        val msg = AgentAttachment.compose(listOf(AgentAttachment("/p/Foo.kt", 3, 4, "a\nb")), "Was macht das?")
        assertTrue(msg.startsWith("[Auszug: /p/Foo.kt, Zeile 3-4]\n```\na\nb\n```"))
        assertTrue(msg.endsWith("Was macht das?"))
    }

    @Test
    fun `ganze Datei wird nur referenziert, lange Auszuege gekuerzt`() {
        assertEquals("[Datei: /p/A.kt]\n\nFrage", AgentAttachment.compose(listOf(AgentAttachment("/p/A.kt")), "Frage"))
        val long = AgentAttachment("/p/A.kt", 1, 1, "x".repeat(AgentAttachment.MAX_CHARS + 5)).toPromptBlock()
        assertTrue(long.contains("(gekürzt)"))
    }

    @Test
    fun `ohne Anhaenge bleibt der Text unveraendert`() {
        assertEquals("Hi", AgentAttachment.compose(emptyList(), "Hi"))
    }
}
