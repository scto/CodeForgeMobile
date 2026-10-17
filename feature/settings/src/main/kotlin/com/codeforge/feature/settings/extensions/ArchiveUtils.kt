// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

object ArchiveUtils {

    /**
     * Entpackt eine ZIP-Datei in [destinationDir].
     * Schützt vor Zip-Slip (Pfad-Traversal) und setzt Ausführungsrechte für Binaries und Skripte.
     */
    fun extractZip(zipFile: File, destinationDir: File): Result<Unit> = runCatching {
        if (!zipFile.exists() || !zipFile.isFile) {
            throw IllegalArgumentException("Source zip file does not exist: ${zipFile.absolutePath}")
        }
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val destCanonicalPath = destinationDir.canonicalPath

        try {
            ZipInputStream(FileInputStream(zipFile).buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name.trimStart('/', '\\')
                    val targetFile = File(destinationDir, entryName)
                    val targetCanonicalPath = targetFile.canonicalPath

                    // Zip Slip vulnerability protection: ensure entry doesn't escape destination directory
                    if (!targetCanonicalPath.startsWith(destCanonicalPath + File.separator) && targetCanonicalPath != destCanonicalPath) {
                        throw SecurityException("Zip Slip detected: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).buffered().use { fos ->
                            zis.copyTo(fos)
                        }
                        // Grant execute permission if in a bin directory or a shell script
                        if (targetFile.name.endsWith(".sh") || targetFile.parentFile?.name == "bin" || !targetFile.name.contains(".")) {
                            targetFile.setExecutable(true, false)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            destinationDir.deleteRecursively()
            throw e
        }
    }

    /**
     * Entpackt eine TAR_GZ- (.tar.gz / .tgz) Datei in [destinationDir].
     * Verwendet [GZIPInputStream] und einen integrierten ustar/GNU-Tar-Parser.
     * Schützt vor Zip-Slip/Pfad-Traversal und Symlink-Ausbrüchen.
     */
    fun extractTarGz(tarGzFile: File, destinationDir: File): Result<Unit> = runCatching {
        if (!tarGzFile.exists() || !tarGzFile.isFile) {
            throw IllegalArgumentException("Source tar.gz file does not exist: ${tarGzFile.absolutePath}")
        }
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val destCanonicalPath = destinationDir.canonicalPath

        try {
            GZIPInputStream(FileInputStream(tarGzFile).buffered()).use { gzipIn ->
                extractTarStream(gzipIn, destinationDir, destCanonicalPath)
            }
        } catch (e: Exception) {
            destinationDir.deleteRecursively()
            throw e
        }
    }

    /**
     * Entpackt eine einzelne .gz-Datei in [destinationFile].
     */
    fun extractGz(gzFile: File, destinationFile: File): Result<Unit> = runCatching {
        if (!gzFile.exists() || !gzFile.isFile) {
            throw IllegalArgumentException("Source gz file does not exist: ${gzFile.absolutePath}")
        }
        val parentDir = destinationFile.parentFile ?: File(".")
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }
        val parentCanonical = parentDir.canonicalPath
        val targetCanonical = destinationFile.canonicalPath
        if (!targetCanonical.startsWith(parentCanonical + File.separator) && targetCanonical != parentCanonical) {
            throw SecurityException("Destination path escapes parent directory: ${destinationFile.absolutePath}")
        }

        try {
            GZIPInputStream(FileInputStream(gzFile).buffered()).use { gis ->
                FileOutputStream(destinationFile).buffered().use { fos ->
                    gis.copyTo(fos)
                }
            }
            destinationFile.setExecutable(true, false)
        } catch (e: Exception) {
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            throw e
        }
    }

    private fun extractTarStream(input: InputStream, destinationDir: File, destCanonicalPath: String) {
        val headerBuf = ByteArray(512)
        var nextLongName: String? = null
        var zeroBlockCount = 0

        while (true) {
            val bytesRead = readFully(input, headerBuf)
            if (bytesRead < 512) {
                break
            }

            // Two consecutive zero blocks signify end of tar archive
            if (isAllZeros(headerBuf)) {
                zeroBlockCount++
                if (zeroBlockCount >= 2) break
                continue
            }
            zeroBlockCount = 0

            val rawName = parseString(headerBuf, 0, 100)
            val prefix = parseString(headerBuf, 345, 155)
            val typeFlag = headerBuf[156].toInt().toChar()
            val size = parseOctal(headerBuf, 124, 12)
            val mode = parseOctal(headerBuf, 100, 8).toInt()

            val entryPath = when {
                nextLongName != null -> {
                    val lName = nextLongName
                    nextLongName = null
                    lName
                }
                prefix.isNotEmpty() -> "$prefix/$rawName"
                else -> rawName
            }.trimStart('/', '\\')

            // Handle GNU Tar Long Link/Name ('L')
            if (typeFlag == 'L') {
                val longNameBytes = readBytes(input, size)
                skipPadding(input, size)
                nextLongName = parseNullTerminatedString(longNameBytes)
                continue
            }

            // Handle PAX extended header ('x' or 'g')
            if (typeFlag == 'x' || typeFlag == 'g') {
                val paxBytes = readBytes(input, size)
                skipPadding(input, size)
                val paxMap = parsePaxHeaders(paxBytes)
                paxMap["path"]?.let { nextLongName = it }
                continue
            }

            if (entryPath.isEmpty()) {
                skipEntryData(input, size)
                continue
            }

            val targetFile = File(destinationDir, entryPath)
            val targetCanonicalPath = targetFile.canonicalPath

            // Zip Slip protection
            if (!targetCanonicalPath.startsWith(destCanonicalPath + File.separator) && targetCanonicalPath != destCanonicalPath) {
                throw SecurityException("Tar Slip detected: $entryPath")
            }

            // Symlink ('2')
            if (typeFlag == '2') {
                val linkTarget = parseString(headerBuf, 157, 100)
                val resolvedSymlink = File(targetFile.parentFile ?: destinationDir, linkTarget).canonicalPath
                if (!resolvedSymlink.startsWith(destCanonicalPath + File.separator) && resolvedSymlink != destCanonicalPath) {
                    throw SecurityException("Symlink target escapes destination directory: $linkTarget")
                }
                targetFile.parentFile?.mkdirs()
                runCatching {
                    val linkPath = targetFile.toPath()
                    Files.deleteIfExists(linkPath)
                    Files.createSymbolicLink(linkPath, java.nio.file.Paths.get(linkTarget))
                }
                skipEntryData(input, size)
                continue
            }

            // Directory ('5' or path ending with '/')
            if (typeFlag == '5' || entryPath.endsWith("/")) {
                targetFile.mkdirs()
                skipEntryData(input, size)
                continue
            }

            // Regular file ('0' or '\0')
            targetFile.parentFile?.mkdirs()
            FileOutputStream(targetFile).buffered().use { fos ->
                copyExact(input, fos, size)
            }
            skipPadding(input, size)

            // Permissions: Tar mode executable bit or bin/sh heuristic
            val isExecutable = (mode and 0b001_000_000 != 0) ||
                (mode and 0b000_001_000 != 0) ||
                targetFile.name.endsWith(".sh") ||
                targetFile.parentFile?.name == "bin" ||
                !targetFile.name.contains(".")

            if (isExecutable) {
                targetFile.setExecutable(true, false)
            }
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray): Int {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val count = input.read(buffer, totalRead, buffer.size - totalRead)
            if (count < 0) break
            totalRead += count
        }
        return totalRead
    }

    private fun isAllZeros(buffer: ByteArray): Boolean {
        for (b in buffer) {
            if (b != 0.toByte()) return false
        }
        return true
    }

    private fun parseString(buffer: ByteArray, offset: Int, length: Int): String {
        var end = offset
        val limit = offset + length
        while (end < limit && buffer[end] != 0.toByte()) {
            end++
        }
        return String(buffer, offset, end - offset, Charsets.UTF_8).trim()
    }

    private fun parseNullTerminatedString(bytes: ByteArray): String {
        var end = 0
        while (end < bytes.size && bytes[end] != 0.toByte()) {
            end++
        }
        return String(bytes, 0, end, Charsets.UTF_8).trim()
    }

    private fun parseOctal(buffer: ByteArray, offset: Int, length: Int): Long {
        var result = 0L
        var i = offset
        val limit = offset + length
        // skip leading spaces and nulls
        while (i < limit && (buffer[i] == ' '.code.toByte() || buffer[i] == 0.toByte())) {
            i++
        }
        while (i < limit) {
            val b = buffer[i]
            if (b in '0'.code.toByte()..'7'.code.toByte()) {
                result = (result shl 3) + (b - '0'.code.toByte())
            } else {
                break
            }
            i++
        }
        return result
    }

    private fun readBytes(input: InputStream, length: Long): ByteArray {
        val bytes = ByteArray(length.toInt())
        readFully(input, bytes)
        return bytes
    }

    private fun copyExact(input: InputStream, output: java.io.OutputStream, totalBytes: Long) {
        val buffer = ByteArray(8192)
        var remaining = totalBytes
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val count = input.read(buffer, 0, toRead)
            if (count < 0) {
                throw java.io.EOFException("Unexpected end of archive data stream")
            }
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun skipPadding(input: InputStream, size: Long) {
        val pad = (512 - (size % 512)) % 512
        if (pad > 0) {
            val padBuf = ByteArray(pad.toInt())
            readFully(input, padBuf)
        }
    }

    private fun skipEntryData(input: InputStream, size: Long) {
        if (size <= 0) return
        var remaining = size
        val buffer = ByteArray(8192)
        while (remaining > 0) {
            val toRead = minOf(remaining, buffer.size.toLong()).toInt()
            val count = input.read(buffer, 0, toRead)
            if (count < 0) break
            remaining -= count
        }
        skipPadding(input, size)
    }

    private fun parsePaxHeaders(bytes: ByteArray): Map<String, String> {
        val headers = mutableMapOf<String, String>()
        val text = String(bytes, Charsets.UTF_8)
        val lines = text.split("\n")
        for (line in lines) {
            val spaceIndex = line.indexOf(' ')
            if (spaceIndex <= 0) continue
            val equalsIndex = line.indexOf('=', spaceIndex)
            if (equalsIndex <= spaceIndex) continue
            val key = line.substring(spaceIndex + 1, equalsIndex)
            val value = line.substring(equalsIndex + 1)
            headers[key] = value
        }
        return headers
    }
}
