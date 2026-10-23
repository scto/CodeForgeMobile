// Modul: :core:domain
package com.codeforge.core.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSanitizerTest {

    @Test
    fun testExcludedFiles() {
        assertTrue(AiSanitizer.isPathExcluded("/path/to/keystore.jks"))
        assertTrue(AiSanitizer.isPathExcluded("release.keystore"))
        assertTrue(AiSanitizer.isPathExcluded("cert.p12"))
        assertTrue(AiSanitizer.isPathExcluded(".env"))
        assertTrue(AiSanitizer.isPathExcluded(".env.local"))
        assertTrue(AiSanitizer.isPathExcluded(".env.production"))
        assertTrue(AiSanitizer.isPathExcluded("/root/local.properties"))
        assertTrue(AiSanitizer.isPathExcluded("signing.properties"))
        assertTrue(AiSanitizer.isPathExcluded("/home/.ssh/id_rsa"))
        assertTrue(AiSanitizer.isPathExcluded("id_ed25519"))
        assertTrue(AiSanitizer.isPathExcluded("server.key"))
        assertTrue(AiSanitizer.isPathExcluded("cert.pem"))

        assertFalse(AiSanitizer.isPathExcluded("MainActivity.kt"))
        assertFalse(AiSanitizer.isPathExcluded("build.gradle.kts"))
        assertFalse(AiSanitizer.isPathExcluded("AndroidManifest.xml"))
        assertFalse(AiSanitizer.isPathExcluded("strings.xml"))
    }

    @Test
    fun testSensitiveTokensRedaction() {
        val sampleCode = """
            val ghToken = "ghp_123456789012345678901234567890123456"
            val openAiKey = "sk-abcdefghijklmnopqrstuvwxyz123456"
            val anthropicKey = "sk-ant-abcdefghijklmnopqrstuvwxyz123456"
            val geminiKey = "AIzaSyD-1234567890abcdefghijklmnopqrst"
            val header = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xyz"
            val password = "secret_password_1234"
            val normalVar = "Hello World"
        """.trimIndent()

        val (sanitized, count) = AiSanitizer.sanitizeText(sampleCode)
        assertTrue(count >= 5)
        assertFalse(sanitized.contains("ghp_123456789012345678901234567890123456"))
        assertFalse(sanitized.contains("sk-abcdefghijklmnopqrstuvwxyz123456"))
        assertFalse(sanitized.contains("sk-ant-abcdefghijklmnopqrstuvwxyz123456"))
        assertFalse(sanitized.contains("AIzaSyD-1234567890abcdefghijklmnopqrst"))
        assertFalse(sanitized.contains("secret_password_1234"))
        assertTrue(sanitized.contains("[REDACTED_SECRET]"))
        assertTrue(sanitized.contains("Hello World"))
    }

    @Test
    fun testFalseAlarmsNotRedacted() {
        val benignCode = """
            val secretKeyName = "normalIdentifier"
            val isSecret = false
            fun findPassword(): Boolean = true
            val apiKeyLength = 32
        """.trimIndent()

        val (sanitized, count) = AiSanitizer.sanitizeText(benignCode)
        assertEquals(0, count)
        assertEquals(benignCode, sanitized)
    }

    @Test
    fun testTruncation() {
        val smallText = "Hello World"
        val (result1, truncated1) = AiSanitizer.truncateWithNotice(smallText, 100)
        assertFalse(truncated1)
        assertEquals(smallText, result1)

        val longText = "a".repeat(150)
        val (result2, truncated2) = AiSanitizer.truncateWithNotice(longText, 100)
        assertTrue(truncated2)
        assertTrue(result2.startsWith("a".repeat(100)))
        assertTrue(result2.contains("Truncated 50 characters"))
    }
}
