package com.codeforge.libs.j2k

import com.codeforge.core.domain.model.J2kFileResult
import com.codeforge.core.domain.model.J2kFileStatus
import com.codeforge.core.domain.model.J2kOptions
import com.codeforge.core.domain.model.J2kPlan
import com.codeforge.core.domain.model.J2kProgress
import com.codeforge.core.domain.model.J2kReport
import com.codeforge.core.domain.model.J2kScanResult
import com.codeforge.core.domain.repository.GradleDslConverter
import com.codeforge.core.domain.repository.J2kRepository
import com.codeforge.core.domain.repository.JavaToKotlinConverter
import com.codeforge.core.domain.repository.ProjectBackupRepository
import com.codeforge.core.domain.repository.ProjectLanguageScanner
import com.codeforge.core.domain.repository.VersionCatalogMigrator
import com.codeforge.libs.j2k.backup.ProjectBackupRepositoryImpl
import com.codeforge.libs.j2k.converter.GradleDslConverterImpl
import com.codeforge.libs.j2k.converter.JavaToKotlinConverterImpl
import com.codeforge.libs.j2k.converter.VersionCatalogMigratorImpl
import com.codeforge.libs.j2k.scanner.ProjectLanguageScannerImpl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class J2kRepositoryImpl(
    private val scanner: ProjectLanguageScanner = ProjectLanguageScannerImpl(),
    private val backupRepo: ProjectBackupRepository = ProjectBackupRepositoryImpl(),
    private val javaConverter: JavaToKotlinConverter = JavaToKotlinConverterImpl(),
    private val gradleConverter: GradleDslConverter = GradleDslConverterImpl(),
    private val catalogMigrator: VersionCatalogMigrator = VersionCatalogMigratorImpl()
) : J2kRepository {

    private val _progress = MutableStateFlow<J2kProgress>(J2kProgress.Idle)
    override val progress: StateFlow<J2kProgress> = _progress.asStateFlow()

    @Volatile
    private var isCancelled = false

    override suspend fun scanProject(projectPath: String): J2kScanResult {
        return scanner.scanProject(projectPath)
    }

    override suspend fun createPlan(projectPath: String): J2kPlan {
        return scanner.createPlan(projectPath)
    }

    override suspend fun executeMigration(projectPath: String, options: J2kOptions): J2kReport {
        isCancelled = false
        val projectDir = File(projectPath).canonicalFile
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val backupDir = File(projectDir, "Backup").apply { mkdirs() }
        val journalFile = File(backupDir, "j2k-journal-$timestamp.json")
        val journalEntries = mutableListOf<String>()

        _progress.value = J2kProgress.Scanning("Projekt-Quellen und Build-Dateien analysieren...")
        val plan = scanner.createPlan(projectPath)

        _progress.value = J2kProgress.CreatingBackup(projectPath)
        val backupZipPath = backupRepo.createBackup(projectPath, options.includeGit)

        val fileResults = mutableListOf<J2kFileResult>()
        var convertedJava = 0
        var notesJava = 0
        var skippedJava = 0
        var failedJava = 0

        var convertedGradle = 0
        var notesGradle = 0
        var skippedGradle = 0

        try {
            // 1. Build-Vorbereitung (Kotlin-Unterstützung sicherstellen)
            _progress.value = J2kProgress.PreparingBuild("Kotlin-Plugin und Abhängigkeiten vorbereiten...")
            ensureKotlinInBuild(projectDir, journalEntries)

            // 2. Java-Dateien konvertieren
            val totalJava = plan.javaFiles.size
            for ((index, relJava) in plan.javaFiles.withIndex()) {
                checkCancelled(journalFile, journalEntries, projectPath)
                _progress.value = J2kProgress.ConvertingJava(index + 1, totalJava, relJava)

                val javaFile = File(projectDir, relJava)
                if (!javaFile.exists()) {
                    skippedJava++
                    fileResults.add(J2kFileResult(relJava, J2kFileStatus.SKIPPED, notes = listOf("Datei nicht gefunden")))
                    continue
                }

                val source = javaFile.readText()
                val conv = javaConverter.convertFile(source, relJava)
                fileResults.add(conv.result)

                when (conv.result.status) {
                    J2kFileStatus.CONVERTED -> convertedJava++
                    J2kFileStatus.CONVERTED_WITH_NOTES -> notesJava++
                    J2kFileStatus.SKIPPED -> skippedJava++
                    J2kFileStatus.FAILED -> failedJava++
                }

                val content = conv.convertedContent
                if (content != null && (conv.result.status == J2kFileStatus.CONVERTED || conv.result.status == J2kFileStatus.CONVERTED_WITH_NOTES)) {
                    val ktRelPath = relJava.removeSuffix(".java") + ".kt"
                    val ktFile = File(projectDir, ktRelPath)
                    ktFile.writeText(content)
                    javaFile.delete()
                    journalEntries.add("CONVERTED|$ktRelPath|$relJava")
                }
            }

            // 3. Gradle-Dateien konvertieren (wenn Schalter aktiv)
            if (options.convertGradle) {
                val totalGradle = plan.groovyFiles.size
                for ((index, relGroovy) in plan.groovyFiles.withIndex()) {
                    checkCancelled(journalFile, journalEntries, projectPath)
                    _progress.value = J2kProgress.ConvertingGradle(index + 1, totalGradle, relGroovy)

                    val groovyFile = File(projectDir, relGroovy)
                    if (!groovyFile.exists()) {
                        skippedGradle++
                        continue
                    }

                    val source = groovyFile.readText()
                    val conv = gradleConverter.convertFile(source, relGroovy)
                    fileResults.add(conv.result)

                    when (conv.result.status) {
                        J2kFileStatus.CONVERTED -> convertedGradle++
                        J2kFileStatus.CONVERTED_WITH_NOTES -> notesGradle++
                        J2kFileStatus.SKIPPED -> skippedGradle++
                        J2kFileStatus.FAILED -> skippedGradle++
                    }

                    val content = conv.convertedContent
                    if (content != null) {
                        val ktsRelPath = relGroovy + ".kts"
                        val ktsFile = File(projectDir, ktsRelPath)
                        ktsFile.writeText(content)
                        groovyFile.delete()
                        journalEntries.add("CONVERTED|$ktsRelPath|$relGroovy")
                    }
                }
            }

            // 4. Versionskatalog erstellen/verwenden (wenn Schalter aktiv)
            var catalogCreated = false
            if (options.useVersionCatalog) {
                _progress.value = J2kProgress.ApplyingChanges("Versionskatalog (libs.versions.toml) erstellen...")
                val currentGradleFiles = if (options.convertGradle) {
                    plan.groovyFiles.map { it + ".kts" } + plan.ktsFiles
                } else {
                    plan.groovyFiles + plan.ktsFiles
                }

                val migration = catalogMigrator.migrateToCatalog(projectPath, currentGradleFiles)
                val catalogFile = File(projectDir, "gradle/libs.versions.toml")
                catalogFile.parentFile?.mkdirs()
                catalogFile.writeText(migration.catalogContent)
                journalEntries.add("CREATED|gradle/libs.versions.toml")

                for ((fileRel, updatedContent) in migration.updatedGradleFiles) {
                    val f = File(projectDir, fileRel)
                    if (f.exists()) {
                        f.writeText(updatedContent)
                        journalEntries.add("MODIFIED|$fileRel")
                    }
                }
                catalogCreated = true
            }

            // Journal schreiben
            journalFile.writeText(journalEntries.joinToString("\n"))

            // Bericht generieren
            val reportMd = generateReportMarkdown(
                projectPath = projectPath,
                timestamp = timestamp,
                backupZipPath = backupZipPath,
                journalPath = journalFile.absolutePath,
                convertedJava = convertedJava,
                notesJava = notesJava,
                skippedJava = skippedJava,
                failedJava = failedJava,
                convertedGradle = convertedGradle,
                catalogCreated = catalogCreated,
                results = fileResults
            )
            val reportFile = File(backupDir, "j2k-report-$timestamp.md")
            reportFile.writeText(reportMd)

            val finalReport = J2kReport(
                projectPath = projectPath,
                timestampEpochMillis = System.currentTimeMillis(),
                convertedJavaCount = convertedJava,
                notesJavaCount = notesJava,
                skippedJavaCount = skippedJava,
                failedJavaCount = failedJava,
                convertedGradleCount = convertedGradle,
                notesGradleCount = notesGradle,
                skippedGradleCount = skippedGradle,
                versionCatalogCreated = catalogCreated,
                backupPath = backupZipPath,
                journalPath = journalFile.absolutePath,
                fileResults = fileResults,
                markdownSummary = reportMd
            )

            _progress.value = J2kProgress.Completed(finalReport)
            return finalReport
        } catch (e: CancellationException) {
            journalFile.writeText(journalEntries.joinToString("\n"))
            undoMigration(projectPath, journalFile.absolutePath)
            _progress.value = J2kProgress.Failed("Migration durch Benutzer abgebrochen.")
            throw e
        } catch (e: Exception) {
            journalFile.writeText(journalEntries.joinToString("\n"))
            _progress.value = J2kProgress.Failed("Fehler bei Migration: ${e.message}")
            throw e
        }
    }

    override suspend fun undoMigration(projectPath: String, journalPath: String): Boolean {
        return backupRepo.rollbackWithJournal(projectPath, journalPath)
    }

    override fun cancelMigration() {
        isCancelled = true
    }

    private fun checkCancelled(journalFile: File, journalEntries: List<String>, projectPath: String) {
        if (isCancelled) {
            throw CancellationException("J2K Migration wurde abgebrochen.")
        }
    }

    private fun ensureKotlinInBuild(projectDir: File, journal: MutableList<String>) {
        val rootBuildGradle = File(projectDir, "build.gradle")
        val rootBuildKts = File(projectDir, "build.gradle.kts")

        if (rootBuildKts.exists()) {
            val content = rootBuildKts.readText()
            if (!content.contains("kotlin(\"jvm\")") && !content.contains("org.jetbrains.kotlin")) {
                // Hinzufügen im plugins Block
                val updated = content.replace("plugins {", "plugins {\n    kotlin(\"jvm\") version \"2.1.21\" apply false")
                rootBuildKts.writeText(updated)
                journal.add("MODIFIED|build.gradle.kts")
            }
        } else if (rootBuildGradle.exists()) {
            val content = rootBuildGradle.readText()
            if (!content.contains("kotlin-gradle-plugin") && !content.contains("org.jetbrains.kotlin")) {
                val updated = "buildscript {\n    dependencies {\n        classpath 'org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.21'\n    }\n}\n" + content
                rootBuildGradle.writeText(updated)
                journal.add("MODIFIED|build.gradle")
            }
        }
    }

    private fun generateReportMarkdown(
        projectPath: String,
        timestamp: String,
        backupZipPath: String,
        journalPath: String,
        convertedJava: Int,
        notesJava: Int,
        skippedJava: Int,
        failedJava: Int,
        convertedGradle: Int,
        catalogCreated: Boolean,
        results: List<J2kFileResult>
    ): String {
        return buildString {
            appendLine("# J2K Migrationsbericht ($timestamp)")
            appendLine()
            appendLine("> **Hinweis:** Automatisch umgewandelt, bitte prüfen!")
            appendLine()
            appendLine("## Zusammenfassung")
            appendLine("- **Projekt:** `$projectPath`")
            appendLine("- **Backup:** `$backupZipPath`")
            appendLine("- **Journal:** `$journalPath`")
            appendLine("- **Java-Dateien erfolgreich:** $convertedJava")
            appendLine("- **Java-Dateien mit Hinweisen:** $notesJava")
            appendLine("- **Java-Dateien übersprungen:** $skippedJava")
            appendLine("- **Java-Dateien fehlgeschlagen:** $failedJava")
            appendLine("- **Gradle-Dateien konvertiert:** $convertedGradle")
            appendLine("- **Versionskatalog erstellt:** ${if (catalogCreated) "Ja (`gradle/libs.versions.toml`)" else "Nein"}")
            appendLine()
            appendLine("## Dateiergebnisse")
            appendLine("| Datei | Status | Originalzeilen | Ergebniszeilen | Hinweise |")
            appendLine("|---|---|---|---|---|")
            for (res in results) {
                val notesStr = if (res.notes.isNotEmpty()) res.notes.joinToString("; ") else (res.errorMessage ?: "-")
                appendLine("| `${res.filePath}` | ${res.status} | ${res.originalLines} | ${res.convertedLines} | $notesStr |")
            }
        }
    }
}
