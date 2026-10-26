package com.codeforge.feature.x2c

import com.codeforge.core.domain.model.UndoConflictPolicy
import com.codeforge.core.domain.model.UndoResult
import com.codeforge.core.domain.model.X2cBuildStepResult
import com.codeforge.core.domain.model.X2cBuildStepStatus
import com.codeforge.core.domain.model.X2cFileResult
import com.codeforge.core.domain.model.X2cFileStatus
import com.codeforge.core.domain.model.X2cOptions
import com.codeforge.core.domain.model.X2cPlan
import com.codeforge.core.domain.model.X2cProgress
import com.codeforge.core.domain.model.X2cReport
import com.codeforge.core.domain.repository.X2cBuildChecker
import com.codeforge.core.domain.repository.X2cRepository
import com.codeforge.core.navigation.JumpTarget
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.feature.x2c.run.X2cRunNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun testReport(
    reportPath: String? = "/project/Backup/x2c-report.md",
    buildSteps: List<X2cBuildStepResult> = emptyList()
) = X2cReport(
    projectPath = "/project",
    timestampEpochMillis = 123456789L,
    generatedCount = 2,
    generatedWithNotesCount = 1,
    skippedCount = 1,
    failedCount = 0,
    totalTodos = 1,
    stage2WiredCount = 0,
    stage2SkippedCount = 0,
    themeGenerated = true,
    removedXmlCount = 0,
    backupPath = "/project/Backup/backup.zip",
    journalPath = "/project/Backup/journal.json",
    fileResults = listOf(
        X2cFileResult("res/layout/activity_main.xml", "MainScreen.kt", X2cFileStatus.GENERATED),
        X2cFileResult("res/layout/item_user.xml", "UserItem.kt", X2cFileStatus.GENERATED_WITH_NOTES, todoCount = 1)
    ),
    buildSteps = buildSteps,
    reportPath = reportPath
)

internal class FakeX2cRepository : X2cRepository {
    private val _progress = MutableStateFlow<X2cProgress>(X2cProgress.Idle)
    override val progress: StateFlow<X2cProgress> = _progress.asStateFlow()

    var reportToReturn: X2cReport = testReport()

    /** Hält `executeMigration` an, bis der Test es freigibt. */
    var gate: CompletableDeferred<Unit>? = null
    var lastOptions: X2cOptions? = null
    var cancelCalls = 0

    var conflictPaths: List<String> = emptyList()
    val conflictAnswers = mutableListOf<UndoConflictPolicy>()
    var undoResult: Result<UndoResult> = Result.success(UndoResult(restored = listOf("a.xml", "b.xml"), deleted = listOf("A.kt")))

    fun emit(progress: X2cProgress) {
        _progress.value = progress
    }

    override suspend fun analyzeProject(projectPath: String): X2cPlan = error("Not used in fake")

    override suspend fun executeMigration(projectPath: String, options: X2cOptions): X2cReport {
        lastOptions = options
        gate?.await()
        return reportToReturn
    }

    override suspend fun undoMigration(projectPath: String, journalPath: String): Boolean = true

    override suspend fun undoMigration(
        projectPath: String,
        journalPath: String,
        onConflict: suspend (relativePath: String) -> UndoConflictPolicy
    ): Result<UndoResult> {
        conflictPaths.forEach { conflictAnswers += onConflict(it) }
        return undoResult
    }

    override fun cancelMigration() {
        cancelCalls++
        // Wie die echte Umsetzung: kontrolliert beenden, nachdem zurückgenommen wurde.
        gate?.completeExceptionally(CancellationException("Abgebrochen"))
    }
}

internal class FakeBuildChecker : X2cBuildChecker {
    var result = X2cBuildStepResult(listOf("assembleDebug"), X2cBuildStepStatus.SUCCESS)
    var lastTasks: List<String>? = null
    var lastProject: String? = null

    override suspend fun runTasks(
        projectPath: String,
        tasks: List<String>,
        onOutput: (String) -> Unit,
        isCancelled: () -> Boolean
    ): X2cBuildStepResult {
        lastProject = projectPath
        lastTasks = tasks
        return result
    }
}

internal class FakeRunNotifier : X2cRunNotifier {
    var startedCount = 0
    var finishedCount = 0
    var lastPhase: X2cPhase? = null
    var lastPercent: Int? = null
    var onCancel: (() -> Unit)? = null

    override fun started(onCancelRequested: () -> Unit) {
        startedCount++
        onCancel = onCancelRequested
    }

    override fun update(phase: X2cPhase?, percent: Int?) {
        lastPhase = phase
        lastPercent = percent
    }

    override fun finished() {
        finishedCount++
    }
}

internal class FakeOpenFileRequestBridge : OpenFileRequestBridge {
    var lastOpenedPath: String? = null
    private val _openRequests = MutableSharedFlow<String>()
    override val openRequests: SharedFlow<String> = _openRequests
    private val _jumpRequests = MutableSharedFlow<JumpTarget>()
    override val jumpRequests: SharedFlow<JumpTarget> = _jumpRequests

    override suspend fun requestOpen(path: String) {
        lastOpenedPath = path
    }

    override suspend fun requestOpenAt(path: String, line: Int, column: Int, length: Int) {
        lastOpenedPath = path
    }
}
