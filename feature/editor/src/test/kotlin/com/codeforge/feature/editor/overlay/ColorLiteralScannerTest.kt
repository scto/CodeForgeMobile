package com.codeforge.feature.editor.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorLiteralScannerTest {

    @Test fun androidXmlAndJson() {
        val text = """
            <color name="a">#6200EE</color>
            <color name="b">#80FF0000</color>
            { "foreground": "#f0c" }
        """.trimIndent()
        val m = ColorLiteralScanner.scan(text)
        assertEquals(listOf(0, 1, 2), m.map { it.line })
        assertEquals(0xFF6200EE.toInt(), m[0].argb)
        assertEquals(0x80FF0000.toInt(), m[1].argb)
        assertEquals(0xFFFF00CC.toInt(), m[2].argb)
    }

    @Test fun kotlinHexAndMultiplePerLine() {
        val m = ColorLiteralScanner.scan("val a = Color(0xFF112233); val b = \"#abc\"")
        assertEquals(2, m.size)
        assertEquals(0xFF112233.toInt(), m.first { it.argb == 0xFF112233.toInt() }.argb)
    }

    @Test fun ignoresNonColors() {
        assertTrue(ColorLiteralScanner.scan("&#123456; #include #ghijkl x#abcdef 0x12").isEmpty())
        assertTrue(ColorLiteralScanner.scan("# heading\n#12345").isEmpty())
    }

    @Test fun cssAlphaLast() {
        assertEquals(0x80FF0000.toInt(), ColorLiteralScanner.scan("#FF000080", alphaLast = true).single().argb)
    }

    @Test fun supportsByExtension() {
        assertTrue(ColorLiteralScanner.supports("/p/res/values/colors.xml"))
        assertFalse(ColorLiteralScanner.supports("/p/README.md"))
    }
}
