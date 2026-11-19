package com.codeforge.core.domain.extension

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Prüft Plugins, die `tool-plugins/scripts/vscode_import.py` aus echten Extensions erzeugt hat, mit den Parsern der App.
 * Nur mit `CF_IMPORTED_SAMPLE=<…/cf-plugin-out>` (sonst übersprungen).
 */
class ImportedVsCodePluginsTest {
    @Test
    fun `importierte Plugins sind gültig`() {
        val dir = System.getProperty("cf.importedSample")?.let(::File)
        assumeTrue(dir?.isDirectory == true)
        val plugins = dir!!.listFiles { f -> File(f, "plugin.json").isFile }.orEmpty()
        assertTrue(plugins.isNotEmpty())
        for (p in plugins) {
            val m = PluginManifestParser.parse(File(p, "plugin.json").readText()).getOrThrow()
            m.contributes.colorThemes.forEach { assertTrue(it.path, ColorThemeFileParser.parse(File(p, it.path).readText()).isSuccess) }
            m.contributes.iconThemes.forEach { assertTrue(it.path, IconThemeFileParser.parse(File(p, it.path).readText()).isSuccess) }
            (m.contributes.grammars.map { it.path } + m.contributes.snippets.map { it.path }).forEach { assertTrue(it, File(p, it).isFile) }
            println("${m.id}: ${m.contributes.kinds()}")
        }
    }
}
