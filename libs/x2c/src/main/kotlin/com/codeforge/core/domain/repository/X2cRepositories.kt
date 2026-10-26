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
    suspend fun findRemainingViewReferences(projectPath: String): List<String>
}

data class BuildEnableResult(
    val modifiedFiles: List<String>,
    val notes: List<String>,
    val addedDependencies: List<String>,
    val kotlinVersionUsed: String,
    val composeBomUsed: String
)

interface ComposeBuildEnabler {
    suspend fun enableComposeInProject(
        projectPath: String,
        useVersionCatalog: Boolean,
        wireActivities: Boolean,
        hasConstraintLayout: Boolean
    ): Result<BuildEnableResult>
}

interface ComposeMigrationScanner {
    suspend fun scanProject(projectPath: String): ComposeScanResult
    suspend fun createPlan(projectPath: String, preselectedLayout: String? = null): X2cPlan
}

interface X2cRepository {
    val progress: StateFlow<X2cProgress>
    suspend fun analyzeProject(projectPath: String): X2cPlan
    suspend fun executeMigration(projectPath: String, options: X2cOptions): X2cReport
    suspend fun undoMigration(projectPath: String, journalPath: String): Boolean
    fun cancelMigration()
}
