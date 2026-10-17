// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveUtilsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `extractZip successfully extracts nested files and directories`() {
        val zipFile = tempFolder.newFile("sample.zip")
        val destDir = tempFolder.newFolder("dest_zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("bin/"))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("bin/launcher"))
            zos.write("#!/bin/sh\necho ok".toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("data/config.txt"))
            zos.write("key=value".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = ArchiveUtils.extractZip(zipFile, destDir)
        assertTrue(result.isSuccess)

        val launcherFile = File(destDir, "bin/launcher")
        val configFile = File(destDir, "data/config.txt")

        assertTrue(launcherFile.exists())
        assertEquals("#!/bin/sh\necho ok", launcherFile.readText())
        assertTrue(launcherFile.canExecute())

        assertTrue(configFile.exists())
        assertEquals("key=value", configFile.readText())
    }

    @Test
    fun `extractZip prevents Zip Slip attack and cleans up destination`() {
        val zipFile = tempFolder.newFile("malicious.zip")
        val destDir = tempFolder.newFolder("dest_safe_zip")

        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            zos.putNextEntry(ZipEntry("../escaped.txt"))
            zos.write("danger".toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = ArchiveUtils.extractZip(zipFile, destDir)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertFalse(destDir.exists())
    }

    @Test
    fun `extractTarGz successfully extracts files and sets executable mode`() {
        val tarGzFile = tempFolder.newFile("sample.tar.gz")
        val destDir = tempFolder.newFolder("dest_targz")

        val files = mapOf(
            "bin/server.sh" to "#!/bin/sh\necho server".toByteArray(Charsets.UTF_8),
            "lib/app.jar" to "dummy jar content".toByteArray(Charsets.UTF_8)
        )
        val modes = mapOf(
            "bin/server.sh" to 0b111_101_101 // rwxr-xr-x
        )

        val tarGzBytes = createTarGzArchive(files = files, modes = modes)
        tarGzFile.writeBytes(tarGzBytes)

        val result = ArchiveUtils.extractTarGz(tarGzFile, destDir)
        assertTrue(result.isSuccess)

        val script = File(destDir, "bin/server.sh")
        val jar = File(destDir, "lib/app.jar")

        assertTrue(script.exists())
        assertEquals("#!/bin/sh\necho server", script.readText())
        assertTrue("Executable bit must be preserved", script.canExecute())

        assertTrue(jar.exists())
        assertEquals("dummy jar content", jar.readText())
    }

    @Test
    fun `extractTarGz prevents Tar Slip path traversal and cleans up`() {
        val tarGzFile = tempFolder.newFile("malicious.tar.gz")
        val destDir = tempFolder.newFolder("dest_safe_targz")

        val files = mapOf(
            "../../etc/evil.sh" to "malicious content".toByteArray(Charsets.UTF_8)
        )

        val tarGzBytes = createTarGzArchive(files = files)
        tarGzFile.writeBytes(tarGzBytes)

        val result = ArchiveUtils.extractTarGz(tarGzFile, destDir)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertFalse(destDir.exists())
    }

    @Test
    fun `extractTarGz prevents Symlink escaping destination folder`() {
        val tarGzFile = tempFolder.newFile("symlink_evil.tar.gz")
        val destDir = tempFolder.newFolder("dest_symlink")

        val tarGzBytes = createTarGzArchive(
            files = emptyMap(),
            symlinks = mapOf("bin/escaped_link" to "../../sensitive/data")
        )
        tarGzFile.writeBytes(tarGzBytes)

        val result = ArchiveUtils.extractTarGz(tarGzFile, destDir)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertFalse(destDir.exists())
    }

    @Test
    fun `extractGz extracts single file and sets executable bit`() {
        val gzFile = tempFolder.newFile("binary.gz")
        val destFile = File(tempFolder.newFolder("bin_dest"), "rust-analyzer")

        val content = "ELF_BINARY_CONTENT"
        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { it.write(content.toByteArray(Charsets.UTF_8)) }
        gzFile.writeBytes(byteOut.toByteArray())

        val result = ArchiveUtils.extractGz(gzFile, destFile)
        assertTrue(result.isSuccess)
        assertTrue(destFile.exists())
        assertEquals(content, destFile.readText())
        assertTrue(destFile.canExecute())
    }

    /**
     * Erzeugt ein syntaktisch valides .tar.gz-Archiv im ustar-Format im Speicher.
     */
    private fun createTarGzArchive(
        files: Map<String, ByteArray>,
        modes: Map<String, Int> = emptyMap(),
        symlinks: Map<String, String> = emptyMap()
    ): ByteArray {
        val rawTarOut = ByteArrayOutputStream()

        for ((name, data) in files) {
            val mode = modes[name] ?: 0b110_100_100 // rw-r--r--
            writeTarHeader(
                out = rawTarOut,
                name = name,
                size = data.size.toLong(),
                mode = mode,
                typeFlag = '0',
                linkTarget = ""
            )
            rawTarOut.write(data)
            val pad = (512 - (data.size % 512)) % 512
            if (pad > 0) {
                rawTarOut.write(ByteArray(pad))
            }
        }

        for ((name, target) in symlinks) {
            writeTarHeader(
                out = rawTarOut,
                name = name,
                size = 0L,
                mode = 0b111_111_111,
                typeFlag = '2',
                linkTarget = target
            )
        }

        // Two 512-byte zero blocks at end of tar archive
        rawTarOut.write(ByteArray(1024))

        val gzippedOut = ByteArrayOutputStream()
        GZIPOutputStream(gzippedOut).use { gz ->
            gz.write(rawTarOut.toByteArray())
        }
        return gzippedOut.toByteArray()
    }

    private fun writeTarHeader(
        out: ByteArrayOutputStream,
        name: String,
        size: Long,
        mode: Int,
        typeFlag: Char,
        linkTarget: String
    ) {
        val header = ByteArray(512)

        // Name (0..99)
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        System.arraycopy(nameBytes, 0, header, 0, minOf(nameBytes.size, 100))

        // Mode (100..107, octal)
        val modeStr = "%07o".format(mode)
        System.arraycopy(modeStr.toByteArray(Charsets.US_ASCII), 0, header, 100, 7)

        // UID (108..115) & GID (116..123)
        System.arraycopy("0000000".toByteArray(Charsets.US_ASCII), 0, header, 108, 7)
        System.arraycopy("0000000".toByteArray(Charsets.US_ASCII), 0, header, 116, 7)

        // Size (124..135, octal)
        val sizeStr = "%011o".format(size)
        System.arraycopy(sizeStr.toByteArray(Charsets.US_ASCII), 0, header, 124, 11)

        // Mtime (136..147, octal)
        System.arraycopy("00000000000".toByteArray(Charsets.US_ASCII), 0, header, 136, 11)

        // Checksum placeholder (148..155) - spaces
        for (i in 148..155) header[i] = ' '.code.toByte()

        // Typeflag (156)
        header[156] = typeFlag.code.toByte()

        // Linkname (157..256)
        val linkBytes = linkTarget.toByteArray(Charsets.UTF_8)
        System.arraycopy(linkBytes, 0, header, 157, minOf(linkBytes.size, 100))

        // Magic (257..262): "ustar  \0"
        System.arraycopy("ustar  ".toByteArray(Charsets.US_ASCII), 0, header, 257, 7)

        // Calculate checksum
        var sum = 0
        for (b in header) {
            sum += (b.toInt() and 0xFF)
        }
        val chkStr = "%06o\u0000 ".format(sum)
        System.arraycopy(chkStr.toByteArray(Charsets.US_ASCII), 0, header, 148, 8)

        out.write(header)
    }
}
