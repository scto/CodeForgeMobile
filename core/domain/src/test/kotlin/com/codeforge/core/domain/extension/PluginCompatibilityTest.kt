/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCompatibilityTest {

    @Test
    fun `Versionen werden numerisch je Segment verglichen`() {
        assertTrue(PluginCompatibility.compareVersions("3.0.5", "3.0.10") < 0)
        assertTrue(PluginCompatibility.compareVersions("3.1", "3.0.9") > 0)
        assertEquals(0, PluginCompatibility.compareVersions("3.0", "3.0.0"))
        assertEquals(0, PluginCompatibility.compareVersions("3.0.5-beta", "3.0.5"))
    }

    @Test
    fun `zu alte App wird gemeldet`() {
        val issues = PluginCompatibility.check("3.1.0", emptyList(), "3.0.5", emptyMap())
        assertEquals(listOf(PluginIssue.HostTooOld("3.1.0", "3.0.5")), issues)
    }

    @Test
    fun `fehlende und deaktivierte Abhaengigkeiten werden unterschieden`() {
        val issues = PluginCompatibility.check("", listOf("a", "b", "c"), "3.0.5", mapOf("b" to false, "c" to true))
        assertEquals(listOf(PluginIssue.DependencyMissing("a"), PluginIssue.DependencyDisabled("b")), issues)
    }

    @Test
    fun `ohne Anforderungen keine Probleme`() {
        assertTrue(PluginCompatibility.check("", emptyList(), "1.0", emptyMap()).isEmpty())
    }

    @Test
    fun `Manifest liest minHostVersion und dependsOn`() {
        val m = PluginManifestParser.parse(
            """{"id":"x","name":"X","version":"1","apiVersion":2,"minHostVersion":"3.1.0","dependsOn":["y"]}"""
        ).getOrThrow()
        assertEquals("3.1.0", m.minHostVersion)
        assertEquals(listOf("y"), m.dependsOn)
    }

    @Test
    fun `Manifest lehnt Selbstabhaengigkeit und ungueltige Version ab`() {
        assertTrue(PluginManifestParser.parse("""{"id":"x","name":"X","version":"1","dependsOn":["x"]}""").isFailure)
        assertTrue(PluginManifestParser.parse("""{"id":"x","name":"X","version":"1","minHostVersion":"neu"}""").isFailure)
    }
}
