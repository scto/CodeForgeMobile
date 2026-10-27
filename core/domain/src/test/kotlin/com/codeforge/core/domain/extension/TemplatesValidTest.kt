package com.codeforge.core.domain.extension

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stellt sicher, dass die Vorlagen unter `templates/` mit den echten Parsern gültig bleiben. */
class TemplatesValidTest {
    private val dir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "templates") }.first { it.isDirectory }

    private fun manifest(name: String) =
        PluginManifestParser.parse(File(dir, "$name/plugin.json").readText()).getOrThrow()

    private fun file(name: String, rel: String) = File(dir, "$name/$rel")

    @Test
    fun `alle Vorlagen haben gueltige Manifeste`() {
        val folders = dir.listFiles { f -> File(f, "plugin.json").isFile }.orEmpty()
        assertTrue(folders.size >= 8)
        folders.forEach { assertEquals(it.name, 2, manifest(it.name).apiVersion) }
    }

    @Test
    fun `Farbschema Icons Sprachpaket sind lesbar`() {
        val c = manifest("color-theme").contributes.colorThemes.single()
        assertTrue(ColorThemeFileParser.parse(file("color-theme", c.path).readText()).isSuccess)
        val i = manifest("icon-theme").contributes.iconThemes.single()
        val icons = IconThemeFileParser.parse(file("icon-theme", i.path).readText()).getOrThrow()
        icons.allPaths().forEach { assertTrue(it, File(file("icon-theme", i.path).parentFile, it).isFile) }
        val l = manifest("language-pack").contributes.languagePacks.single()
        assertTrue(LanguagePackFileParser.parse(file("language-pack", l.path).readText()).isSuccess)
    }

    @Test
    fun `deklarative Vorlagen bestehen die Store-Pruefung, Code-Plugin nicht`() {
        val decl = listOf("color-theme", "icon-theme", "language-pack", "grammar", "snippets", "lsp-server", "extension-pack")
        decl.forEach { n ->
            val files = File(dir, n).walkTopDown().filter { it.isFile }.map { it.relativeTo(File(dir, n)).path }.toList()
            assertEquals(n, null, DeclarativeOnly.violation(files, manifest(n)))
        }
        assertTrue(manifest("code-plugin").entryPointClass != null)
    }
}
