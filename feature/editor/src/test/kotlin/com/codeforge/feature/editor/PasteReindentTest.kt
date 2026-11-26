package com.codeforge.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class PasteReindentTest {

    @Test
    fun singleLineUnchanged() {
        assertEquals("  foo()", PasteReindent.reindent("  foo()", "    ", 4))
    }

    @Test
    fun blankPrefixTakesTargetIndent() {
        val pasted = listOf("        if (x) {", "            y()", "        }").joinToString("\n")
        val expected = listOf("if (x) {", "        y()", "    }").joinToString("\n")
        assertEquals(expected, PasteReindent.reindent(pasted, "    ", 4))
    }

    @Test
    fun codePrefixKeepsFirstLine() {
        val pasted = listOf("listOf(", "\t1,", "\t2", ")").joinToString("\n")
        val expected = listOf("listOf(", "        1,", "        2", "    )").joinToString("\n")
        assertEquals(expected, PasteReindent.reindent(pasted, "    val a = ", 4))
    }

    @Test
    fun blankLinesAreEmptied() {
        assertEquals("a\n\n  b", PasteReindent.reindent("a\n   \nb", "  ", 4))
    }
}
