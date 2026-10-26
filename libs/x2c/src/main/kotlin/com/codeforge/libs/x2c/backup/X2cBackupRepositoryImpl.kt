package com.codeforge.libs.x2c.backup

import com.codeforge.core.domain.repository.X2cBackupRepository
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Task 52.3: Vorab-Sicherung eines Projekts als ZIP vor der XML-zu-Compose-Migration.
 *
 * Dateiname: <Projekt>/Backup/<projektname>-xml-<JJJJMMTT-HHMMSS>.zip
 * Ausschlüsse: Backup, build, .gradle, .cxx, node_modules, out
 * .git standardmäßig enthalten.
 * Freier Speicherplatz: mindestens 2x der Gesamtdateigröße.
 * Verifikation: Nach dem Schreiben wird das ZIP geöffnet und CRC32/Größe jedes Eintrags validiert.
 * Schlägt die Prüfung fehl, wird die ZIP-Datei gelöscht und keine Änderung vorgenommen.
 * Zip-Slip-Schutz beim Wiederherstellen.
 */
class X2cBackupRepositoryImpl(
    private val freeSpaceProvider: ((File) -> Long)? = null
) : X2cBackupRepository {

    companion object {
        val DEFAULT_EXCLUDES = setOf(
            "Backup", "build", ".gradle", ".cxx", "node_modules", "out"
        )
    }

    override suspend fun createBackup(projectPath: String, includeGit: Boolean): String {
        val projectDir = File(projectPath).canonicalFile
        require(projectDir.exists() && projectDir.isDirectory) {
            "Projektverzeichnis existiert nicht: $projectPath"
        }

        val backupDir = File(projectDir, "Backup")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val projectName = projectDir.name.ifBlank { "project" }
        val backupZip = File(backupDir, "$projectName-xml-$timestamp.zip")

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
                if (file.canonicalPath != backupZip.canonicalPath) {
                    filesToBackup.add(file)
                }
            }

        // Freier Speicherplatz prüfen (mindestens 2x der Gesamtgröße aller Quelldateien)
        val totalBytes = filesToBackup.sumOf { it.length() }
        val freeBytes = freeSpaceProvider?.invoke(backupDir) ?: backupDir.freeSpace
        if (freeBytes in 1 until totalBytes * 2) {
            throw IllegalStateException(
                "Nicht genügend freier Speicher für Backup vorhanden (benötigt: ${totalBytes * 2} Bytes, frei: $freeBytes Bytes)."
            )
        }

        val originalCrcs = mutableMapOf<String, Long>()
        val originalSizes = mutableMapOf<String, Long>()

        try {
            FileOutputStream(backupZip).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    for (file in filesToBackup) {
                        val relPath = file.relativeTo(projectDir).path.replace('\\', '/')
                        val entry = ZipEntry(relPath).apply {
                            time = file.lastModified()
                        }
                        zos.putNextEntry(entry)
                        val crc = CRC32()
                        var fileBytes = 0L
                        file.inputStream().use { input ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                zos.write(buffer, 0, bytesRead)
                                crc.update(buffer, 0, bytesRead)
                                fileBytes += bytesRead
                            }
                        }
                        zos.closeEntry()
                        originalCrcs[relPath] = crc.value
                        originalSizes[relPath] = fileBytes
                    }
                }
            }

            // Post-Write Verifikation: ZIP erneut öffnen und Eintragsanzahl, Größe und CRC prüfen
            ZipFile(backupZip).use { zip ->
                if (zip.size() != filesToBackup.size) {
                    throw IllegalStateException(
                        "Backup-Verifikation fehlgeschlagen: Anzahl der Einträge stimmt nicht überein (${zip.size()} vs ${filesToBackup.size})."
                    )
                }
                for (entry in zip.entries()) {
                    val expectedCrc = originalCrcs[entry.name]
                    val expectedSize = originalSizes[entry.name]

                    if (expectedCrc == null || expectedSize == null) {
                        throw IllegalStateException("Backup-Verifikation fehlgeschlagen: Unerwarteter Eintrag ${entry.name}.")
                    }
                    if (entry.size != -1L && entry.size != expectedSize) {
                        throw IllegalStateException(
                            "Backup-Verifikation fehlgeschlagen: Dateigröße für ${entry.name} weicht ab (${entry.size} vs $expectedSize)."
                        )
                    }
                    if (entry.crc != -1L && entry.crc != expectedCrc) {
                        throw IllegalStateException(
                            "Backup-Verifikation fehlgeschlagen: CRC-Meldung für ${entry.name} (${entry.crc} vs $expectedCrc)."
                        )
                    }
                }
            }
        } catch (e: Exception) {
            // Schlägt die Prüfung fehl oder tritt ein Fehler auf, wird die ZIP-Datei gelöscht
            if (backupZip.exists()) {
                backupZip.delete()
            }
            throw e
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

    override suspend fun restoreFile(
        projectPath: String,
        backupZipPath: String,
        relativeFilePath: String
    ): Boolean {
        val projectDir = File(projectPath).canonicalFile
        val zipFile = File(backupZipPath)
        if (!zipFile.exists() || !zipFile.isFile) return false

        val normalizedRelPath = relativeFilePath.replace('\\', '/')
        val targetFile = File(projectDir, normalizedRelPath).canonicalFile

        // Zip-Slip Schutz
        if (!targetFile.path.startsWith(projectDir.path + File.separator) && targetFile != projectDir) {
            throw SecurityException("Zip-Slip erkannt: $normalizedRelPath liegt außerhalb des Projekts!")
        }

        ZipFile(zipFile).use { zip ->
            val entry = zip.getEntry(normalizedRelPath) ?: return false
            targetFile.parentFile?.mkdirs()
            zip.getInputStream(entry).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.setLastModified(entry.time)
        }
        return true
    }

    override suspend fun rollbackWithJournal(projectPath: String, journalPath: String): Boolean {
        val journalFile = File(journalPath)
        if (!journalFile.exists()) return false

        val projectDir = File(projectPath).canonicalFile
        val content = journalFile.readText()

        // 1. Suche nach Backup-ZIP im Journal oder im Backup-Ordner
        val backupZipPath = findBackupZipPath(content, projectDir)

        // 2. Behandle JSON-Journal oder Pipe-Delimited-Journal
        if (content.trimStart().startsWith("{")) {
            rollbackJsonJournal(content, projectDir, backupZipPath)
        } else {
            rollbackLineJournal(journalFile.readLines(), projectDir, backupZipPath)
        }
        return true
    }

    private fun findBackupZipPath(content: String, projectDir: File): String? {
        val regex = Regex(""""backupZipPath"\s*:\s*"([^"]+)"""")
        val match = regex.find(content)
        if (match != null) {
            val path = match.groupValues[1]
            if (File(path).exists()) return path
        }

        // Fallback: neuestes XML-Backup im Backup/-Verzeichnis
        val backupDir = File(projectDir, "Backup")
        if (backupDir.exists()) {
            val latest = backupDir.listFiles { f -> f.isFile && f.name.contains("-xml-") && f.name.endsWith(".zip") }
                ?.maxByOrNull { it.lastModified() }
            if (latest != null) return latest.absolutePath
        }
        return null
    }

    private suspend fun rollbackJsonJournal(jsonContent: String, projectDir: File, backupZipPath: String?) {
        // Regex-basierte Extraktion der Journal-Aktionen
        val entryRegex = Regex("""\{\s*"action"\s*:\s*"([^"]+)"\s*,\s*"relativePath"\s*:\s*"([^"]+)".*?\}""", RegexOption.DOT_MATCHES_ALL)
        for (match in entryRegex.findAll(jsonContent)) {
            val action = match.groupValues[1]
            val relPath = match.groupValues[2]
            applyRollbackAction(action, relPath, projectDir, backupZipPath)
        }
    }

    private suspend fun rollbackLineJournal(lines: List<String>, projectDir: File, backupZipPath: String?) {
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            val parts = trimmed.split("|")
            if (parts.size >= 2) {
                val action = parts[0]
                val relPath = parts[1]
                applyRollbackAction(action, relPath, projectDir, backupZipPath)
            }
        }
    }

    private suspend fun applyRollbackAction(
        action: String,
        relPath: String,
        projectDir: File,
        backupZipPath: String?
    ) {
        val targetFile = File(projectDir, relPath).canonicalFile
        // Schutz vor Pfadausbrüchen
        if (!targetFile.path.startsWith(projectDir.path)) return

        when (action.uppercase()) {
            "CREATED" -> {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            }
            "MODIFIED", "DELETED" -> {
                if (backupZipPath != null) {
                    restoreFile(projectDir.absolutePath, backupZipPath, relPath)
                }
            }
            "CONVERTED" -> {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
            }
        }
    }
}
