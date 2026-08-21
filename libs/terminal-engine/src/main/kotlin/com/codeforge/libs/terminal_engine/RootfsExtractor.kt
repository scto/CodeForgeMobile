// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import android.system.Os
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Entpackt eine tar.gz/tar.xz-Rootfs in [targetDir]. Symlinks (in Linux-Rootfs-Archiven
 * z.B. /bin -> usr/bin sehr häufig) werden über android.system.Os.symlink als echte
 * Dateisystem-Symlinks angelegt, nicht als Kopien — sonst brechen viele Distro-interne
 * Pfaderwartungen. Datei-Permissions (insbesondere das execute-Bit für Binaries wie
 * /bin/sh) werden aus dem Tar-Eintrag übernommen.
 */
class RootfsExtractor {

    fun extract(archiveFile: File, targetDir: File, archiveFormat: ArchiveFormat): Flow<Int> = flow {
        val totalEntries = countEntries(archiveFile, archiveFormat)
        var processed = 0
        var lastEmittedPercent = -1

        openTarStream(archiveFile, archiveFormat).use { tarStream ->
            var entry: TarArchiveEntry? = tarStream.nextEntry as TarArchiveEntry?
            while (entry != null) {
                extractEntry(tarStream, entry, targetDir)

                processed++
                if (totalEntries > 0) {
                    val percent = ((processed * 100) / totalEntries).coerceIn(0, 100)
                    if (percent != lastEmittedPercent) {
                        lastEmittedPercent = percent
                        emit(percent)
                    }
                }

                entry = tarStream.nextEntry as TarArchiveEntry?
            }
        }

        if (lastEmittedPercent != 100) emit(100)
    }.flowOn(Dispatchers.IO)

    private fun extractEntry(tarStream: TarArchiveInputStream, entry: TarArchiveEntry, targetDir: File) {
        val outFile = File(targetDir, entry.name)

        when {
            entry.isSymbolicLink -> {
                outFile.parentFile?.mkdirs()
                runCatching { Os.symlink(entry.linkName, outFile.path) }
            }

            entry.isDirectory -> {
                outFile.mkdirs()
            }

            else -> {
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { output -> tarStream.copyTo(output) }
                applyPermissions(outFile, entry.mode)
            }
        }
    }

    private fun applyPermissions(file: File, mode: Int) {
        file.setReadable((mode and 0b100_000_000) != 0, false)
        file.setWritable((mode and 0b010_000_000) != 0, false)
        file.setExecutable((mode and 0b001_000_000) != 0, false)
    }

    private fun countEntries(archiveFile: File, archiveFormat: ArchiveFormat): Int {
        var count = 0
        openTarStream(archiveFile, archiveFormat).use { tarStream ->
            while (tarStream.nextEntry != null) count++
        }
        return count
    }

    private fun openTarStream(archiveFile: File, archiveFormat: ArchiveFormat): TarArchiveInputStream {
        val rawInput: InputStream = BufferedInputStream(FileInputStream(archiveFile))
        val decompressed: InputStream = when (archiveFormat) {
            ArchiveFormat.TAR_GZ -> GzipCompressorInputStream(rawInput)
            ArchiveFormat.TAR_XZ -> XZCompressorInputStream(rawInput)
        }
        return TarArchiveInputStream(decompressed)
    }
}
