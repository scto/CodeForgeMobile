// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

object ArchiveUtils {

    fun extractZip(zipFile: File, destinationDir: File): Result<Unit> = runCatching {
        if (!zipFile.exists() || !zipFile.isFile) {
            throw IllegalArgumentException("Source zip file does not exist: ${zipFile.absolutePath}")
        }
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val destCanonicalPath = destinationDir.canonicalPath

        ZipInputStream(FileInputStream(zipFile).buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val targetFile = File(destinationDir, entry.name)
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
    }
}
