// Modul: :core:domain
package com.codeforge.core.domain.extension

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginApiDeprecationTest {
    @Test
    fun onlyCodeApi1Deprecated() {
        assertTrue(PluginApiVersion.isDeprecated(1, "com.example.Plugin"))
        assertFalse(PluginApiVersion.isDeprecated(2, "com.example.Plugin"))
        assertFalse(PluginApiVersion.isDeprecated(1, null))
        assertFalse(PluginApiVersion.isDeprecated(1, ""))
    }

    @Test
    fun missingApiVersionParses() {
        val m = PluginManifestParser.parse("""{"id":"a.b","name":"A","version":"1","entryPointClass":"a.B"}""").getOrThrow()
        assertTrue(PluginApiVersion.isDeprecated(m.apiVersion, m.entryPointClass))
    }
}
