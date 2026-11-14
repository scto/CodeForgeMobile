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

    private val plugins = dir.listFiles { f -> f.name.endsWith("-lsp-plugin") && File(f, "plugin.json").isFile }
        .orEmpty()
        .sortedBy { it.name }

    @Test
    fun `alle neun LSP-Plugins haben gueltige Code-Manifeste mit lspServers`() {
        assertEquals(plugins.map { it.name }.toString(), 9, plugins.size)
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
        val serverIds = ms.flatMap { m -> m.contributes.lspServers.map { it.id } }
        assertEquals(serverIds.toString(), serverIds.size, serverIds.toSet().size)
        // Sprachen sind je Plugin eindeutig; kmp-lsp ist bewusst Alternative zu Kotlin-/Java-Plugin (gleiche Sprachen).
        val langs = ms.filter { it.id != "com.example.kmp-lsp" }
            .flatMap { m -> m.contributes.lspServers.flatMap { it.languages } }
        assertEquals(langs.toString(), langs.size, langs.toSet().size)
    }

    @Test
    fun `Websuche-Plugin ist gueltig`() {
        val folder = File(dir, "web-search-plugin")
        val m = PluginManifestParser.parse(File(folder, "plugin.json").readText()).getOrThrow()
        assertEquals(null, m.entryPointClass)
        assertTrue(PluginPermissions.AGENT in m.permissions && PluginPermissions.SHELL in m.permissions)
        assertEquals(listOf("web_search", "web_fetch"), m.contributes.agentTools.map { it.id })
        // Anfragen verlassen das Gerät: Der Agent soll vor jedem Aufruf fragen.
        assertTrue(m.contributes.agentTools.all { it.kind == AgentToolKind.EXECUTE })
        m.contributes.agentTools.forEach { tool ->
            assertEquals(listOf("python3", "\${pluginDir}/websearch.py"), tool.command.take(2))
            assertTrue(tool.command.last() == "\${input}")
        }
        assertTrue(File(folder, "websearch.py").isFile)
        assertEquals(listOf("python"), m.contributes.requires.map { it.name })
        // Agent-Werkzeuge starten Programme: nur per Sideload, nicht über den Store.
        val files = folder.listFiles().orEmpty().map { it.name }
        assertNotNull(DeclarativeOnly.violation(files, m, allowTools = true))
    }
}
