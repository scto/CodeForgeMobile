// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LspServerResolverTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `resolve returns null for unknown language`() = runBlocking {
        val baseDir = tempFolder.newFolder("extensions_root")
        val dummyDownloader = FileDownloader { _, _, _ -> Result.failure(UnsupportedOperationException()) }
        val manager = ExtensionsManager(baseDir = baseDir, downloader = dummyDownloader)
        val resolver = LspServerResolverImpl(manager)

        val result = resolver.resolve("unknown-language-xyz")
        assertNull(result)
    }

    @Test
    fun `resolve returns null when extension is not installed`() = runBlocking {
        val baseDir = tempFolder.newFolder("extensions_root_empty")
        val dummyDownloader = FileDownloader { _, _, _ -> Result.failure(UnsupportedOperationException()) }
        val manager = ExtensionsManager(baseDir = baseDir, downloader = dummyDownloader)
        val resolver = LspServerResolverImpl(manager)

        assertNull("Uninstalled lemminx should return null", resolver.resolve("xml"))
        assertNull("Uninstalled yaml-ls should return null", resolver.resolve("yaml"))
        assertNull("Uninstalled taplo should return null", resolver.resolve("toml"))
        assertNull("Uninstalled bash-ls should return null", resolver.resolve("shellscript"))
    }

    @Test
    fun `resolve returns expected launch commands for installed extensions`() = runBlocking {
        val baseDir = tempFolder.newFolder("extensions_root_installed")
        val fakeBin = tempFolder.newFolder("fake_termux_bin")

        // 1. Mock lemminx installed
        val lemminxDir = File(baseDir, "lemminx").apply { mkdirs() }
        val lemminxJar = File(lemminxDir, "org.eclipse.lemminx-uber.jar").apply {
            writeText("dummy jar content")
        }

        // 2. Mock yaml-language-server installed
        val yamlDir = File(baseDir, "yaml-language-server/package/bin").apply { mkdirs() }
        val yamlScript = File(yamlDir, "yaml-language-server").apply {
            writeText("#!/usr/bin/env node")
        }

        // 3. Mock taplo installed in Termux bin
        val taploBin = File(fakeBin, "taplo").apply {
            writeText("#!/bin/sh")
            setExecutable(true)
        }

        // 4. Mock bash-language-server installed
        val bashDir = File(baseDir, "bash-language-server/package/out").apply { mkdirs() }
        val bashScript = File(bashDir, "cli.js").apply {
            writeText("console.log('bashls');")
        }

        val dummyDownloader = FileDownloader { _, _, _ -> Result.failure(UnsupportedOperationException()) }
        val manager = ExtensionsManager(
            baseDir = baseDir,
            downloader = dummyDownloader,
            customTermuxBinDir = fakeBin
        )
        val resolver = LspServerResolverImpl(manager)

        // XML -> lemminx
        val xmlLaunch = resolver.resolve("xml")
        assertNotNull("XML launch must not be null", xmlLaunch)
        assertEquals("lemminx", xmlLaunch?.serverId)
        assertTrue(xmlLaunch!!.command.last().endsWith("org.eclipse.lemminx-uber.jar"))
        assertTrue(xmlLaunch.command.contains("-jar"))

        // YAML -> yaml-language-server
        val yamlLaunch = resolver.resolve("yaml")
        assertNotNull("YAML launch must not be null", yamlLaunch)
        assertEquals("yaml-language-server", yamlLaunch?.serverId)
        assertEquals(listOf("node", yamlScript.absolutePath, "--stdio"), yamlLaunch!!.command)

        val ymlLaunch = resolver.resolve("yml")
        assertNotNull("YML launch must not be null", ymlLaunch)
        assertEquals("yaml-language-server", ymlLaunch?.serverId)

        // TOML -> taplo
        val tomlLaunch = resolver.resolve("toml")
        assertNotNull("TOML launch must not be null", tomlLaunch)
        assertEquals("taplo", tomlLaunch?.serverId)
        assertEquals(listOf(taploBin.absolutePath, "lsp", "stdio"), tomlLaunch!!.command)

        // Shell / Bash -> bash-language-server
        val bashLaunch = resolver.resolve("bash")
        assertNotNull("Bash launch must not be null", bashLaunch)
        assertEquals("bash-language-server", bashLaunch?.serverId)
        assertEquals(listOf("node", bashScript.absolutePath, "start"), bashLaunch!!.command)

        val shLaunch = resolver.resolve("shellscript")
        assertNotNull("Shellscript launch must not be null", shLaunch)
        assertEquals("bash-language-server", shLaunch?.serverId)
    }
}
