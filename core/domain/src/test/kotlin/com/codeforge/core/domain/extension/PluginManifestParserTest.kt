package com.codeforge.core.domain.extension

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestParserTest {

    @Test
    fun `v1 Manifest bleibt kompatibel`() {
        val m = PluginManifestParser.parse(
            """{"id":"com.example.kotlin-lsp","name":"K","version":"1.0.0","description":"d","entryPointClass":"a.B"}"""
        ).getOrThrow()
        assertEquals(1, m.apiVersion)
        assertEquals("a.B", m.entryPointClass)
        assertTrue(m.contributes.isEmpty)
        assertFalse(m.isDeclarativeOnly)
    }

    @Test
    fun `v2 Manifest mit allen Contributions`() {
        val m = PluginManifestParser.parse(
            """
            {"id":"p.one","name":"One","version":"2.1","apiVersion":2,"publisher":"me","permissions":["commands"],
             "contributes":{
               "colorThemes":[{"id":"t","label":"T","uiTheme":"dark","path":"themes/t.json"}],
               "iconThemes":[{"id":"i","label":"I","path":"icons/i.json"}],
               "languagePacks":[{"locale":"fr","label":"Français","path":"l/fr.json"}],
               "grammars":[{"languageId":"zig","scopeName":"source.zig","path":"g/zig.json","extensions":["zig"]}],
               "snippets":[{"languageId":"kotlin","path":"s/k.json"}],
               "lspServers":[{"id":"zls","languages":["zig"],"command":["${'$'}{pluginDir}/zls"]}],
               "extensionPack":["p.two"]}}
            """.trimIndent()
        ).getOrThrow()
        assertNull(m.entryPointClass)
        assertTrue(m.isDeclarativeOnly)
        assertTrue(m.contributes.colorThemes.single().isDark)
        assertEquals("fr", m.contributes.languagePacks.single().locale)
        assertEquals(listOf("zig"), m.contributes.grammars.single().extensions)
        assertEquals("zls", m.contributes.lspServers.single().label)
        assertEquals(listOf("p.two"), m.contributes.extensionPack)
        assertEquals(setOf("commands"), m.permissions)
    }

    @Test
    fun `Pfad-Traversal und absolute Pfade werden abgelehnt`() {
        for (p in listOf("../x.json", "/etc/passwd", "a/../../b.json")) {
            val r = PluginManifestParser.parse(
                """{"id":"p","name":"n","version":"1","contributes":{"iconThemes":[{"id":"i","label":"I","path":"$p"}]}}"""
            )
            assertTrue("sollte scheitern: $p", r.isFailure)
        }
    }

    @Test
    fun `ungueltige ID, fehlende Felder, zu neue API und unbekannte Berechtigung scheitern`() {
        assertTrue(PluginManifestParser.parse("""{"id":"../x","name":"n","version":"1"}""").isFailure)
        assertTrue(PluginManifestParser.parse("""{"id":"x","version":"1"}""").isFailure)
        assertTrue(PluginManifestParser.parse("""{"id":"x","name":"n"}""").isFailure)
        assertTrue(PluginManifestParser.parse("""{"id":"x","name":"n","version":"1","apiVersion":99}""").isFailure)
        assertTrue(PluginManifestParser.parse("""{"id":"x","name":"n","version":"1","permissions":["root"]}""").isFailure)
        assertTrue(PluginManifestParser.parse("[]").isFailure)
        assertTrue(PluginManifestParser.parse("kein json").isFailure)
    }

    @Test
    fun `lspServer ohne command scheitert`() {
        val r = PluginManifestParser.parse(
            """{"id":"x","name":"n","version":"1","contributes":{"lspServers":[{"id":"a","languages":["zig"],"command":[]}]}}"""
        )
        assertTrue(r.isFailure)
    }
}

class ActiveContributionsTest {
    private fun manifest(id: String, theme: String) = PluginManifest(
        id = id, name = id, version = "1",
        contributes = Contributes(colorThemes = listOf(ColorThemeContribution(theme, theme, true, "t.json")))
    )

    @Test
    fun `erste Contribution mit gleicher ID gewinnt und Herkunft bleibt erhalten`() {
        val result = ActiveContributions.of(
            listOf(manifest("a", "dracula") to "/p/a", manifest("b", "dracula") to "/p/b", manifest("b", "nord") to "/p/b")
        )
        assertEquals(listOf("dracula", "nord"), result.colorThemes.map { it.item.id }.distinct())
        assertEquals("a", result.colorThemes.first { it.item.id == "dracula" }.pluginId)
        assertEquals("/p/b", result.colorThemes.first { it.item.id == "nord" }.pluginDir)
    }

    @Test
    fun `kinds listet nur nicht leere Arten`() {
        val c = Contributes(colorThemes = listOf(ColorThemeContribution("t", "T", false, "x.json")), extensionPack = listOf("p"))
        assertEquals(listOf("colorThemes", "extensionPack"), c.kinds())
    }
}

class GrammarLookupTest {
    private val c = ActiveContributions(
        grammars = listOf(
            Resolved("p", "/d", GrammarContribution("zig", "source.zig", "g.json", listOf("zig", ".ZIGON"))),
            Resolved("q", "/e", GrammarContribution("just", "source.just", "j.json", listOf("justfile")))
        )
    )

    @Test
    fun `Endung und Dateiname werden gefunden`() {
        assertEquals("source.zig", c.grammarForPath("/a/b/Main.ZIG")?.item?.scopeName)
        assertEquals("source.zig", c.grammarForPath("x.zigon")?.item?.scopeName)
        assertEquals("source.just", c.grammarForPath("/proj/Justfile")?.item?.scopeName)
        assertNull(c.grammarForPath("a.kt"))
    }
}
