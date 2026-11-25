package com.codeforge.feature.settings.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemInfoTest {
    private val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "gradle/libs.versions.toml").isFile }

    @Test
    fun libraryVersionsMatchBuild() {
        val catalog = File(root, "gradle/libs.versions.toml").readText()
        assertTrue(catalog.contains("treesitter = \"${LibraryVersions.TREE_SITTER}\""))
        assertTrue(catalog.contains("composeBom = \"${LibraryVersions.COMPOSE_BOM}\""))
        val editor = File(root, "feature/editor/build.gradle.kts").readText()
        assertTrue(editor.contains("sora-editor:editor:${LibraryVersions.SORA_EDITOR}\""))
    }

    @Test
    fun formatsBytesAndGl() {
        assertEquals("512 MB", InfoFormat.bytes(512L * 1024 * 1024))
        assertEquals("15.8 GB", InfoFormat.bytes((15.8 * 1024 * 1024 * 1024).toLong()))
        assertEquals("3.2", InfoFormat.glEs(0x30002))
    }

    @Test
    fun reportListsSections() {
        val sections = listOf(InfoSection(InfoSectionKind.DEVICE, listOf(InfoItem(1, "Google"))))
        assertEquals("## Gerät\n- Hersteller: Google", InfoFormat.report(sections, { "Gerät" }, { "Hersteller" }))
    }
}
