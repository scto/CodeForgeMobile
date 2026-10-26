package com.codeforge.feature.editor.ui

import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LspInfoUiTest {
    @Test
    fun `parseHoverBlocks trennt Text und Codeblock`() {
        val blocks = parseHoverBlocks("```kotlin\nfun foo(): Int\n```\n\nGibt 42 zurück.\n")
        assertEquals(2, blocks.size)
        assertTrue(blocks[0].isCode)
        assertEquals("fun foo(): Int", blocks[0].text)
        assertFalse(blocks[1].isCode)
        assertEquals("Gibt 42 zurück.", blocks[1].text)
    }

    @Test
    fun `parseHoverBlocks behandelt ungeschlossenen Codeblock und reinen Text`() {
        assertEquals(listOf(HoverBlock("nur Text", false)), parseHoverBlocks("nur Text"))
        val open = parseHoverBlocks("```\nval x = 1")
        assertEquals(listOf(HoverBlock("val x = 1", true)), open)
    }

    @Test
    fun `signatureAnnotated hebt aktiven Parameter hervor`() {
        val text = signatureAnnotated("foo(a: Int, b: String)", "b: String", SpanStyle())
        assertEquals("foo(a: Int, b: String)", text.text)
        assertEquals(1, text.spanStyles.size)
        assertEquals(12, text.spanStyles[0].start)
        assertEquals(21, text.spanStyles[0].end)
        assertTrue(signatureAnnotated("foo()", null, SpanStyle()).spanStyles.isEmpty())
    }
}
