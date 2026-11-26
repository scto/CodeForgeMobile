// Modul: :core:domain
package com.codeforge.core.domain.extension

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginApiSupportTest {
    @Test
    fun onlyCodeApi1Unsupported() {
        assertTrue(PluginApiVersion.isUnsupported(1, "com.example.Plugin"))
        assertFalse(PluginApiVersion.isUnsupported(2, "com.example.Plugin"))
        assertFalse(PluginApiVersion.isUnsupported(1, null))
        assertFalse(PluginApiVersion.isUnsupported(1, ""))
    }

    @Test
    fun missingApiVersionIsApi1() {
        val m = PluginManifestParser.parse("""{"id":"a.b","name":"A","version":"1","entryPointClass":"a.B"}""").getOrThrow()
        assertTrue(PluginApiVersion.isUnsupported(m.apiVersion, m.entryPointClass))
    }
}
