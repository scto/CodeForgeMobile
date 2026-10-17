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
