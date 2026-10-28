/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsAndRequirementsTest {

    private fun manifest(permissions: String = "[\"shell\"]", contributes: String) = """
        {"id":"x.y","name":"X","version":"1","apiVersion":2,"permissions":$permissions,"contributes":$contributes}
    """.trimIndent()

    @Test
    fun `tools und requires werden geparst`() {
        val m = PluginManifestParser.parse(
            manifest(
                contributes = """{"tools":[{"id":"run","label":"Run","command":["python3","${'$'}{pluginDir}/t.py","${'$'}{projectDir}","${'$'}{input}"],
                "prompt":{"label":"Modus","default":"check"},"confirm":"Sicher?","timeoutSeconds":60}],
                "requires":[{"manager":"pkg","name":"python","check":"python3"},{"manager":"npm","name":"@scope/pkg"}]}"""
            )
        ).getOrThrow()
        val t = m.contributes.tools.single()
        assertEquals("check", t.prompt?.default)
        assertEquals(60, t.timeoutSeconds)
        assertEquals("Sicher?", t.confirm)
        assertEquals(listOf("tools", "requires"), m.contributes.kinds())
        assertEquals("pkg", m.contributes.requires[0].manager.id)
        assertEquals("pkg", m.contributes.requires[1].check) // Standard: letztes Namenssegment
    }

    @Test
    fun `tools ohne shell-Berechtigung werden abgelehnt`() {
        val r = PluginManifestParser.parse(
            manifest(permissions = "[]", contributes = """{"tools":[{"id":"a","command":["x"]}]}""")
        )
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("shell"))
    }

    @Test
    fun `ungueltige Paketnamen und Manager werden abgelehnt`() {
        listOf("-g", "--foo", "a b", "../x", "a;rm").forEach { bad ->
            assertTrue(bad, PluginManifestParser.parse(manifest(contributes = """{"requires":[{"manager":"npm","name":"$bad"}]}""")).isFailure)
        }
        assertTrue(PluginManifestParser.parse(manifest(contributes = """{"requires":[{"manager":"gem","name":"x"}]}""")).isFailure)
        assertFalse(RequirementPolicy.isValidName(""))
        assertTrue(RequirementPolicy.isValidName("@types/node"))
    }

    @Test
    fun `Store erlaubt nur Freigabeliste und keine tools`() {
        val ok = PluginManifestParser.parse(manifest(permissions = "[]", contributes = """{"lspServers":[{"id":"a","languages":["c"],"command":["clangd"]}],
            "requires":[{"manager":"pkg","name":"clang","check":"clangd"}]}""")).getOrThrow()
        assertNull(DeclarativeOnly.violation(listOf("plugin.json"), ok))
        val evil = PluginManifestParser.parse(manifest(permissions = "[]", contributes = """{"lspServers":[{"id":"a","languages":["c"],"command":["clangd"]}],
            "requires":[{"manager":"npm","name":"left-pad"}]}""")).getOrThrow()
        assertNotNull(DeclarativeOnly.violation(listOf("plugin.json"), evil))
        val tool = PluginManifestParser.parse(manifest(contributes = """{"tools":[{"id":"a","command":["x"]}]}""")).getOrThrow()
        assertNotNull(DeclarativeOnly.violation(listOf("plugin.json"), tool))
    }

    @Test
    fun `Kommando-Platzhalter bleiben einzelne Argumente`() {
        val t = ToolContribution("a", "A", command = listOf("python3", "${'$'}{pluginDir}/t.py", "${'$'}{projectDir}", "${'$'}{input}"))
        val cmd = t.expandCommand("/p roj", "/plug", "/home", "a; rm -rf / --x")
        assertEquals(listOf("python3", "/plug/t.py", "/p roj", "a; rm -rf / --x"), cmd)
    }

    @Test
    fun `Werkzeug-IDs bleiben ueber Plugins hinweg erhalten`() {
        fun m(id: String) = PluginManifestParser.parse(
            """{"id":"$id","name":"N","version":"1","apiVersion":2,"permissions":["shell"],"contributes":{"tools":[{"id":"run","command":["x"]}]}}"""
        ).getOrThrow()
        val active = ActiveContributions.of(listOf(m("p.one") to "/a", m("p.two") to "/b"))
        assertEquals(listOf("p.one", "p.two"), active.tools.map { it.pluginId })
    }
}
