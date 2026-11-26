package com.codeforge.feature.editor.folding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldingRulesTest {
    @Test
    fun bracketsKeepClosingLine() {
        val lines = """
            fun a() {
                val s = "{ nicht }"
                // { auch nicht
                val list = listOf(
                    1, 2,
                )
                val arr = [
                  3 ]
            }
        """.trimIndent().lines()
        val r = FoldingRules.brackets(lines)
        assertTrue(r.toString(), 0..7 in r)
        assertTrue(r.toString(), 6..7 in r)
        assertEquals(2, r.size)
    }

    @Test
    fun rawStringsAndChars() {
        val lines = """
            val q = '{'
            val raw = ${"\"\"\""}
              { ignoriert
            ${"\"\"\""}
            class X {
            }
        """.trimIndent().lines()
        assertEquals(listOf(4..4).filter { it.last > it.first }, FoldingRules.brackets(lines, FoldingRules.Syntax(tripleQuotes = true)))
    }

    @Test
    fun commentsImportsRuns() {
        val lines = """
            import a.B
            import a.C

            import a.D
            /* Kopf
             * Text
             */
            // eins
            // zwei
            // drei
            fun x() = 1
        """.trimIndent().lines()
        assertEquals(listOf(0..3), FoldingRules.imports(lines, listOf("import ")))
        assertEquals(listOf(4..6), FoldingRules.blockComments(lines, "/*", "*/"))
        assertEquals(listOf(7..9), FoldingRules.commentRuns(lines, listOf("//")))
    }

    @Test
    fun indentationPython() {
        val lines = """
            def f():
                if x:
                    a()

                b()
            g()
        """.trimIndent().lines()
        val r = FoldingRules.indentation(lines)
        assertTrue(0..4 in r)
        assertTrue(1..2 in r)
    }

    @Test
    fun xmlElementsAndAttributes() {
        val lines = """
            <LinearLayout
                android:id="@+id/a"
                android:orientation="vertical">
                <TextView
                    android:text="x" />
                <!-- Kommentar
                     zweizeilig -->
            </LinearLayout>
        """.trimIndent().lines()
        val r = FoldingRules.xmlElements(lines)
        assertTrue(r.toString(), 0..6 in r)
        assertTrue(r.toString(), 0..1 in r)
        assertTrue(r.toString(), 3..4 in r)
    }

    @Test
    fun markdownSections() {
        val lines = """
            # Titel
            Text
            ## Teil
            mehr

            # Zweiter
            ```
            # kein Titel
            ```
        """.trimIndent().lines()
        val r = FoldingRules.markdownSections(lines)
        assertEquals(listOf(0..3, 2..3, 5..8), r)
    }
}
