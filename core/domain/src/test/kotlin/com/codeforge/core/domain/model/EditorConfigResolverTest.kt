package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorConfigResolverTest {

    private val sampleConfig = """
        root = true

        [*]
        charset = utf-8
        end_of_line = lf
        insert_final_newline = true
        trim_trailing_whitespace = true
        indent_size = 2

        [*.{kt,kts}]
        indent_style = space
        indent_size = 4
        max_line_length = 120

        [*.java]
        indent_style = tab
        indent_size = 8

        [*.json]
        indent_size = 2
    """.trimIndent()

    @Test
    fun `resolves kotlin file options from editorconfig`() {
        val options = EditorConfigResolver.resolve(sampleConfig, "app/src/main/kotlin/Main.kt")
        assertEquals(4, options.indentSize)
        assertFalse(options.useTabs)
        assertEquals(120, options.maxLineLength)
        assertTrue(options.trimTrailingWhitespace)
        assertTrue(options.ensureFinalNewline)
    }

    @Test
    fun `resolves kts file options from editorconfig`() {
        val options = EditorConfigResolver.resolve(sampleConfig, "build.gradle.kts")
        assertEquals(4, options.indentSize)
        assertFalse(options.useTabs)
        assertEquals(120, options.maxLineLength)
    }

    @Test
    fun `resolves java file options with tabs`() {
        val options = EditorConfigResolver.resolve(sampleConfig, "src/Main.java")
        assertEquals(8, options.indentSize)
        assertTrue(options.useTabs)
    }

    @Test
    fun `resolves json file options`() {
        val options = EditorConfigResolver.resolve(sampleConfig, "data/schema.json")
        assertEquals(2, options.indentSize)
    }

    @Test
    fun `falls back to general star section for unmatched specific extension`() {
        val options = EditorConfigResolver.resolve(sampleConfig, "README.md")
        assertEquals(2, options.indentSize)
        assertTrue(options.trimTrailingWhitespace)
    }

    @Test
    fun `returns fallback options when editorconfig is null or blank`() {
        val fallback = UnifiedFormatOptions(indentSize = 4, useTabs = true)
        val options = EditorConfigResolver.resolve(null, "Main.kt", fallback)
        assertEquals(fallback, options)
    }

    @Test
    fun `matchesPattern correctly handles glob expressions`() {
        assertTrue(EditorConfigResolver.matchesPattern("Main.kt", "*"))
        assertTrue(EditorConfigResolver.matchesPattern("Main.kt", "*.{kt,kts}"))
        assertTrue(EditorConfigResolver.matchesPattern("build.gradle.kts", "*.{kt,kts}"))
        assertFalse(EditorConfigResolver.matchesPattern("Main.java", "*.{kt,kts}"))
        assertTrue(EditorConfigResolver.matchesPattern("Main.java", "*.java"))
        assertFalse(EditorConfigResolver.matchesPattern("Main.kt", "*.java"))
    }
}
