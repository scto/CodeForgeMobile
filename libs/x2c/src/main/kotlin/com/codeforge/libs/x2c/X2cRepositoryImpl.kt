package com.codeforge.libs.x2c

import com.codeforge.core.domain.model.X2cOptions
import com.codeforge.core.domain.model.X2cPlan
import com.codeforge.core.domain.model.X2cProgress
import com.codeforge.core.domain.model.X2cReport
import com.codeforge.core.domain.repository.ComposeBuildEnabler
import com.codeforge.core.domain.repository.ComposeMigrationScanner
import com.codeforge.core.domain.repository.X2cBackupRepository
import com.codeforge.core.domain.repository.X2cRepository
import com.codeforge.libs.x2c.backup.X2cBackupRepositoryImpl
import com.codeforge.libs.x2c.build.ComposeBuildEnablerImpl
import com.codeforge.libs.x2c.scanner.ComposeMigrationScannerImpl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class X2cRepositoryImpl(
    private val scanner: ComposeMigrationScanner = ComposeMigrationScannerImpl(),
    private val backupRepo: X2cBackupRepository = X2cBackupRepositoryImpl(),
    private val buildEnabler: ComposeBuildEnabler = ComposeBuildEnablerImpl()
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

        var backupZipPath = "$projectPath/Backup"
        if (options.createBackup) {
            _progress.value = X2cProgress.CreatingBackup(projectPath)
            backupZipPath = backupRepo.createBackup(projectPath, options.includeGitInBackup)
        }

        // Build für Compose vorbereiten
        _progress.value = X2cProgress.PreparingBuild("Compose-Unterstützung im Build wird eingerichtet...")
        val hasConstraintLayout = plan.layouts.any {
            it.rootTag == "ConstraintLayout" || it.rootTag.endsWith(".ConstraintLayout")
        }
        val buildResult = buildEnabler.enableComposeInProject(
            projectPath = projectPath,
            useVersionCatalog = options.useVersionCatalog,
            wireActivities = options.wireActivities,
            hasConstraintLayout = hasConstraintLayout
        )
        if (buildResult.isFailure) {
            val error = buildResult.exceptionOrNull()?.message ?: "Build-Vorbereitung fehlgeschlagen"
            _progress.value = X2cProgress.Failed(error)
            throw IllegalStateException(error)
        }

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
            backupPath = backupZipPath,
            journalPath = "$projectPath/Backup/x2c-journal.json",
            fileResults = emptyList(),
            markdownSummary = "Migration vorbereitet (Task 52.4)"
        )
        _progress.value = X2cProgress.Completed(report)
        return report
    }

    override suspend fun undoMigration(projectPath: String, journalPath: String): Boolean {
        _progress.value = X2cProgress.RollingBack("Migration wird zurückgerollt...")
        val result = backupRepo.rollbackWithJournal(projectPath, journalPath)
        _progress.value = X2cProgress.Idle
        return result
    }

    override fun cancelMigration() {
        isCancelled = true
        _progress.value = X2cProgress.Failed("Abgebrochen durch Benutzer")
    }
}
