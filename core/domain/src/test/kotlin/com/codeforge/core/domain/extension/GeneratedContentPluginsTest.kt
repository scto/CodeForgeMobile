package com.codeforge.core.domain.extension

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Prüft die von `content-plugins/generate.py` und `icon-plugins/generate.py` erzeugten Pakete mit den echten Parsern.
 * Ordner per Umgebungsvariable `CF_GENERATED_DIRS` (durch `:` getrennt, jeweils mit `src/<id>/`); ohne Angabe wird übersprungen.
 */
class GeneratedContentPluginsTest {
    private val dirs: List<File> = System.getenv("CF_GENERATED_DIRS").orEmpty().split(':').filter { it.isNotBlank() }
        .flatMap { File(it, "src").listFiles { f -> f.isDirectory }.orEmpty().toList() }

    @Test
    fun `alle erzeugten Plugins sind gueltig und deklarativ`() {
        assumeTrue(dirs.isNotEmpty())
        dirs.forEach { dir ->
            val m = PluginManifestParser.parse(File(dir, "plugin.json").readText()).getOrThrow()
            assertEquals(dir.name, m.id)
            val files = dir.walkTopDown().filter { it.isFile }.map { it.relativeTo(dir).path }.toList()
            assertNull(dir.name, DeclarativeOnly.violation(files, m))
            m.contributes.iconThemes.forEach {
                val t = IconThemeFileParser.parse(File(dir, it.path).readText()).getOrThrow()
                t.allPaths().forEach { p -> assertTrue("${dir.name}: $p fehlt", File(File(dir, it.path).parentFile, p).isFile) }
            }
            m.contributes.grammars.forEach { assertTrue("${dir.name}: Grammatik fehlt", File(dir, it.path).isFile) }
            m.contributes.snippets.forEach {
                val text = File(dir, it.path).readText()
                assertTrue("${dir.name}: keine Snippets", ExtensionSnippets.fromText(Resolved(m.id, dir.path, it), text).isNotEmpty())
            }
            m.contributes.languagePacks.forEach {
                assertTrue("${dir.name}: Sprachdatei", LanguagePackFileParser.parse(File(dir, it.path).readText()).getOrThrow().isNotEmpty())
            }
        }
    }
}
