package com.codeforge.plugin.showcase

import com.codeforge.core.domain.extension.ide.IdeEditorSnapshot
import com.codeforge.core.domain.extension.ide.IdeTextEdit
import com.codeforge.libs.plugin_api.IdeFileEntry
import com.codeforge.libs.plugin_api.IdeGitStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowcaseLogicTest {
    private val fs = mapOf(
        "/p" to listOf(dir("/p/src"), dir("/p/build"), file("/p/README.md")),
        "/p/src" to listOf(file("/p/src/A.kt"), file("/p/src/B.kt")),
    )

    private fun dir(p: String) = IdeFileEntry(p, p.substringAfterLast('/'), true, 0, 0)
    private fun file(p: String) = IdeFileEntry(p, p.substringAfterLast('/'), false, 1, 0)

    @Test
    fun treeRows() {
        val git = mapOf("src/A.kt" to "MODIFIED")
        val closed = ProjectTree.rows("/p", emptySet(), git) { fs[it].orEmpty() }
        assertEquals(listOf("src", "README.md"), closed.map { it.name })
        assertEquals("MODIFIED", closed.first().git)
        val open = ProjectTree.rows("/p", setOf("/p/src"), git) { fs[it].orEmpty() }
        assertEquals(listOf("src", "A.kt", "B.kt", "README.md"), open.map { it.name })
        assertEquals(1, open[1].depth)
        assertEquals("MODIFIED", open[1].git)
        assertNull(open[2].git)
    }

    @Test
    fun gitMapAndSummary() {
        val s = IdeGitStatus("main", 2, 0, mapOf("a" to "MODIFIED"), listOf("b"))
        assertEquals(mapOf("b" to "STAGED", "a" to "MODIFIED"), ProjectTree.gitMap(s))
        assertEquals("main ↑2 · 1 geändert, 1 bereit", TextOps.gitSummary(s))
    }

    @Test
    fun textOps() {
        val s = IdeEditorSnapshot("/p/A.kt", "hallo welt", caret = 5, selectionStart = 0, selectionEnd = 5)
        assertEquals(IdeTextEdit(0, 5, "HALLO"), TextOps.upperSelection(s))
        assertNull(TextOps.upperSelection(s.copy(selectionEnd = 0)))
        assertEquals(IdeTextEdit(5, 5, "// TODO(me): "), TextOps.todoAt(s, "me"))
        assertEquals("# TODO(me): ", TextOps.todoAt(s.copy(path = "x.py"), "me").newText)
    }

    @Test
    fun eventLogIsBounded() {
        val log = EventLog(max = 2)
        log.add("a"); log.add("b"); log.add("c")
        assertEquals(listOf("c", "b"), log.snapshot())
    }
}
