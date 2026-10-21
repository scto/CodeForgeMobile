package com.codeforge.libs.hardcodedstrings

import com.codeforge.libs.hardcodedstrings.applier.StringsXmlWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StringsXmlWriterTest {

    @Test
    fun `creates clean strings xml from scratch`() {
        val entries = listOf("app_name" to "CodeForge", "btn_save" to "Speichern")
        val result = StringsXmlWriter.mergeEntries(null, entries)
        assertTrue(result.contains("<resources>"))
        assertTrue(result.contains("<string name=\"app_name\">CodeForge</string>"))
        assertTrue(result.contains("<string name=\"btn_save\">Speichern</string>"))
        assertTrue(result.contains("</resources>"))
    }

    @Test
    fun `inserts new strings into existing xml before closing tag`() {
        val existing = """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="existing_key">Vorhandener Text</string>
            </resources>
        """.trimIndent()

        val entries = listOf("new_key" to "Neuer Text")
        val result = StringsXmlWriter.mergeEntries(existing, entries)
        assertTrue(result.contains("<string name=\"existing_key\">Vorhandener Text</string>"))
        assertTrue(result.contains("<string name=\"new_key\">Neuer Text</string>"))
        assertTrue(result.trimEnd().endsWith("</resources>"))
    }

    @Test
    fun `does not duplicate existing keys`() {
        val existing = """
            <resources>
                <string name="dup_key">Original</string>
            </resources>
        """.trimIndent()

        val entries = listOf("dup_key" to "Modified")
        val result = StringsXmlWriter.mergeEntries(existing, entries)
        assertEquals(1, Regex("dup_key").findAll(result).count())
    }
}
