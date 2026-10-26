// Modul: :feature:editor
package com.codeforge.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * sora-editor liest aus `languages.json` den Schlüssel `"languages"`. Mit einem anderen Schlüssel ist die
 * Liste null (NullPointerException in `GrammarRegistry.prepareLoadGrammars`) und es gibt kein Highlighting.
 */
class TextMateManifestTest {

    private val assets: File = listOf(
        File("src/main/assets/textmate"),
        File("feature/editor/src/main/assets/textmate")
    ).first { it.isDirectory }

    private val manifest: String = File(assets, "languages.json").readText()

    @Test
    fun `manifest uses the languages key expected by sora`() {
        assertTrue(manifest.contains("\"languages\""))
        assertFalse(manifest.contains("\"grammars\""))
    }

    @Test
    fun `every referenced file exists and scope names are unique`() {
        val grammarPaths = Regex("\"grammar\"\\s*:\\s*\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        val configPaths = Regex("\"languageConfiguration\"\\s*:\\s*\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
        val scopes = Regex("\"scopeName\"\\s*:\\s*\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()

        assertTrue(grammarPaths.isNotEmpty())
        (grammarPaths + configPaths).forEach { path ->
            // Pfade sind relativ zum Asset-Root, der Ordner `textmate/` liegt im Asset-Verzeichnis.
            assertTrue("Asset fehlt: $path", File(assets.parentFile, path).isFile)
        }
        assertEquals("doppelte scopeName-Einträge", scopes.size, scopes.toSet().size)
    }
}
