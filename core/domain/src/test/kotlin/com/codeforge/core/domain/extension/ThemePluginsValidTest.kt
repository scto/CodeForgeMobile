package com.codeforge.core.domain.extension

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die generierten Preset-Theme-Plugins unter `theme-plugins/src` müssen mit den echten Parsern gültig sein. */
class ThemePluginsValidTest {
    private val src: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "theme-plugins/src") }.first { it.isDirectory }

    @Test
    fun `alle 15 Theme-Plugins sind gueltig und deklarativ`() {
        val folders = src.listFiles { f -> f.isDirectory }.orEmpty()
        assertEquals(15, folders.size)
        folders.forEach { dir ->
            val m = PluginManifestParser.parse(File(dir, "plugin.json").readText()).getOrThrow()
            assertEquals(dir.name, m.id)
            assertTrue(Regex("com\\.codeforge\\.plugin\\.[a-z0-9-]+\\.theme").matches(m.id))
            assertEquals("codeforge", m.publisher)
            assertEquals(2, m.contributes.colorThemes.size)
            assertEquals(setOf(true, false), m.contributes.colorThemes.map { it.isDark }.toSet())
            m.contributes.colorThemes.forEach {
                val t = ColorThemeFileParser.parse(File(dir, it.path).readText()).getOrThrow()
                assertEquals(setOf("primary", "secondary", "tertiary"), t.colors.keys)
            }
            val files = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path }.toList()
            assertNull(DeclarativeOnly.violation(files, m))
        }
    }
}
