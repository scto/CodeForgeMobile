// Modul: :core:domain
package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SigningModelsTest {

    @Test
    fun passwordStrengthEvaluation() {
        assertEquals(PasswordStrength.VERY_WEAK, PasswordStrengthEvaluator.evaluate("123"))
        assertEquals(PasswordStrength.VERY_WEAK, PasswordStrengthEvaluator.evaluate("abcdef"))
        assertEquals(PasswordStrength.WEAK, PasswordStrengthEvaluator.evaluate("abcdefgh"))
        assertEquals(PasswordStrength.MEDIUM, PasswordStrengthEvaluator.evaluate("Abcdefgh1"))
        assertEquals(PasswordStrength.STRONG, PasswordStrengthEvaluator.evaluate("Abcdefgh1!"))
        assertEquals(PasswordStrength.VERY_STRONG, PasswordStrengthEvaluator.evaluate("SuperSecurePassword123!@#"))
    }

    @Test
    fun distinguishedNameFormatting() {
        val dn = DistinguishedName(
            commonName = "John Doe",
            organization = "CodeForge",
            organizationalUnit = "Engineering",
            locality = "Berlin",
            state = "Berlin",
            country = "DE"
        )
        val formatted = dn.toDnameString()
        assertEquals("CN=John Doe, OU=Engineering, O=CodeForge, L=Berlin, ST=Berlin, C=DE", formatted)
    }

    @Test
    fun resolveOutputPathDefaults() {
        val req1 = SignApkRequest(
            inputApkPath = "/path/to/app-release-unsigned.apk",
            keystoreId = "test",
            alias = "test"
        )
        assertEquals("/path/to/app-release-signed.apk", req1.resolveOutputPath())

        val req2 = SignApkRequest(
            inputApkPath = "/path/to/my-app.apk",
            keystoreId = "test",
            alias = "test"
        )
        assertEquals("/path/to/my-app-signed.apk", req2.resolveOutputPath())
    }

    @Test
    fun gradleSigningSnippetContainsExpectedVariables() {
        val snippet = GradleSigningSnippet.generateSnippet()
        assertTrue(snippet.contains("CODEFORGE_KEYSTORE_PATH"))
        assertTrue(snippet.contains("CODEFORGE_KEYSTORE_PASSWORD"))
        assertTrue(snippet.contains("CODEFORGE_KEYSTORE_ALIAS"))
        assertTrue(snippet.contains("CODEFORGE_KEY_PASSWORD"))
        assertTrue(snippet.contains("signingConfigs"))
    }
}
