package com.codeforge.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.codeforge.core.domain.repository.LegacyProjectMigrator
import com.codeforge.core.domain.repository.MigrationSummary
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LegacyProjectMigratorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectsDirectoryProvider: ProjectsDirectoryProvider,
    private val dataStore: DataStore<Preferences>
) : LegacyProjectMigrator {

    companion object {
        private val KEY_MIGRATION_DECLINED = booleanPreferencesKey("legacy_migration_declined")
    }

    private val legacyDir: File
        get() = File(context.filesDir, "projects")

    override fun hasLegacyProjects(): Boolean {
        return listLegacyProjects().isNotEmpty()
    }

    override fun listLegacyProjects(): List<File> {
        val root = legacyDir
        if (!root.exists() || !root.isDirectory) return emptyList()
        return root.listFiles { file -> file.isDirectory }?.toList() ?: emptyList()
    }

    override suspend fun isMigrationDeclined(): Boolean {
        return try {
            val prefs = dataStore.data.first()
            prefs[KEY_MIGRATION_DECLINED] ?: false
        } catch (_: Throwable) {
            false
        }
    }

    override suspend fun setMigrationDeclined(declined: Boolean) {
        try {
            dataStore.edit { prefs ->
                prefs[KEY_MIGRATION_DECLINED] = declined
            }
        } catch (_: Throwable) {}
    }

    override suspend fun migrateLegacyProjects(): Result<MigrationSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val projects = listLegacyProjects()
            if (projects.isEmpty()) {
                return@runCatching MigrationSummary(0, null)
            }

            // 1. ZIP-Backup des bisherigen Bestands anlegen
            val backupDir = File(context.filesDir, "backups").apply { mkdirs() }
            val backupFile = File(backupDir, "legacy-projects-migration-${System.currentTimeMillis()}.zip")
            createZipBackup(legacyDir, backupFile)

            var movedCount = 0
            val targetRoot = projectsDirectoryProvider.getProjectsRoot().apply { mkdirs() }

            for (project in projects) {
                val projectName = project.name
                var targetProjectDir = File(targetRoot, projectName)
                var suffix = 1
                while (targetProjectDir.exists()) {
                    targetProjectDir = File(targetRoot, "${projectName}_migrated_$suffix")
                    suffix++
                }

                // Kopieren
                project.copyRecursively(targetProjectDir, overwrite = false)

                // Prüfen: Dateianzahl und Gesamtgröße
                val (sourceCount, sourceSize) = calculateStats(project)
                val (targetCount, targetSize) = calculateStats(targetProjectDir)

                if (sourceCount != targetCount || sourceSize != targetSize) {
                    // Prüfungsfehler: Ziel verwerfen, Quelle unangetastet lassen
                    targetProjectDir.deleteRecursively()
                    throw IllegalStateException("Validierungsfehler bei Projekt '$projectName': Quelldateien ($sourceCount/$sourceSize B) != Ziel ($targetCount/$targetSize B)")
                }

                // Erst nach erfolgreicher Prüfung Quelle löschen
                project.deleteRecursively()
                movedCount++
            }

            // Wenn legacyDir nun leer ist, aufräumen
            if (legacyDir.listFiles().isNullOrEmpty()) {
                legacyDir.delete()
            }

            MigrationSummary(movedCount, backupFile.absolutePath)
        }
    }

    private fun calculateStats(dir: File): Pair<Int, Long> {
        var count = 0
        var totalSize = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                count++
                totalSize += file.length()
            }
        }
        return count to totalSize
    }

    private fun createZipBackup(sourceDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            val sourceCanonical = sourceDir.canonicalFile
            sourceDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.canonicalPath
                        .removePrefix(sourceCanonical.path)
                        .trimStart(File.separatorChar)
                        .replace(File.separatorChar, '/')
                    val entry = ZipEntry(relativePath)
                    zos.putNextEntry(entry)
                    FileInputStream(file).use { fis ->
                        fis.copyTo(zos)
                    }
                    zos.closeEntry()
                }
            }
        }
    }
}
