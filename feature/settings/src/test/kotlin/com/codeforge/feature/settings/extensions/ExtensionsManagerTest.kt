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
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExtensionsManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `catalog has valid and unique packages`() {
        val catalog = LspExtensionCatalog.ALL
        assertTrue(catalog.size >= 5)

        val ids = catalog.map { it.id }
        assertEquals("IDs in catalog must be unique", ids.size, ids.distinct().size)

        for (pkg in catalog) {
            assertTrue("Package ${pkg.id} must have non-blank downloadUrl", pkg.downloadUrl.isNotBlank())
            assertEquals("Package ${pkg.id} SHA-256 must be 64 hex chars", 64, pkg.expectedSha256.length)
            assertTrue("Package ${pkg.id} executableRelativePath must be non-blank", pkg.executableRelativePath.isNotBlank())
        }
    }

    @Test
    fun `install and uninstall lifecycle with mock downloader`() = runBlocking {
        val baseDir = tempFolder.newFolder("extensions_root")

        // Create a mock zip archive containing "bin/test-server"
        val mockArchiveBytes = createMockZip("bin/test-server", "#!/bin/sh\necho running")
        val correctHash = Sha256Utils.calculateSha256(mockArchiveBytes)

        val testPackage = LspExtensionPackage(
            id = "test-lsp",
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

        // 1. Initially not installed
        assertFalse(manager.isInstalled(testPackage))

        // 2. Install
        val installResult = manager.install(testPackage)
        assertTrue("Install should succeed", installResult.isSuccess)
        val executable = installResult.getOrThrow()
        assertTrue("Executable must exist", executable.exists())
        assertEquals("#!/bin/sh\necho running", executable.readText())

        // 3. Verify installed status and path
        assertTrue(manager.isInstalled(testPackage))
        val resolvedExec = manager.getExecutableFile(testPackage)
        assertNotNull(resolvedExec)
        assertEquals(executable.canonicalPath, resolvedExec?.canonicalPath)

        // 4. Uninstall
        val uninstallResult = manager.uninstall(testPackage)
        assertTrue("Uninstall should succeed", uninstallResult.isSuccess)
        assertFalse(manager.isInstalled(testPackage))
        assertFalse(executable.exists())
    }

    @Test
    fun `install fails if SHA-256 mismatch occurs`() = runBlocking {
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

        val mockDownloader = FileDownloader { _, dest, _ ->
            dest.parentFile?.mkdirs()
            dest.writeBytes(mockArchiveBytes)
            Result.success(dest)
        }

        val manager = ExtensionsManager(baseDir = baseDir, downloader = mockDownloader)
        val result = manager.install(testPackage)

        assertTrue("Install should fail due to SHA-256 mismatch", result.isFailure)
        assertFalse("Package must not be marked as installed", manager.isInstalled(testPackage))
    }

    private fun createMockZip(entryName: String, content: String): ByteArray {
        val byteOut = java.io.ByteArrayOutputStream()
        ZipOutputStream(byteOut).use { zos ->
            zos.putNextEntry(ZipEntry(entryName))
            zos.write(content.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
        return byteOut.toByteArray()
    }
}
