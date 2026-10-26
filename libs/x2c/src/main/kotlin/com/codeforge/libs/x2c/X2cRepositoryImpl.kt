package com.codeforge.libs.x2c

import com.codeforge.core.domain.model.X2cOptions
import com.codeforge.core.domain.model.X2cPlan
import com.codeforge.core.domain.model.X2cProgress
import com.codeforge.core.domain.model.X2cReport
import com.codeforge.core.domain.repository.ComposeMigrationScanner
import com.codeforge.core.domain.repository.X2cRepository
import com.codeforge.libs.x2c.scanner.ComposeMigrationScannerImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class X2cRepositoryImpl(
    private val scanner: ComposeMigrationScanner = ComposeMigrationScannerImpl()
) : X2cRepository {

    private val _progress = MutableStateFlow<X2cProgress>(X2cProgress.Idle)
    override val progress: StateFlow<X2cProgress> = _progress.asStateFlow()

    @Volatile
    private var isCancelled = false

    override suspend fun analyzeProject(projectPath: String): X2cPlan {
        _progress.value = X2cProgress.Analyzing("Layouts werden analysiert...")
        val plan = scanner.createPlan(projectPath)
        _progress.value = X2cProgress.Idle
        return plan
    }

    override suspend fun executeMigration(projectPath: String, options: X2cOptions): X2cReport {
        isCancelled = false
        val plan = scanner.createPlan(projectPath)
        val report = X2cReport(
            projectPath = projectPath,
            timestampEpochMillis = System.currentTimeMillis(),
            generatedCount = 0,
            generatedWithNotesCount = 0,
            skippedCount = plan.totalLayouts,
            failedCount = 0,
            totalTodos = 0,
            stage2WiredCount = 0,
            stage2SkippedCount = 0,
            themeGenerated = options.generateTheme,
            removedXmlCount = 0,
            backupPath = "$projectPath/Backup",
            journalPath = "$projectPath/Backup/x2c-journal.json",
            fileResults = emptyList(),
            markdownSummary = "Migration vorbereitet (Task 52.2)"
        )
        _progress.value = X2cProgress.Completed(report)
        return report
    }

    override suspend fun undoMigration(projectPath: String, journalPath: String): Boolean {
        return true
    }

    override fun cancelMigration() {
        isCancelled = true
        _progress.value = X2cProgress.Failed("Abgebrochen durch Benutzer")
    }
}
