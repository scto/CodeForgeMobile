package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.J2kFileResult
import com.codeforge.core.domain.model.J2kOptions
import com.codeforge.core.domain.model.J2kPlan
import com.codeforge.core.domain.model.J2kProgress
import com.codeforge.core.domain.model.J2kReport
import com.codeforge.core.domain.model.J2kScanResult
import kotlinx.coroutines.flow.StateFlow

interface ProjectLanguageScanner {
    suspend fun scanProject(projectPath: String): J2kScanResult
    suspend fun createPlan(projectPath: String): J2kPlan
}

interface JavaToKotlinConverter {
    suspend fun convertFile(javaSource: String, filePath: String): J2kFileResultWithContent
}

data class J2kFileResultWithContent(
    val result: J2kFileResult,
    val convertedContent: String?
)

interface GradleDslConverter {
    suspend fun convertFile(groovySource: String, filePath: String): J2kFileResultWithContent
}

interface VersionCatalogMigrator {
    suspend fun migrateToCatalog(
        projectPath: String,
        gradleFiles: List<String>
    ): VersionCatalogMigrationResult
}

data class VersionCatalogMigrationResult(
    val catalogContent: String,
    val updatedGradleFiles: Map<String, String>,
    val notes: List<String>
)

interface ProjectBackupRepository {
    suspend fun createBackup(projectPath: String, includeGit: Boolean): String
    suspend fun restoreBackup(projectPath: String, backupZipPath: String): Boolean
    suspend fun rollbackWithJournal(projectPath: String, journalPath: String): Boolean
}

interface J2kRepository {
    val progress: StateFlow<J2kProgress>
    suspend fun scanProject(projectPath: String): J2kScanResult
    suspend fun createPlan(projectPath: String): J2kPlan
    suspend fun executeMigration(projectPath: String, options: J2kOptions): J2kReport
    suspend fun undoMigration(projectPath: String, journalPath: String): Boolean
    fun cancelMigration()
}
