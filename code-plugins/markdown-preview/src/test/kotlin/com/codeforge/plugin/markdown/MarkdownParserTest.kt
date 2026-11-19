package com.codeforge.plugin.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `Blöcke werden erkannt`() {
        val md = """
            # Titel
            Erster Absatz
            geht weiter.

            - [x] fertig
            - [ ] offen
            1. eins
            2. zwei

            > Zitat

            ```kotlin
            val x = 1
            ```

            | A | B |
            |---|---|
            | 1 | 2 |

            ---
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        assertEquals(MdBlock.Heading(1, "Titel"), blocks[0])
        assertEquals(MdBlock.Paragraph("Erster Absatz geht weiter."), blocks[1])
        val tasks = blocks[2] as MdBlock.ListBlock
        assertEquals(listOf(true, false), tasks.items.map { it.checked })
        assertTrue((blocks[3] as MdBlock.ListBlock).ordered)
        assertEquals(MdBlock.Quote(listOf(MdBlock.Paragraph("Zitat"))), blocks[4])
        assertEquals(MdBlock.Code("kotlin", "val x = 1"), blocks[5])
        assertEquals(MdBlock.Table(listOf("A", "B"), listOf(listOf("1", "2"))), blocks[6])
        assertEquals(MdBlock.Rule, blocks[7])
    }

    @Test
    fun `Inline-Auszeichnungen`() {
        val spans = InlineMarkdown.parse("a **b** *c* `d` [e](f.md) ~~g~~ snake_case")
        assertTrue(spans.any { it.text == "b" && it.bold })
        assertTrue(spans.any { it.text == "c" && it.italic })
        assertTrue(spans.any { it.text == "d" && it.code })
        assertTrue(spans.any { it.text == "e" && it.link == "f.md" })
        assertTrue(spans.any { it.text == "g" && it.strike })
        assertTrue(spans.joinToString("") { it.text }.endsWith("snake_case"))
    }
}
