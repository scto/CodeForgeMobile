package com.codeforge.libs.hardcodedstrings

import com.codeforge.libs.hardcodedstrings.generator.StringKeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StringKeyGeneratorTest {

    @Test
    fun `converts German umlauts to ae oe ue ss`() {
        val input = "Änderungen übernehmen für Großkunden"
        val snake = StringKeyGenerator.toSnakeCase(input)
        assertEquals("aenderungen_uebernehmen", snake)
    }

    @Test
    fun `sanitizes key and avoids leading digits`() {
        val sanitized = StringKeyGenerator.sanitizeKey("123_test_key")
        assertTrue(sanitized.startsWith("str_123"))
    }

    @Test
    fun `derives prefix from source file name`() {
        val file = File("/path/to/LoginScreen.kt")
        val prefix = StringKeyGenerator.derivePrefix(file)
        assertEquals("login", prefix)
    }

    @Test
    fun `reuses existing key if identical string exists in strings xml`() {
        val existing = mapOf("btn_save" to "Speichern", "lbl_cancel" to "Abbrechen")
        val key = StringKeyGenerator.generateKey("Speichern", existingKeys = existing)
        assertEquals("btn_save", key)
    }

    @Test
    fun `resolves collision with existing different value by appending suffix`() {
        val existing = mapOf("login_title" to "Different Text")
        val file = File("LoginScreen.kt")
        val key = StringKeyGenerator.generateKey("Title", sourceFile = file, existingKeys = existing)
        assertEquals("login_title_2", key)
    }
}
