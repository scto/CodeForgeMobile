// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExtensionDependenciesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val noDownload = FileDownloader { _, _, _ -> Result.failure(UnsupportedOperationException()) }

    private fun fakeBinary(dir: File, name: String) {
        File(dir, name).apply { writeText("#!/bin/sh"); setExecutable(true) }
    }

    private fun javaPackage(bytes: ByteArray) = LspExtensionPackage(
        id = "test-java-lsp", nameRes = 0, descriptionRes = 0, version = "1", language = "Java",
        downloadUrl = "https://example.com/x.jar", expectedSha256 = Sha256Utils.calculateSha256(bytes),
        archiveType = ExtensionArchiveType.JAR, executableRelativePath = "x.jar",
        // Fantasienamen: unabhängig davon, was auf dem PATH der Testmaschine liegt
        requires = listOf("zz-fake-jdk", "zz-fake-py")
    )

    @Test
    fun `missing JDK and python are installed before the download`() = runBlocking {
        val bin = tempFolder.newFolder("bin")
        val bytes = "JAR".toByteArray()
        val downloader = FileDownloader { _, dest, _ -> dest.parentFile?.mkdirs(); dest.writeBytes(bytes); Result.success(dest) }
        val manager = ExtensionsManager(tempFolder.newFolder("ext"), downloader, bin)
        val requested = mutableListOf<String>()
        manager.dependencyInstaller = { reqs ->
            requested += reqs.map { it.name }
            fakeBinary(bin, "zz-fake-jdk"); fakeBinary(bin, "zz-fake-py")
            Result.success(Unit)
        }

        val result = manager.install(javaPackage(bytes))

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        assertEquals(listOf("zz-fake-jdk", "zz-fake-py"), requested)
    }

    @Test
    fun `nothing is installed when requirements are already met`() = runBlocking {
        val bin = tempFolder.newFolder("bin2")
        fakeBinary(bin, "zz-fake-jdk"); fakeBinary(bin, "zz-fake-py")
        val bytes = "JAR".toByteArray()
        val downloader = FileDownloader { _, dest, _ -> dest.parentFile?.mkdirs(); dest.writeBytes(bytes); Result.success(dest) }
        val manager = ExtensionsManager(tempFolder.newFolder("ext2"), downloader, bin)
        var called = false
        manager.dependencyInstaller = { called = true; Result.success(Unit) }

        assertTrue(manager.install(javaPackage(bytes)).isSuccess)
        assertFalse(called)
    }

    @Test
    fun `failing dependency install aborts without download`() = runBlocking {
        val bin = tempFolder.newFolder("bin3")
        var downloaded = false
        val downloader = FileDownloader { _, _, _ -> downloaded = true; Result.failure(IllegalStateException()) }
        val manager = ExtensionsManager(tempFolder.newFolder("ext3"), downloader, bin)
        manager.dependencyInstaller = { Result.failure(IllegalStateException("apt kaputt")) }

        val result = manager.install(javaPackage(ByteArray(1)))

        assertTrue(result.isFailure)
        assertFalse(downloaded)
    }

    @Test
    fun `termux package clangd is installed via apt`() = runBlocking {
        val bin = tempFolder.newFolder("bin4")
        val manager = ExtensionsManager(tempFolder.newFolder("ext4"), noDownload, bin)
        manager.dependencyInstaller = { fakeBinary(bin, "clangd"); Result.success(Unit) }

        val result = manager.install(LspExtensionCatalog.clangd)

        assertTrue(result.exceptionOrNull()?.toString(), result.isSuccess)
        assertEquals("clangd", result.getOrThrow().name)
    }

    @Test
    fun `termux package without installer keeps the terminal hint`() = runBlocking {
        val manager = ExtensionsManager(tempFolder.newFolder("ext5"), noDownload, tempFolder.newFolder("bin5"))
        assertTrue(manager.install(LspExtensionCatalog.taplo).isFailure)
    }

    @Test
    fun `install list merges nodejs and npm and maps names`() {
        val list = LspDependencies.toInstallList(listOf("nodejs", "npm", "python3", "java"))
        assertEquals(listOf("nodejs", "python", "openjdk-17"), list.map { it.name })
        assertEquals("node", list[0].check)
    }

    @Test
    fun `catalog servers declare their runtimes`() {
        assertTrue(LspExtensionCatalog.kotlinLanguageServer.requires.contains("openjdk-17"))
        assertTrue(LspExtensionCatalog.jdtls.requires.containsAll(listOf("openjdk-17", "python")))
        assertTrue(LspExtensionCatalog.lemminx.requires.contains("openjdk-17"))
        assertTrue(LspExtensionCatalog.clangd.requires.contains("clang"))
    }
}
