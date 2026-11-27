package com.codeforge.core.domain.extension

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die generierten Formatierer-/LSP-Plugins unter `tool-plugins/src` müssen mit den echten Parsern gültig sein. */
class ToolPluginsValidTest {
    private val src: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "tool-plugins/src") }.first { it.isDirectory }

    private val manifests: Map<String, PluginManifest> = src.listFiles { f -> f.isDirectory }.orEmpty().associate { dir ->
        dir.name to PluginManifestParser.parse(File(dir, "plugin.json").readText()).getOrThrow()
    }

    @Test
    fun `alle Tool-Plugins sind gueltig, deklarativ und haben Publisher codeforge`() {
        assertEquals(45, manifests.size) // 37 + 8 Sprachpakete
        manifests.forEach { (dirName, m) ->
            assertEquals(dirName, m.id)
            assertEquals("codeforge", m.publisher)
            assertTrue(Regex("""com\.codeforge\.plugin\.[a-z0-9-]+\.(formatter|lsp|bundle|tool|feature)""").matches(m.id))
            val files = File(src, dirName).walkTopDown().filter { it.isFile }.map { it.relativeTo(File(src, dirName)).path }.toList()
            if (m.id.endsWith(".tool")) {
                // Werkzeuge führen Programme aus → im Store nicht erlaubt, nur Sideload
                assertTrue(dirName, DeclarativeOnly.violation(files, m)!!.contains("tools"))
                // Store-Kategorie „tool“: erlaubt, solange kein Plugin-Code/Native-Datei enthalten ist
                assertNull(dirName, DeclarativeOnly.violation(files, m, allowTools = true))
            } else {
                assertNull(dirName, DeclarativeOnly.violation(files, m))
            }
        }
    }

    @Test
    fun `Formatierer und Language Server verwenden keine Skripte oder Fremdpfade`() {
        val lsp = manifests.values.flatMap { it.contributes.lspServers }
        val fmt = manifests.values.flatMap { it.contributes.formatters }
        assertEquals(8, fmt.size)
        assertEquals(18, lsp.size)
        (lsp.map { it.command } + fmt.map { it.command }).forEach { c ->
            assertTrue(c.toString(), !c.first().startsWith("/") && !c.first().contains("sh -c"))
        }
        assertTrue(fmt.all { it.extensions.isNotEmpty() && it.timeoutSeconds in 1..120 })
        assertTrue(lsp.all { it.languages.isNotEmpty() })
    }

    @Test
    fun `Werkzeug-Plugins haben shell-Berechtigung, Skript und python als Voraussetzung`() {
        val tools = manifests.values.filter { it.id.endsWith(".tool") }
        assertEquals(10, tools.size)
        tools.forEach { m ->
            assertTrue(m.id, PluginPermissions.SHELL in m.permissions)
            val dir = File(src, m.id)
            assertTrue(m.id, File(dir, "tool.py").isFile && File(dir, "common.py").isFile)
            m.contributes.tools.forEach { t ->
                assertTrue(t.command.first() == "python3" && t.command.any { it.endsWith("/tool.py") })
                assertTrue(t.timeoutSeconds in 1..900)
            }
            assertTrue(m.contributes.requires.any { it.name == "python" && it.check == "python3" })
        }
    }

    @Test
    fun `Formatierer und Language Server deklarieren erlaubte Voraussetzungen`() {
        val withRequires = manifests.values.filter { it.id.endsWith(".formatter") || it.id.endsWith(".lsp") }
        assertEquals(24, withRequires.size)
        withRequires.forEach { m ->
            assertTrue(m.id, m.contributes.requires.isNotEmpty())
            m.contributes.requires.forEach { assertTrue("${m.id}: ${it.name}", RequirementPolicy.isStoreAllowed(it)) }
        }
        // JVM-Server laden ihre Dateien als geprüften Download und nehmen JVM-Optionen an.
        val downloads = withRequires.filter { m -> m.contributes.requires.any { it.manager == RequirementManager.DOWNLOAD } }
        val servers = downloads.flatMap { it.contributes.lspServers }
        assertEquals(setOf("kotlin-language-server", "jdtls", "lemminx", "kmp-lsp"), servers.map { it.id }.toSet())
        assertEquals(setOf("kotlin-language-server", "jdtls", "lemminx", "kmp-lsp"), servers.filter { it.acceptsJvmOptions }.map { it.id }.toSet())
        // Architektur-abhängige Downloads decken aarch64 und x86_64 ab.
        val kmp = downloads.single { m -> m.id.endsWith("kmp-lsp.lsp") }.contributes.requires
        assertEquals(setOf("aarch64", "x86_64"), kmp.mapNotNull { it.arch }.toSet())
    }

    @Test
    fun debuggerFeaturePlugin() {
        val m = manifests.getValue("com.codeforge.plugin.debugger.feature")
        assertEquals(listOf(AppFeature.DEBUGGER), m.contributes.features)
        assertTrue(m.permissions.isEmpty())
        assertEquals(listOf("features"), m.contributes.kinds())
        val active = ActiveContributions.of(listOf(m to "/plugins/debugger"))
        assertTrue(active.hasFeature(AppFeature.DEBUGGER))
        assertTrue(!ActiveContributions.EMPTY.hasFeature(AppFeature.DEBUGGER))
    }

    @Test
    fun unknownFeaturesIgnored() {
        val m = PluginManifestParser.parse(
            """
                {"id":"a.b.c","name":"x","version":"1.0.0","apiVersion":2,"publisher":"p",
                "contributes":{"features":["debugger","root-access","debugger"]}}
            """.trimIndent()
        ).getOrThrow()
        assertEquals(listOf(AppFeature.DEBUGGER), m.contributes.features)
    }

    @Test
    fun `Bundles verweisen nur auf vorhandene Plugins`() {
        val bundles = manifests.values.filter { it.id.endsWith(".bundle") }
        assertEquals(10, bundles.size) // 2 Sammel-Bundles + 8 Sprachpakete
        bundles.forEach { b ->
            assertTrue(b.contributes.extensionPack.isNotEmpty())
            b.contributes.extensionPack.forEach { assertTrue(it, it in manifests) }
        }
    }
}
