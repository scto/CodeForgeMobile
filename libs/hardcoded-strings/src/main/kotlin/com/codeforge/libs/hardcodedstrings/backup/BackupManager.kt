package com.codeforge.libs.hardcodedstrings.backup

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages atomic backups and rollbacks for file modifications.
 */
class BackupManager(private val projectRoot: File) {

    private val backupsBaseDir = File(projectRoot, ".codeforge/backups/hardcoded-strings")

    /**
     * Creates a backup of the specified files.
     * Returns the backup directory created.
     */
    fun createBackup(filesToBackup: Collection<File>): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val sessionDir = File(backupsBaseDir, timestamp)
        sessionDir.mkdirs()

        for (file in filesToBackup) {
            if (file.exists()) {
                val rel = file.relativeTo(projectRoot).path
                val backupFile = File(sessionDir, rel)
                backupFile.parentFile?.mkdirs()
                file.copyTo(backupFile, overwrite = true)
            }
        }

        return sessionDir
    }

    /**
     * Restores files from a backup directory back to projectRoot.
     */
    fun restoreBackup(sessionDir: File): Boolean {
        if (!sessionDir.exists() || !sessionDir.isDirectory) {
            return false
        }

        sessionDir.walkTopDown().filter { it.isFile }.forEach { backupFile ->
            val rel = backupFile.relativeTo(sessionDir).path
            val targetFile = File(projectRoot, rel)
            targetFile.parentFile?.mkdirs()
            backupFile.copyTo(targetFile, overwrite = true)
        }

        return true
    }

    /**
     * Returns the most recent backup directory if available.
     */
    fun getLatestBackup(): File? {
        if (!backupsBaseDir.exists()) return null
        return backupsBaseDir.listFiles()
            ?.filter { it.isDirectory }
            ?.maxByOrNull { it.name }
    }
}
