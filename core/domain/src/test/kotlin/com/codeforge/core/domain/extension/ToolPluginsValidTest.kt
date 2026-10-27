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
        assertEquals(20, manifests.size)
        manifests.forEach { (dirName, m) ->
            assertEquals(dirName, m.id)
            assertEquals("codeforge", m.publisher)
            assertTrue(Regex("com\\.codeforge\\.plugin\\.[a-z0-9-]+\\.(formatter|lsp|bundle)").matches(m.id))
            val files = File(src, dirName).walkTopDown().filter { it.isFile }.map { it.relativeTo(File(src, dirName)).path }.toList()
            assertNull(dirName, DeclarativeOnly.violation(files, m))
        }
    }

    @Test
    fun `Formatierer und Language Server verwenden keine Skripte oder Fremdpfade`() {
        val lsp = manifests.values.flatMap { it.contributes.lspServers }
        val fmt = manifests.values.flatMap { it.contributes.formatters }
        assertEquals(6, fmt.size)
        assertEquals(14, lsp.size)
        (lsp.map { it.command } + fmt.map { it.command }).forEach { c ->
            assertTrue(c.toString(), !c.first().startsWith("/") && !c.first().contains("sh -c"))
        }
        assertTrue(fmt.all { it.extensions.isNotEmpty() && it.timeoutSeconds in 1..120 })
        assertTrue(lsp.all { it.languages.isNotEmpty() })
    }

    @Test
    fun `Bundles verweisen nur auf vorhandene Plugins`() {
        val bundles = manifests.values.filter { it.id.endsWith(".bundle") }
        assertEquals(2, bundles.size)
        bundles.forEach { b ->
            assertTrue(b.contributes.extensionPack.isNotEmpty())
            b.contributes.extensionPack.forEach { assertTrue(it, it in manifests) }
        }
    }
}
