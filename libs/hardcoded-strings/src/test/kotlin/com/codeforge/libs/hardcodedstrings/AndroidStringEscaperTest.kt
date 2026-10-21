package com.codeforge.libs.hardcodedstrings

import com.codeforge.libs.hardcodedstrings.generator.AndroidStringEscaper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidStringEscaperTest {

    @Test
    fun `escapes quotes and apostrophes correctly`() {
        val input = "Don't touch \"this\""
        val result = AndroidStringEscaper.escapeForXml(input)
        assertEquals("Don\\'t touch \\\"this\\\"", result.xmlValue)
    }

    @Test
    fun `escapes XML entities`() {
        val input = "Tom & Jerry <friends>"
        val result = AndroidStringEscaper.escapeForXml(input)
        assertEquals("Tom &amp; Jerry &lt;friends&gt;", result.xmlValue)
    }

    @Test
    fun `escapes leading at sign and question mark`() {
        val atInput = "@string/foo"
        val qInput = "?attr/bar"
        assertEquals("\\@string/foo", AndroidStringEscaper.escapeForXml(atInput).xmlValue)
        assertEquals("\\?attr/bar", AndroidStringEscaper.escapeForXml(qInput).xmlValue)
    }

    @Test
    fun `preserves leading and trailing spaces with quotes`() {
        val input = " Hello World "
        val result = AndroidStringEscaper.escapeForXml(input)
        assertEquals("\" Hello World \"", result.xmlValue)
    }

    @Test
    fun `converts simple template variables to positional args`() {
        val input = "Hello \$name, you have \$count unread messages"
        val result = AndroidStringEscaper.escapeForXml(input, isTemplate = true)
        assertEquals("Hello %1\$s, you have %2\$s unread messages", result.xmlValue)
        assertEquals(listOf("name", "count"), result.placeholders)
    }

    @Test
    fun `converts complex expression template to positional args`() {
        val input = "User: \${user.fullName} (id: \${user.id})"
        val result = AndroidStringEscaper.escapeForXml(input, isTemplate = true)
        assertEquals("User: %1\$s (id: %2\$s)", result.xmlValue)
        assertEquals(listOf("user.fullName", "user.id"), result.placeholders)
    }
}
