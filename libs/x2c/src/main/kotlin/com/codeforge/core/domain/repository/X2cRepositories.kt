package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ComposeScanResult
import com.codeforge.core.domain.model.LayoutUsage
import com.codeforge.core.domain.model.X2cOptions
import com.codeforge.core.domain.model.X2cPlan
import com.codeforge.core.domain.model.X2cProgress
import com.codeforge.core.domain.model.X2cReport
import kotlinx.coroutines.flow.StateFlow

data class GeneratedComposeFile(
    val packageName: String,
    val composableName: String,
    val targetRelativePath: String,
    val sourceContent: String,
    val todoCount: Int = 0,
    val notes: List<String> = emptyList()
)

interface LayoutToComposeGenerator {
    suspend fun generateComposable(
        xmlContent: String,
        layoutName: String,
        targetPackage: String,
        touchedViewIds: List<String> = emptyList()
    ): Result<GeneratedComposeFile>
}

interface LayoutUsageAnalyzer {
    suspend fun findUsages(projectPath: String, layoutName: String): List<LayoutUsage>

    /**
     * Wie [findUsages] für viele Layouts in einem Durchlauf (Projekt wird nur einmal gelesen).
     * [layoutIds]: Layoutname -> IDs, die das Layout deklariert (für `referencedViewIds`).
     */
    suspend fun findAllUsages(projectPath: String, layoutIds: Map<String, Set<String>>): Map<String, List<LayoutUsage>>
    suspend fun findRemainingViewReferences(projectPath: String): List<String>
}

data class PrerequisitesCheckResult(
    val isMinSdkSatisfied: Boolean,
    val detectedMinSdk: Int?,
    val requiredMinSdk: Int = 21,
    val isJavaVersionSatisfied: Boolean = true,
    val detectedJavaVersion: String? = null,
    val detectedKotlinVersion: String? = null,
    val detectedAgpVersion: String? = null,
    val isAgpSatisfied: Boolean = true,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

data class BuildEnableResult(
    val modifiedFiles: List<String>,
    val notes: List<String>,
    val addedDependencies: List<String>,
    val kotlinVersionUsed: String,
    val composeBomUsed: String
)

interface ComposeBuildEnabler {
    suspend fun checkPrerequisites(projectPath: String): Result<PrerequisitesCheckResult>
    suspend fun enableComposeInProject(
        projectPath: String,
        useVersionCatalog: Boolean,
        wireActivities: Boolean,
        hasConstraintLayout: Boolean,
        kotlinVersionOverride: String? = null
    ): Result<BuildEnableResult>
}

interface ComposeMigrationScanner {
    suspend fun scanProject(projectPath: String): ComposeScanResult
    suspend fun createPlan(projectPath: String, preselectedLayout: String? = null): X2cPlan
}

interface X2cBackupRepository {
    suspend fun createBackup(projectPath: String, includeGit: Boolean = true): String
    suspend fun restoreBackup(projectPath: String, backupZipPath: String): Boolean
    suspend fun restoreFile(projectPath: String, backupZipPath: String, relativeFilePath: String): Boolean
    suspend fun rollbackWithJournal(projectPath: String, journalPath: String): Boolean
}

interface X2cRepository {
    val progress: StateFlow<X2cProgress>
    suspend fun analyzeProject(projectPath: String): X2cPlan
    suspend fun executeMigration(projectPath: String, options: X2cOptions): X2cReport
    suspend fun undoMigration(projectPath: String, journalPath: String): Boolean
    fun cancelMigration()
}
