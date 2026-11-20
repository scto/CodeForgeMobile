package com.codeforge.plugin.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextTransformsTest {
    @Test
    fun `Schreibweisen`() {
        assertEquals(listOf("xml", "http", "request"), TextTransforms.words("XMLHttpRequest"))
        assertEquals("fooBarBaz", TextTransforms.camel("foo_bar-baz"))
        assertEquals("FooBar", TextTransforms.pascal("foo bar"))
        assertEquals("foo_bar", TextTransforms.snake("fooBar"))
        assertEquals("foo-bar", TextTransforms.kebab("FOO_BAR"))
        assertEquals("MAX_VALUE2", TextTransforms.constant("maxValue2"))
    }

    @Test
    fun `Zeilen und Kodierungen`() {
        assertEquals("a\nB\nc", TextTransforms.sortLines("c\nB\na"))
        assertEquals("c\nB\na", TextTransforms.sortLines("a\nc\nB", descending = true))
        assertEquals("a\nb", TextTransforms.uniqueLines("a\nb\na"))
        assertEquals("x\ny", TextTransforms.trimLines("x  \ny\t"))
        assertEquals("aGFsbG8=", TextTransforms.base64Encode("hallo"))
        assertEquals("hallo", TextTransforms.base64Decode("aGFsbG8="))
        assertEquals("a+b%26c", TextTransforms.urlEncode("a b&c"))
        assertEquals("2 Zeilen · 4 Wörter · 10 Zeichen", TextTransforms.stats("ab cd\nef g"))
    }
}
