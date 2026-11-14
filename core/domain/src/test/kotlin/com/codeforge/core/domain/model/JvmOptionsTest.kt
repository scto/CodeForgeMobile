package com.codeforge.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmOptionsTest {
    @Test fun buildsArgsAndSkipsInvalidEntries() {
        val o = JvmOptions(xms = "512m", xmx = "2g", maxMetaspace = "abc", extraFlags = "-XX:+UseG1GC  ;rm -Xss512k")
        assertEquals(listOf("-Xms512m", "-Xmx2g", "-XX:+UseG1GC", "-Xss512k"), o.toArgs())
        assertEquals("abc", o.firstInvalid())
    }

    @Test fun emptyMeansJvmDefault() {
        assertTrue(JvmOptions().toArgs().isEmpty())
        assertNull(JvmOptions().firstInvalid())
    }

    @Test fun flagsRejectShellMetacharacters() {
        assertFalse(JvmOptions.isValidFlag("-Dx=\$(id)"))
        assertFalse(JvmOptions.isValidFlag("-Dx=a;b"))
        assertFalse(JvmOptions.isValidFlag("UseG1GC"))
        assertTrue(JvmOptions.isValidFlag("-XX:G1HeapRegionSize=32m"))
        assertEquals("-Dx=`id`", JvmOptions(extraFlags = "-Dx=`id`").firstInvalid())
    }

    @Test fun settingsKeysAndFeatures() {
        assertEquals("vscode-langservers-extracted", LspServerDefaults.keyFor("vscode-css-language-server"))
        assertEquals("jdtls", LspServerDefaults.keyFor("jdtls"))
        val s = LspServerSettings(disabledFeatures = setOf(LspFeature.HOVER))
        assertFalse(s.allows(LspFeature.HOVER))
        assertTrue(s.allows(LspFeature.COMPLETION))
        assertFalse(s.copy(enabled = false).allows(LspFeature.COMPLETION))
    }
}
