package com.codeforge.core.domain.extension

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die LSP-Beispiel-Plugins unter `examples/` müssen mit den echten Parsern installierbar (Sideload) bleiben. */
class ExamplePluginsValidTest {
    private val dir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "examples") }.first { it.isDirectory }

    private val plugins = dir.listFiles { f -> File(f, "plugin.json").isFile }.orEmpty().sortedBy { it.name }

    @Test
    fun `alle acht LSP-Plugins haben gueltige Code-Manifeste mit lspServers`() {
        assertEquals(plugins.map { it.name }.toString(), 8, plugins.size)
        plugins.forEach { folder ->
            val m = PluginManifestParser.parse(File(folder, "plugin.json").readText()).getOrThrow()
            assertEquals(folder.name, 2, m.apiVersion)
            assertNotNull(folder.name, m.entryPointClass)
            assertTrue(folder.name, PluginPermissions.SHELL in m.permissions)
            assertTrue(folder.name, m.contributes.lspServers.isNotEmpty())
            // Code-Plugins sind Sideload-only: der Store muss sie ablehnen.
            assertTrue(folder.name, DeclarativeOnly.violation(listOf("plugin.json", "classes.dex"), m) != null)
        }
    }

    @Test
    fun `Plugin-IDs und Sprachen sind eindeutig`() {
        val ms = plugins.map { PluginManifestParser.parse(File(it, "plugin.json").readText()).getOrThrow() }
        assertEquals(ms.size, ms.map { it.id }.toSet().size)
        val langs = ms.flatMap { m -> m.contributes.lspServers.flatMap { it.languages } }
        assertEquals(langs.toString(), langs.size, langs.toSet().size)
    }
}
