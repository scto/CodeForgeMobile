package com.codeforge.core.domain.extension

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SandboxPolicyTest {
    private fun parse(json: String) = PluginManifestParser.parse(json)

    private val ok = """{"id":"a.b","name":"A","version":"1","apiVersion":4,"sandbox":true,"entryPointClass":"a.B","permissions":["editor"]}"""

    @Test
    fun `Manifest-Regeln für die Sandbox`() {
        assertTrue(parse(ok).getOrThrow().sandbox)
        assertTrue(parse(ok.replace("\"apiVersion\":4", "\"apiVersion\":3")).isFailure)
        assertTrue(parse(ok.replace("\"editor\"", "\"shell\"")).isFailure)
        assertTrue(parse(ok.replace(",\"entryPointClass\":\"a.B\"", "")).isFailure)
        assertTrue(parse(ok.replace("}", ",\"activationEvents\":[\"onProject:x\"]}")).isFailure)
    }

    @Test
    fun `Store nimmt Sandbox-Plugins mit genau einer classes dex`() {
        val m = parse(ok).getOrThrow()
        assertNull(SandboxPolicy.storeInstallViolation(listOf("plugin.json", "classes.dex", "signature.json"), m))
        assertNotNull(SandboxPolicy.storeInstallViolation(listOf("plugin.json"), m))
        assertNotNull(SandboxPolicy.storeInstallViolation(listOf("plugin.json", "classes.dex", "lib/x.so"), m))
        // Ohne Sandbox gilt weiter: kein Code im Store
        val code = parse(ok.replace("\"sandbox\":true,", "")).getOrThrow()
        assertNotNull(SandboxPolicy.storeInstallViolation(listOf("plugin.json", "classes.dex"), code))
    }
}
