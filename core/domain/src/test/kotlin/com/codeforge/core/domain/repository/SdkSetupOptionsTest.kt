// Modul: :core:domain
package com.codeforge.core.domain.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SdkSetupOptionsTest {
    @Test
    fun `feste Zielumgebung als Kommando`() {
        assertEquals(
            "codeforge-env setup --jdk 21 --sdk 37.0.0 --platform 36 --ndk r29 --cmake 4.1.2",
            SdkSetupOptions().toCommand()
        )
    }

    @Test
    fun `unsichere Versionsangaben werden abgelehnt`() {
        assertThrows(IllegalArgumentException::class.java) { SdkSetupOptions(jdk = "21; rm -rf /") }
    }
}
