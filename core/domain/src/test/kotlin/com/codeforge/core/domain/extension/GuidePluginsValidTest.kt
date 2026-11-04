package com.codeforge.core.domain.extension

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Beispiel-Plugins des Plugin-Leitfadens (`examples/guide/`, docs/dev/PluginDevelopment.md) müssen mit den echten Parsern gültig bleiben. */
class GuidePluginsValidTest {
    private val dir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "examples/guide") }.first { it.isDirectory }

    private fun manifest(name: String) = PluginManifestParser.parse(File(dir, "$name/plugin.json").readText()).getOrThrow()
    private fun files(name: String) = File(dir, name).walkTopDown()
        .filter { it.isFile && !it.relativeTo(File(dir, name)).path.startsWith("build") && !it.name.endsWith(".kts") && !it.path.contains("/src/") }
        .map { it.relativeTo(File(dir, name)).path }.toList()

    @Test
    fun `nord-night ist ein gueltiges Farbschema und Store-tauglich`() {
        val m = manifest("nord-night")
        val c = m.contributes.colorThemes.single()
        val theme = File(dir, "nord-night/${c.path}")
        assertTrue(ColorThemeFileParser.parse(theme.readText()).isSuccess)
        assertTrue(File(dir, "nord-night/themes/syntax/nord-night-tm.json").isFile)
        assertNull(DeclarativeOnly.violation(files("nord-night"), m))
    }

    @Test
    fun `zig-support enthaelt Grammatik Snippets Formatierer Server und Voraussetzungen`() {
        val m = manifest("zig-support")
        val c = m.contributes
        assertEquals(1, c.grammars.size)
        assertEquals(1, c.snippets.size)
        assertEquals(1, c.formatters.size)
        assertEquals(1, c.lspServers.size)
        assertEquals(2, c.requires.size)
        (c.grammars.map { it.path } + c.snippets.map { it.path }).forEach { assertTrue(it, File(dir, "zig-support/$it").isFile) }
        // zig/zls stehen nicht auf der Store-Freigabeliste (RequirementPolicy): nur per Sideload installierbar.
        assertTrue(DeclarativeOnly.violation(files("zig-support"), m)!!.contains("Freigabeliste"))
    }

    @Test
    fun `todo-report ist ein Werkzeug mit shell-Berechtigung und nur mit allowTools zulaessig`() {
        val m = manifest("todo-report")
        assertTrue(PluginPermissions.SHELL in m.permissions)
        assertEquals(4, m.contributes.tools.single().prompt!!.options.size)
        assertNotNull(DeclarativeOnly.violation(files("todo-report"), m))
        assertNull(DeclarativeOnly.violation(files("todo-report"), m, allowTools = true))
    }

    @Test
    fun `commit-notes ist ein Code-Plugin mit minimalen Berechtigungen und nur per Sideload`() {
        val m = manifest("commit-notes")
        assertEquals(setOf("commands", "menus", "git-events"), m.permissions)
        assertNotNull(m.entryPointClass)
        assertNotNull(DeclarativeOnly.violation(listOf("plugin.json", "classes.dex"), m))
    }
}
