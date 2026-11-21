// Modul: :app
package com.codeforge.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SandboxProcessTest {
    @Test
    fun isolatedUidsDetected() {
        assertTrue(SandboxProcess.isIsolatedUid(99_000))
        assertTrue(SandboxProcess.isIsolatedUid(99_999))
        assertTrue(SandboxProcess.isIsolatedUid(1_099_123)) // Nutzer 10
    }

    @Test
    fun regularUidsNotIsolated() {
        assertFalse(SandboxProcess.isIsolatedUid(10_234))
        assertFalse(SandboxProcess.isIsolatedUid(1_010_234))
        assertFalse(SandboxProcess.isIsolatedUid(1_000))
        assertFalse(SandboxProcess.isIsolatedUid(90_005)) // App-Zygote-Bereich
    }
}
