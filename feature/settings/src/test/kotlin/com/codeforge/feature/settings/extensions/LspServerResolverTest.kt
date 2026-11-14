// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import com.codeforge.core.domain.extension.ActiveContributions
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.LspServerContribution
import com.codeforge.core.domain.extension.PluginManifestParser
import com.codeforge.core.domain.extension.Resolved
import com.codeforge.core.domain.model.JvmOptions
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val toolPlugins = File("../../tool-plugins/src")

    private fun repoOf(vararg servers: Resolved<LspServerContribution>) = object : ContributionRepository {
        override val contributions = MutableStateFlow(ActiveContributions(lspServers = servers.toList()))
    }

    // Contributions der echten Plugins aus tool-plugins/src.
    private fun toolPluginRepo(vararg names: String): ContributionRepository {
        val entries = names.map { name ->
            val dir = File(toolPlugins, "com.codeforge.plugin.$name.lsp")
            PluginManifestParser.parse(File(dir, "plugin.json").readText()).getOrThrow() to dir.absolutePath
        }
        return object : ContributionRepository {
            override val contributions = MutableStateFlow(ActiveContributions.of(entries))
        }
    }

    private fun resolver(repo: ContributionRepository, settings: FakeLspSettings = FakeLspSettings(), deps: File = tempFolder.root) =
        LspServerResolverImpl(repo, settings, "/home", deps) { mapOf("PATH" to "/usr/bin", "KOTLIN_LANGUAGE_SERVER_OPTS" to "-Duser=1") }

    @Test
    fun pluginServerIsResolved() = runBlocking {
        val pluginDir = tempFolder.newFolder("plugin_zig")
        val exe = File(pluginDir, "zls").apply { writeText("#!/bin/sh") }
        val repo = repoOf(Resolved("p.zig", pluginDir.absolutePath, LspServerContribution("zls", "ZLS", listOf("zig"), listOf("${'$'}{pluginDir}/zls", "--stdio"))))
        val launch = resolver(repo).resolve("zig")
        assertNotNull(launch)
        assertEquals(listOf(exe.absolutePath, "--stdio"), launch!!.command)
        assertEquals("zls", launch.serverId)
        assertNull("andere Sprache", resolver(repo).resolve("pascal"))
    }

    @Test
    fun missingFileGivesNull() = runBlocking {
        val repo = repoOf(Resolved("p", "/nonexistent", LspServerContribution("x", "X", listOf("zig"), listOf("${'$'}{pluginDir}/missing"))))
        assertNull(resolver(repo).resolve("zig"))
    }

    @Test
    fun noPluginNoServer() = runBlocking {
        assertNull(resolver(repoOf()).resolve("kotlin"))
    }

    @Test
    fun lemminxJvmAndSwitch() = runBlocking {
        val repo = toolPluginRepo("lemminx")
        val settings = FakeLspSettings()
        assertNull("Download fehlt", resolver(repo, settings).resolve("xml"))

        File(tempFolder.root, "lemminx").apply { mkdirs() }.resolve("org.eclipse.lemminx-uber.jar").writeText("jar")
        val default = resolver(repo, settings).resolve("xml")!!.command
        assertEquals("java", default.first())
        assertTrue(default.indexOf("-Xmx256m") in 1 until default.indexOf("-jar"))

        settings.update("lemminx") { it.copy(jvm = JvmOptions(xmx = "512m", extraFlags = "-Dx=1 ;bad")) }
        val custom = resolver(repo, settings).resolve("xml")!!.command
        assertTrue(custom.contains("-Xmx512m"))
        assertTrue(custom.contains("-Dx=1"))
        assertTrue(custom.none { it.contains(";") })

        settings.update("lemminx") { it.copy(enabled = false) }
        assertNull(resolver(repo, settings).resolve("xml"))
    }

    @Test
    fun jdtlsGetsJvmArgFlags() = runBlocking {
        File(tempFolder.root, "jdtls/bin").apply { mkdirs() }.resolve("jdtls").writeText("#!/usr/bin/env python3")
        val command = resolver(toolPluginRepo("jdtls")).resolve("java")!!.command
        assertEquals("python3", command.first())
        assertTrue(command.contains("--jvm-arg=-XX:+UseSerialGC"))
    }

    @Test
    fun kotlinLsGetsJvmViaEnv() = runBlocking {
        File(tempFolder.root, "kotlin-language-server/server/bin")
            .apply { mkdirs() }
            .resolve("kotlin-language-server")
            .writeText("#!/bin/sh")
        val launch = resolver(toolPluginRepo("kotlin-language-server")).resolve("kotlin")!!
        assertEquals(1, launch.command.size)
        val opts = launch.env.getValue("KOTLIN_LANGUAGE_SERVER_OPTS")
        assertTrue(opts, opts.startsWith("-Duser=1 ") && opts.contains("-XX:+UseSerialGC"))
    }

    @Test
    fun webServersShareSwitch() = runBlocking {
        val settings = FakeLspSettings()
        val repo = toolPluginRepo("web-langservers")
        assertEquals("vscode-css-language-server", resolver(repo, settings).resolve("css")!!.serverId)
        settings.update("vscode-langservers-extracted") { it.copy(enabled = false) }
        assertNull(resolver(repo, settings).resolve("css"))
        assertNull(resolver(repo, settings).resolve("json"))
    }
}
