package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.J2kFileResult
import com.codeforge.core.domain.model.J2kOptions
import com.codeforge.core.domain.model.J2kPlan
import com.codeforge.core.domain.model.J2kProgress
import com.codeforge.core.domain.model.J2kReport
import com.codeforge.core.domain.model.J2kScanResult
import kotlinx.coroutines.flow.StateFlow

/** Ermittelt die Sprachverteilung eines Projekts. */
interface ProjectLanguageScanner {
    /** Zählt Java- und Kotlin-Dateien samt Zeilen. */
    suspend fun scanProject(projectPath: String): J2kScanResult
    /** Erstellt den Migrationsplan. */
    suspend fun createPlan(projectPath: String): J2kPlan
}

/** Wandelt Java-Quelltext in Kotlin um. */
interface JavaToKotlinConverter {
    /** Konvertiert eine Java-Datei; [filePath] dient der Meldung und Zuordnung. */
    suspend fun convertFile(javaSource: String, filePath: String): J2kFileResultWithContent
}

/** Ergebnis einer Konvertierung mit dem neuen Inhalt (`null` bei Fehler oder Überspringen). */
data class J2kFileResultWithContent(
    val result: J2kFileResult,
    val convertedContent: String?
)

/** Wandelt Groovy-Gradle-Dateien in die Kotlin-DSL um. */
interface GradleDslConverter {
    /** Konvertiert eine Groovy-Gradle-Datei. */
    suspend fun convertFile(groovySource: String, filePath: String): J2kFileResultWithContent
}

/** Verlagert Abhängigkeiten in einen Version Catalog. */
interface VersionCatalogMigrator {
    /** Erzeugt den Katalog aus den Gradle-Dateien und passt diese an. */
    suspend fun migrateToCatalog(
        projectPath: String,
        gradleFiles: List<String>
    ): VersionCatalogMigrationResult
}

/** Ergebnis der Katalog-Migration: Katalog-Inhalt, geänderte Gradle-Dateien (Pfad auf Inhalt) und Hinweise. */
data class VersionCatalogMigrationResult(
    val catalogContent: String,
    val updatedGradleFiles: Map<String, String>,
    val notes: List<String>
)

/** Sichert das Projekt vor der Migration. */
interface ProjectBackupRepository {
    /** Erstellt ein ZIP-Backup und liefert dessen Pfad. */
    suspend fun createBackup(projectPath: String, includeGit: Boolean): String
    /** Stellt das Projekt aus dem Backup wieder her. */
    suspend fun restoreBackup(projectPath: String, backupZipPath: String): Boolean
    /** Nimmt die Änderungen anhand des Journals zurück. */
    suspend fun rollbackWithJournal(projectPath: String, journalPath: String): Boolean
}

/** Fassade der Java-nach-Kotlin-Migration. */
interface J2kRepository {
    /** Aktueller Fortschritt. */
    val progress: StateFlow<J2kProgress>
    /** Scannt das Projekt. */
    suspend fun scanProject(projectPath: String): J2kScanResult
    /** Erstellt den Migrationsplan. */
    suspend fun createPlan(projectPath: String): J2kPlan
    /** Führt die Migration mit den [options] aus und liefert den Bericht. */
    suspend fun executeMigration(projectPath: String, options: J2kOptions): J2kReport
    /** Nimmt die Migration anhand des Journals zurück. */
    suspend fun undoMigration(projectPath: String, journalPath: String): Boolean
    /** Bricht eine laufende Migration ab. */
    fun cancelMigration()
}
