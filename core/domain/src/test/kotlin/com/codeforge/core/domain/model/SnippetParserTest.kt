// Modul: :core:domain
package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetParserTest {

    @Test
    fun `validate simple text without variables returns Valid`() {
        val result = SnippetParser.validate("println(\"Hello World\")")
        assertTrue(result is SnippetValidationResult.Valid)
    }

    @Test
    fun `validate tabstops and final tabstop returns Valid`() {
        val result = SnippetParser.validate("fun \$1(\$2): \$3 {\n    \$0\n}")
        assertTrue(result is SnippetValidationResult.Valid)
    }

    @Test
    fun `validate placeholders with default values returns Valid`() {
        val body = "for (int \${1:i} = 0; \${1:i} < \${2:max}; \${1:i}++) {\n    \$0\n}"
        val result = SnippetParser.validate(body)
        assertTrue(result is SnippetValidationResult.Valid)
    }

    @Test
    fun `validate nested placeholders returns Valid`() {
        val body = "\${1:outer \${2:inner} end}"
        val result = SnippetParser.validate(body)
        assertTrue(result is SnippetValidationResult.Valid)
    }

    @Test
    fun `validate choice tabstop returns Valid`() {
        val body = "val visibility = \${1|public,private,protected|}"
        val result = SnippetParser.validate(body)
        assertTrue(result is SnippetValidationResult.Valid)
    }

    @Test
    fun `validate escaped dollar and backslash returns Valid`() {
        val body = "val cost = \\\$100\nval path = \"C:\\\\test\""
        val result = SnippetParser.validate(body)
        assertTrue(result is SnippetValidationResult.Valid)
    }

    @Test
    fun `validate known variables returns Valid`() {
        val body = "package \$PACKAGE\nclass \$CLASSNAME {\n    // Year: \$CURRENT_YEAR\n}"
        val result = SnippetParser.validate(body)
        assertTrue(result is SnippetValidationResult.Valid)
    }

    @Test
    fun `validate unclosed brace reports Invalid with line and column`() {
        val body = "fun test() {\n    \${1:unclosed"
        val result = SnippetParser.validate(body)
        assertTrue(result is SnippetValidationResult.Invalid)
        val invalid = result as SnippetValidationResult.Invalid
        assertEquals(2, invalid.line)
        assertEquals(5, invalid.column)
    }

    @Test
    fun `validate unclosed choice tabstop reports Invalid with line and column`() {
        val body = "val x = \${1|a,b,c"
        val result = SnippetParser.validate(body)
        assertTrue(result is SnippetValidationResult.Invalid)
        val invalid = result as SnippetValidationResult.Invalid
        assertEquals(1, invalid.line)
        assertEquals(9, invalid.column)
    }

    @Test
    fun `extractTabStops extracts unique tab stop numbers in order`() {
        val body = "for (\${1:i} = 0; \$1 < \${2:count}; \$1++) {\n    \$0\n}"
        val stops = SnippetParser.extractTabstops(body)
        assertEquals(listOf(0, 1, 2), stops)
    }
}
