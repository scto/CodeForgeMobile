// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionsManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `install and uninstall lifecycle with ZIP archive`() = runBlocking {
        val baseDir = tempFolder.newFolder("extensions_root_zip")

        val mockArchiveBytes = createMockZip("bin/test-server", "#!/bin/sh\necho running zip")
        val correctHash = Sha256Utils.calculateSha256(mockArchiveBytes)

        val testPackage = LspExtensionPackage(
            id = "test-lsp-zip",
            nameRes = 0,
            descriptionRes = 0,
            version = "1.0.0",
            language = "TestLang",
            downloadUrl = "https://example.com/test-server.zip",
            expectedSha256 = correctHash,
            archiveType = ExtensionArchiveType.ZIP,
            executableRelativePath = "bin/test-server"
        )

        val mockDownloader = FileDownloader { _, dest, onProgress ->
            dest.parentFile?.mkdirs()
            dest.writeBytes(mockArchiveBytes)
            onProgress(mockArchiveBytes.size.toLong(), mockArchiveBytes.size.toLong())
            Result.success(dest)
        }

        val manager = ExtensionsManager(baseDir = baseDir, downloader = mockDownloader)

        assertFalse(manager.isInstalled(testPackage))

        val installResult = manager.install(testPackage)
        assertTrue("Install should succeed", installResult.isSuccess)
        val executable = installResult.getOrThrow()
        assertTrue("Executable must exist", executable.exists())
        assertEquals("#!/bin/sh\necho running zip", executable.readText())

        assertTrue(manager.isInstalled(testPackage))
        val resolvedExec = manager.getExecutableFile(testPackage)
        assertNotNull(resolvedExec)
        assertEquals(executable.canonicalPath, resolvedExec?.canonicalPath)

        val uninstallResult = manager.uninstall(testPackage)
        assertTrue("Uninstall should succeed", uninstallResult.isSuccess)
        assertFalse(manager.isInstalled(testPackage))
        assertFalse(executable.exists())
    }

    @Test
    fun `install and uninstall lifecycle with TAR_GZ archive`() = runBlocking {
        val baseDir = tempFolder.newFolder("extensions_root_targz")

        val mockTarGzBytes = createMockTarGz("bin/targz-server", "#!/bin/sh\necho running targz")
        val correctHash = Sha256Utils.calculateSha256(mockTarGzBytes)

        val testPackage = LspExtensionPackage(
            id = "test-lsp-targz",
            nameRes = 0,
            descriptionRes = 0,
            version = "1.0.0",
            language = "TestLang",
            downloadUrl = "https://example.com/test-server.tar.gz",
            expectedSha256 = correctHash,
            archiveType = ExtensionArchiveType.TAR_GZ,
            executableRelativePath = "bin/targz-server"
        )

        val mockDownloader = FileDownloader { _, dest, onProgress ->
            dest.parentFile?.mkdirs()
            dest.writeBytes(mockTarGzBytes)
            onProgress(mockTarGzBytes.size.toLong(), mockTarGzBytes.size.toLong())
            Result.success(dest)
        }

        val manager = ExtensionsManager(baseDir = baseDir, downloader = mockDownloader)

        assertFalse(manager.isInstalled(testPackage))

        val installResult = manager.install(testPackage)
        assertTrue("Install should succeed for tar.gz", installResult.isSuccess)
        val executable = installResult.getOrThrow()
        assertTrue(executable.exists())
        assertEquals("#!/bin/sh\necho running targz", executable.readText())

        assertTrue(manager.isInstalled(testPackage))

        val uninstallResult = manager.uninstall(testPackage)
        assertTrue("Uninstall should succeed", uninstallResult.isSuccess)
        assertFalse(manager.isInstalled(testPackage))
    }

    @Test
    fun `install and uninstall lifecycle with GZ raw binary`() = runBlocking {
        val baseDir = tempFolder.newFolder("extensions_root_gz")

        val rawContent = "RAW_BINARY_DATA"
        val mockGzBytes = createMockGz(rawContent)
        val correctHash = Sha256Utils.calculateSha256(mockGzBytes)

        val testPackage = LspExtensionPackage(
            id = "test-lsp-gz",
            nameRes = 0,
            descriptionRes = 0,
            version = "1.0.0",
            language = "TestLang",
            downloadUrl = "https://example.com/test-binary.gz",
            expectedSha256 = correctHash,
            archiveType = ExtensionArchiveType.GZ,
            executableRelativePath = "test-binary"
        )

        val mockDownloader = FileDownloader { _, dest, onProgress ->
            dest.parentFile?.mkdirs()
            dest.writeBytes(mockGzBytes)
            onProgress(mockGzBytes.size.toLong(), mockGzBytes.size.toLong())
            Result.success(dest)
        }

        val manager = ExtensionsManager(baseDir = baseDir, downloader = mockDownloader)

        assertFalse(manager.isInstalled(testPackage))

        val installResult = manager.install(testPackage)
        assertTrue("Install should succeed for gz", installResult.isSuccess)
        val executable = installResult.getOrThrow()
        assertTrue(executable.exists())
        assertEquals(rawContent, executable.readText())

        assertTrue(manager.isInstalled(testPackage))
        manager.uninstall(testPackage)
        assertFalse(manager.isInstalled(testPackage))
    }

    @Test
    fun `install fails if SHA-256 mismatch occurs and cleans up archive and directory`() = runBlocking {
        val baseDir = tempFolder.newFolder("extensions_root_mismatch")
        val mockArchiveBytes = createMockZip("bin/bad", "bad content")

        val testPackage = LspExtensionPackage(
            id = "mismatch-lsp",
            nameRes = 0,
            descriptionRes = 0,
            version = "1.0.0",
            language = "TestLang",
            downloadUrl = "https://example.com/mismatch.zip",
            expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
            archiveType = ExtensionArchiveType.ZIP,
            executableRelativePath = "bin/bad"
        )

        var destinationFileCreated: File? = null
        val mockDownloader = FileDownloader { _, dest, _ ->
            dest.parentFile?.mkdirs()
            dest.writeBytes(mockArchiveBytes)
            destinationFileCreated = dest
            Result.success(dest)
        }

        val manager = ExtensionsManager(baseDir = baseDir, downloader = mockDownloader)
        val result = manager.install(testPackage)

        assertTrue("Install should fail due to SHA-256 mismatch", result.isFailure)
        assertFalse("Package must not be marked as installed", manager.isInstalled(testPackage))
        assertFalse("Archive file must be cleaned up on failure", destinationFileCreated?.exists() == true)
        assertFalse("Extension directory must not exist on failure", manager.getExtensionDirectory(testPackage).exists())
    }

    @Test
    fun `termux package detection and requirements check`() {
        val baseDir = tempFolder.newFolder("extensions_root_termux")
        val fakeTermuxBin = tempFolder.newFolder("fake_termux_bin")

        val clangdFile = File(fakeTermuxBin, "clangd")
        clangdFile.writeText("#!/bin/sh")
        clangdFile.setExecutable(true)

        val dummyDownloader = FileDownloader { _, _, _ -> Result.failure(UnsupportedOperationException()) }
        val manager = ExtensionsManager(
            baseDir = baseDir,
            downloader = dummyDownloader,
            customTermuxBinDir = fakeTermuxBin
        )

        // clangd is installed in fakeTermuxBin
        val clangdPkg = LspExtensionCatalog.clangd
        assertTrue("clangd should be detected as installed", manager.isInstalled(clangdPkg))
        assertEquals(clangdFile.canonicalPath, manager.getExecutableFile(clangdPkg)?.canonicalPath)

        // rust-analyzer is not in fakeTermuxBin
        val rustPkg = LspExtensionCatalog.rustAnalyzer
        assertFalse("rust-analyzer should not be detected as installed", manager.isInstalled(rustPkg))

        // missing requirements check
        val missingForRust = manager.checkMissingRequirements(rustPkg)
        assertTrue("Missing requirements should contain rust-analyzer", missingForRust.contains("rust-analyzer"))

        // taplo check
        val taploPkg = LspExtensionCatalog.taplo
        assertFalse("taplo should not be detected as installed initially", manager.isInstalled(taploPkg))
        val taploFile = File(fakeTermuxBin, "taplo")
        taploFile.writeText("#!/bin/sh")
        taploFile.setExecutable(true)
        assertTrue("taplo should be detected as installed once binary exists", manager.isInstalled(taploPkg))
    }

    @Test
    fun `install and uninstall lifecycle with JAR archive`() = runBlocking {
        val baseDir = tempFolder.newFolder("extensions_root_jar")
        val mockJarBytes = "DUMMY_JAR_BINARY_BYTES".toByteArray(Charsets.UTF_8)
        val correctHash = Sha256Utils.calculateSha256(mockJarBytes)

        val testPackage = LspExtensionPackage(
            id = "test-lemminx",
            nameRes = 0,
            descriptionRes = 0,
            version = "0.31.2",
            language = "XML",
            downloadUrl = "https://example.com/org.eclipse.lemminx-uber.jar",
            expectedSha256 = correctHash,
            archiveType = ExtensionArchiveType.JAR,
            executableRelativePath = "org.eclipse.lemminx-uber.jar"
        )

        val mockDownloader = FileDownloader { _, dest, onProgress ->
            dest.parentFile?.mkdirs()
            dest.writeBytes(mockJarBytes)
            onProgress(mockJarBytes.size.toLong(), mockJarBytes.size.toLong())
            Result.success(dest)
        }

        val manager = ExtensionsManager(baseDir = baseDir, downloader = mockDownloader)
        assertFalse(manager.isInstalled(testPackage))

        val installResult = manager.install(testPackage)
        assertTrue("Install should succeed for JAR", installResult.isSuccess)
        val executable = installResult.getOrThrow()
        assertTrue(executable.exists())
        assertEquals("DUMMY_JAR_BINARY_BYTES", executable.readText())
        assertTrue(manager.isInstalled(testPackage))

        val uninstallResult = manager.uninstall(testPackage)
        assertTrue(uninstallResult.isSuccess)
        assertFalse(manager.isInstalled(testPackage))
    }

    @Test
    fun `install with needsNpmInstall invokes runner and transitions through INSTALLING_DEPENDENCIES`() = runBlocking {
        val baseDir = tempFolder.newFolder("extensions_root_npm")
        val mockTarGzBytes = createMockTarGz("package/bin/my-server", "#!/usr/bin/env node")
        val correctHash = Sha256Utils.calculateSha256(mockTarGzBytes)

        val testPackage = LspExtensionPackage(
            id = "test-npm-pkg",
            nameRes = 0,
            descriptionRes = 0,
            version = "1.0.0",
            language = "TestLang",
            downloadUrl = "https://example.com/test-npm.tar.gz",
            expectedSha256 = correctHash,
            archiveType = ExtensionArchiveType.TAR_GZ,
            executableRelativePath = "package/bin/my-server",
            needsNpmInstall = true
        )

        val mockDownloader = FileDownloader { _, dest, onProgress ->
            dest.parentFile?.mkdirs()
            dest.writeBytes(mockTarGzBytes)
            onProgress(mockTarGzBytes.size.toLong(), mockTarGzBytes.size.toLong())
            Result.success(dest)
        }

        val manager = ExtensionsManager(baseDir = baseDir, downloader = mockDownloader)
        var npmInvoked = false
        manager.customNpmRunner = { pkgDir ->
            npmInvoked = true
            assertTrue("Package dir must exist", pkgDir.exists())
            Result.success(Unit)
        }

        val statuses = mutableListOf<ExtensionStatus>()
        val result = manager.install(testPackage) { _, status ->
            statuses.add(status)
        }

        assertTrue("Install must succeed", result.isSuccess)
        assertTrue("npm runner must have been invoked", npmInvoked)
        assertTrue("Status list must contain INSTALLING_DEPENDENCIES", statuses.contains(ExtensionStatus.INSTALLING_DEPENDENCIES))
        assertTrue("Package must be installed", manager.isInstalled(testPackage))
    }

    @Test
    fun `install with needsNpmInstall cleans up on npm failure`() = runBlocking {
        val baseDir = tempFolder.newFolder("extensions_root_npm_fail")
        val mockTarGzBytes = createMockTarGz("package/bin/my-server", "#!/usr/bin/env node")
        val correctHash = Sha256Utils.calculateSha256(mockTarGzBytes)

        val testPackage = LspExtensionPackage(
            id = "test-npm-pkg-fail",
            nameRes = 0,
            descriptionRes = 0,
            version = "1.0.0",
            language = "TestLang",
            downloadUrl = "https://example.com/test-npm-fail.tar.gz",
            expectedSha256 = correctHash,
            archiveType = ExtensionArchiveType.TAR_GZ,
            executableRelativePath = "package/bin/my-server",
            needsNpmInstall = true
        )

        val mockDownloader = FileDownloader { _, dest, _ ->
            dest.parentFile?.mkdirs()
            dest.writeBytes(mockTarGzBytes)
            Result.success(dest)
        }

        val manager = ExtensionsManager(baseDir = baseDir, downloader = mockDownloader)
        manager.customNpmRunner = {
            Result.failure(IllegalStateException("npm error simulated"))
        }

        val result = manager.install(testPackage)
        assertTrue("Install must fail", result.isFailure)
        assertFalse("Package must not be installed", manager.isInstalled(testPackage))
        assertFalse("Extension dir must be cleaned up", manager.getExtensionDirectory(testPackage).exists())
    }

    @Test
    fun `optional requirement is detected correctly`() {
        val baseDir = tempFolder.newFolder("extensions_root_opt")
        val fakeBin = tempFolder.newFolder("fake_bin")

        val dummyDownloader = FileDownloader { _, _, _ -> Result.failure(UnsupportedOperationException()) }
        val manager = ExtensionsManager(baseDir = baseDir, downloader = dummyDownloader, customTermuxBinDir = fakeBin)

        val testPkg = LspExtensionPackage(
            id = "custom-test-pkg",
            nameRes = 0,
            descriptionRes = 0,
            version = "1.0.0",
            language = "Test",
            downloadUrl = "https://example.com/test.zip",
            expectedSha256 = "1".repeat(64),
            archiveType = ExtensionArchiveType.ZIP,
            executableRelativePath = "bin/server",
            optionalRequires = listOf("my-nonexistent-linter")
        )

        val missingBefore = manager.checkMissingOptionalRequirements(testPkg)
        assertTrue("my-nonexistent-linter should be in missing optional requirements", missingBefore.contains("my-nonexistent-linter"))

        val toolFile = File(fakeBin, "my-nonexistent-linter").apply {
            writeText("#!/bin/sh")
            setExecutable(true)
        }
        val missingAfter = manager.checkMissingOptionalRequirements(testPkg)
        assertFalse("my-nonexistent-linter should not be missing after creation", missingAfter.contains("my-nonexistent-linter"))
    }

    private fun createMockZip(entryName: String, content: String): ByteArray {
        val byteOut = ByteArrayOutputStream()
        ZipOutputStream(byteOut).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(content.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return byteOut.toByteArray()
    }

    private fun createMockGz(content: String): ByteArray {
        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { gz ->
            gz.write(content.toByteArray(Charsets.UTF_8))
        }
        return byteOut.toByteArray()
    }

    private fun createMockTarGz(entryName: String, content: String): ByteArray {
        val rawTarOut = ByteArrayOutputStream()
        val data = content.toByteArray(Charsets.UTF_8)

        val header = ByteArray(512)
        val nameBytes = entryName.toByteArray(Charsets.UTF_8)
        System.arraycopy(nameBytes, 0, header, 0, minOf(nameBytes.size, 100))
        System.arraycopy("0000755".toByteArray(Charsets.US_ASCII), 0, header, 100, 7)
        System.arraycopy("0000000".toByteArray(Charsets.US_ASCII), 0, header, 108, 7)
        System.arraycopy("0000000".toByteArray(Charsets.US_ASCII), 0, header, 116, 7)
        val sizeStr = "%011o".format(data.size.toLong())
        System.arraycopy(sizeStr.toByteArray(Charsets.US_ASCII), 0, header, 124, 11)
        System.arraycopy("00000000000".toByteArray(Charsets.US_ASCII), 0, header, 136, 11)
        for (i in 148..155) header[i] = ' '.code.toByte()
        header[156] = '0'.code.toByte()
        System.arraycopy("ustar  ".toByteArray(Charsets.US_ASCII), 0, header, 257, 7)

        var sum = 0
        for (b in header) sum += (b.toInt() and 0xFF)
        val chkStr = "%06o\u0000 ".format(sum)
        System.arraycopy(chkStr.toByteArray(Charsets.US_ASCII), 0, header, 148, 8)

        rawTarOut.write(header)
        rawTarOut.write(data)
        val pad = (512 - (data.size % 512)) % 512
        if (pad > 0) rawTarOut.write(ByteArray(pad))
        rawTarOut.write(ByteArray(1024))

        val gzippedOut = ByteArrayOutputStream()
        GZIPOutputStream(gzippedOut).use { gz ->
            gz.write(rawTarOut.toByteArray())
        }
        return gzippedOut.toByteArray()
    }
}
