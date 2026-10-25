package com.codeforge.libs.j2k.backup

import com.codeforge.core.domain.repository.ProjectBackupRepository
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ProjectBackupRepositoryImpl : ProjectBackupRepository {

    companion object {
        val DEFAULT_EXCLUDES = setOf(
            "Backup", "build", ".gradle", ".cxx", "node_modules", "out"
        )
    }

    override suspend fun createBackup(projectPath: String, includeGit: Boolean): String {
        val projectDir = File(projectPath).canonicalFile
        require(projectDir.exists() && projectDir.isDirectory) { "Projektverzeichnis existiert nicht: $projectPath" }

        val backupDir = File(projectDir, "Backup")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val projectName = projectDir.name.ifBlank { "project" }
        val backupZip = File(backupDir, "$projectName-java-$timestamp.zip")

        val filesToBackup = mutableListOf<File>()
        projectDir.walkTopDown()
            .onEnter { dir ->
                val name = dir.name
                when {
                    DEFAULT_EXCLUDES.contains(name) -> false
                    name == ".git" && !includeGit -> false
                    else -> true
                }
            }
            .filter { it.isFile }
            .forEach { file ->
                filesToBackup.add(file)
            }

        // Freier Speicherplatz prüfen (mindestens 2x der geschätzten Gesamtgröße)
        val totalBytes = filesToBackup.sumOf { it.length() }
        val freeBytes = backupDir.freeSpace
        if (freeBytes < totalBytes * 2 && freeBytes > 0) {
            throw IllegalStateException("Nicht genügend freier Speicher für Backup vorhanden (benötigt: ${totalBytes * 2} Bytes, frei: $freeBytes Bytes).")
        }

        val originalCrcs = mutableMapOf<String, Long>()

        FileOutputStream(backupZip).use { fos ->
            ZipOutputStream(fos).use { zos ->
                for (file in filesToBackup) {
                    val relPath = file.relativeTo(projectDir).path.replace('\\', '/')
                    val entry = ZipEntry(relPath).apply {
                        time = file.lastModified()
                    }
                    zos.putNextEntry(entry)
                    val crc = CRC32()
                    file.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            zos.write(buffer, 0, bytesRead)
                            crc.update(buffer, 0, bytesRead)
                        }
                    }
                    zos.closeEntry()
                    originalCrcs[relPath] = crc.value
                }
            }
        }

        // Verifikation: ZIP erneut öffnen und CRC/Eintragsanzahl prüfen
        ZipFile(backupZip).use { zip ->
            if (zip.size() != filesToBackup.size) {
                backupZip.delete()
                throw IllegalStateException("Backup-Verifikation fehlgeschlagen: Anzahl der Einträge stimmt nicht überein.")
            }
            for (entry in zip.entries()) {
                val expectedCrc = originalCrcs[entry.name]
                if (expectedCrc != null && entry.crc != -1L && entry.crc != expectedCrc) {
                    backupZip.delete()
                    throw IllegalStateException("Backup-Verifikation fehlgeschlagen: CRC-Meldung für ${entry.name}.")
                }
            }
        }

        return backupZip.absolutePath
    }

    override suspend fun restoreBackup(projectPath: String, backupZipPath: String): Boolean {
        val projectDir = File(projectPath).canonicalFile
        val zipFile = File(backupZipPath)
        if (!zipFile.exists() || !zipFile.isFile) return false

        ZipFile(zipFile).use { zip ->
            for (entry in zip.entries()) {
                val targetFile = File(projectDir, entry.name).canonicalFile
                // Zip-Slip Schutz
                if (!targetFile.path.startsWith(projectDir.path + File.separator) && targetFile != projectDir) {
                    throw SecurityException("Zip-Slip erkannt: ${entry.name} liegt außerhalb des Projekts!")
                }
                if (entry.isDirectory) {
                    targetFile.mkdirs()
                } else {
                    targetFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    targetFile.setLastModified(entry.time)
                }
            }
        }
        return true
    }

    override suspend fun rollbackWithJournal(projectPath: String, journalPath: String): Boolean {
        val journalFile = File(journalPath)
        if (!journalFile.exists()) return false

        val projectDir = File(projectPath).canonicalFile
        val lines = journalFile.readLines()
        for (line in lines) {
            val parts = line.split("|")
            if (parts.size >= 2) {
                val action = parts[0]
                val relPath = parts[1]
                val targetFile = File(projectDir, relPath).canonicalFile
                if (!targetFile.path.startsWith(projectDir.path)) continue

                when (action) {
                    "CREATED" -> {
                        if (targetFile.exists()) {
                            targetFile.delete()
                        }
                    }
                    "CONVERTED" -> {
                        // Neue Datei löschen, wenn alte noch als Backup existiert
                        if (targetFile.exists()) {
                            targetFile.delete()
                        }
                        if (parts.size >= 3) {
                            val originalPath = parts[2]
                            val origFile = File(projectDir, originalPath)
                            if (origFile.exists() && targetFile.path != origFile.path) {
                                // original bleibt
                            }
                        }
                    }
                }
            }
        }
        return true
    }
}
