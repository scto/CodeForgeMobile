package com.codeforge.libs.hardcodedstrings

import com.codeforge.libs.hardcodedstrings.generator.KeyDeduplicator
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringFinding
import com.codeforge.libs.hardcodedstrings.model.ReplacementContextType
import com.codeforge.libs.hardcodedstrings.model.StringConfidence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class KeyDeduplicatorTest {

    @Test
    fun sameValueSharesKey() {
        val result = KeyDeduplicator.resolve(listOf(finding("a", "x", "Hallo"), finding("b", "y", "Hallo")), emptyMap())
        assertEquals(listOf("x", "x"), result.map { it.selectedKey })
    }

    @Test
    fun keyClashGetsSuffix() {
        val result = KeyDeduplicator.resolve(
            listOf(finding("a", "k", "Eins"), finding("b", "k", "Zwei"), finding("c", "k", "Drei")),
            emptyMap()
        )
        assertEquals(listOf("k", "k_2", "k_3"), result.map { it.selectedKey })
    }

    @Test
    fun respectsExistingEntries() {
        val result = KeyDeduplicator.resolve(
            listOf(finding("a", "k", "Neu"), finding("b", "other", "Alt")),
            mapOf("k" to "Alt")
        )
        assertEquals(listOf("k_2", "k"), result.map { it.selectedKey })
    }

    private fun finding(id: String, key: String, value: String) = HardcodedStringFinding(
        id = id,
        file = File("A.kt"),
        relativePath = "A.kt",
        moduleName = "app",
        line = 1,
        column = 1,
        endLine = 1,
        endColumn = 1,
        startOffset = 0,
        endOffset = 0,
        originalLiteral = "\"$value\"",
        rawValue = value,
        confidence = StringConfidence.HIGH,
        contextType = ReplacementContextType.COMPOSE,
        suggestedKey = key
    )
}
