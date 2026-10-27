package com.codeforge.core.domain.extension

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prüft `textmate-plugins/src` und die Vorlage: Manifest, App-Farben und die vom Plugin-Ordner aus aufgelöste TextMate-Datei. */
class TextMatePluginsValidTest {
    private fun root(name: String): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, name) }.first { it.isDirectory }

    private fun checkPlugin(dir: File) {
        val m = PluginManifestParser.parse(File(dir, "plugin.json").readText()).getOrThrow()
        m.contributes.colorThemes.forEach { c ->
            val t = ColorThemeFileParser.parse(File(dir, c.path).readText()).getOrThrow()
            val tm = File(dir, requireNotNull(t.textMateTheme) { "${dir.name}: textmateTheme fehlt" })
            assertTrue("${dir.name}: ${tm.path} fehlt", tm.isFile)
            val json = Json.parseToJsonElement(tm.readText()).jsonObject
            assertTrue(json["tokenColors"] is JsonArray)
            assertTrue((json["colors"] as JsonObject).containsKey("editor.background"))
        }
    }

    @Test
    fun `15 TextMate-Plugins sind gueltig und deklarativ`() {
        val folders = root("textmate-plugins/src").listFiles { f -> f.isDirectory }.orEmpty()
        assertEquals(15, folders.size)
        folders.forEach { dir ->
            checkPlugin(dir)
            val m = PluginManifestParser.parse(File(dir, "plugin.json").readText()).getOrThrow()
            assertEquals(dir.name, m.id)
            assertEquals("codeforge", m.publisher)
            assertTrue(Regex("com\\.codeforge\\.plugin\\.[a-z0-9-]+\\.theme").matches(m.id))
            assertEquals(setOf(true, false), m.contributes.colorThemes.map { it.isDark }.toSet())
            val files = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path }.toList()
            assertNull(DeclarativeOnly.violation(files, m))
        }
    }

    @Test
    fun `Farbschema-Vorlage verweist auf ein vorhandenes TextMate-Theme`() {
        checkPlugin(File(root("templates"), "color-theme"))
    }
}
