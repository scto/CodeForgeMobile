package com.codeforge.libs.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextDiffTest {
    @Test
    fun `gleiche Texte ergeben leeren Diff`() = assertEquals("", TextDiff.unified("a\nb", "a\nb"))

    @Test
    fun `Aenderung in der Mitte mit Kontext und Hunk-Kopf`() {
        val before = (1..10).joinToString("\n") { "z$it" }
        val after = before.replace("z5", "NEU")
        assertEquals(
            "@@ -3,5 +3,5 @@\n z3\n z4\n-z5\n+NEU\n z6\n z7",
            TextDiff.unified(before, after),
        )
    }

    @Test
    fun `neue Datei besteht nur aus Plus-Zeilen, geloeschte nur aus Minus-Zeilen`() {
        assertEquals("@@ -0,0 +1,2 @@\n+a\n+b", TextDiff.unified(null, "a\nb"))
        assertEquals("@@ -1,2 +0,0 @@\n-a\n-b", TextDiff.unified("a\nb", null))
    }

    @Test
    fun `getrennte Aenderungen ergeben zwei Hunks, Einfuegen und Loeschen werden erkannt`() {
        val before = (1..20).joinToString("\n") { "l$it" }
        val after = before.replace("l2\n", "").replace("l18", "l18\nextra")
        val d = TextDiff.unified(before, after)
        assertEquals(2, d.lines().count { it.startsWith("@@") })
        assertTrue(d.contains("-l2"))
        assertTrue(d.contains("+extra"))
    }

    @Test
    fun `lange Diffs werden gekuerzt`() {
        val d = TextDiff.unified("", (1..500).joinToString("\n") { "x$it" }, maxLines = 10)
        assertTrue(d.endsWith("weitere Zeilen)"))
        assertEquals(11, d.lines().size)
    }

    @Test
    fun `Vorschau der Werkzeuge entspricht der spaeteren Aenderung und aendert nichts`() = runBlocking {
        val ws = FakeWorkspace(mapOf("a.kt" to "eins\nzwei\ndrei"))
        val tools = BuiltinTools.create(ws).associateBy { it.name }
        val p = tools.getValue("edit_file").preview(args("path" to "a.kt", "old_string" to "zwei", "new_string" to "ZWEI"))
        assertNotNull(p)
        assertTrue(p!!.contains("-zwei") && p.contains("+ZWEI"))
        assertEquals("eins\nzwei\ndrei", ws.files["a.kt"])
        assertNull(tools.getValue("edit_file").preview(args("path" to "a.kt", "old_string" to "gibtsnicht", "new_string" to "x")))
        assertTrue(tools.getValue("create_file").preview(args("path" to "n.kt", "content" to "x"))!!.contains("+x"))
        assertNull(tools.getValue("read_file").preview(args("path" to "a.kt")))
    }
}
