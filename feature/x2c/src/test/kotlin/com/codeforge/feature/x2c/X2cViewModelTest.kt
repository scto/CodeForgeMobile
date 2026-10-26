package com.codeforge.feature.x2c

import androidx.datastore.core.DataStore
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.domain.model.ComposeScanResult
import com.codeforge.core.domain.model.LayoutClassification
import com.codeforge.core.domain.model.LayoutPlanEntry
import com.codeforge.core.domain.model.UndoConflictPolicy
import com.codeforge.core.domain.model.X2cBuildStepResult
import com.codeforge.core.domain.model.X2cBuildStepStatus
import com.codeforge.core.domain.model.X2cPlan
import com.codeforge.core.domain.model.X2cProgress
import com.codeforge.core.resources.R
import com.codeforge.core.resources.UiText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import com.codeforge.core.domain.repository.ComposeMigrationScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class X2cViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeScanner: FakeComposeMigrationScanner
    private lateinit var fakeRepository: FakeX2cRepository
    private lateinit var fakeDataStore: FakeDataStore
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var fakeBridge: FakeOpenFileRequestBridge
    private lateinit var fakeChecker: FakeBuildChecker
    private lateinit var fakeNotifier: FakeRunNotifier
    private lateinit var viewModel: X2cViewModel

    private class FakeDataStore : DataStore<AppSettings> {
        private val _flow = MutableStateFlow(AppSettings.getDefaultInstance())
        override val data: Flow<AppSettings> = _flow.asStateFlow()

        override suspend fun updateData(transform: suspend (t: AppSettings) -> AppSettings): AppSettings {
            val updated = transform(_flow.value)
            _flow.value = updated
            return updated
        }
    }

    private class FakeComposeMigrationScanner : ComposeMigrationScanner {
        var scanResult = ComposeScanResult(
            projectPath = "/project",
            isAndroidProject = true,
            layoutCount = 3,
            moduleCount = 1,
            layoutFiles = listOf(
                "app/src/main/res/layout/activity_main.xml",
                "app/src/main/res/layout/item_user.xml",
                "app/src/main/res/layout/widget_clock.xml"
            ),
            hasExistingCompose = false,
            qualifiesForAutoPrompt = true
        )

        var plan = X2cPlan(
            projectPath = "/project",
            totalLayouts = 3,
            automaticCount = 1,
            withNotesCount = 1,
            notConvertibleCount = 1,
            layouts = listOf(
                LayoutPlanEntry(
                    relativePath = "app/src/main/res/layout/activity_main.xml",
                    layoutName = "activity_main",
                    classification = LayoutClassification.AUTOMATIC,
                    reason = "Vollständig automatisch",
                    rootTag = "LinearLayout",
                    stage2Eligible = true
                ),
                LayoutPlanEntry(
                    relativePath = "app/src/main/res/layout/item_user.xml",
                    layoutName = "item_user",
                    classification = LayoutClassification.WITH_NOTES,
                    reason = "Enthält Custom View",
                    rootTag = "FrameLayout"
                ),
                LayoutPlanEntry(
                    relativePath = "app/src/main/res/layout/widget_clock.xml",
                    layoutName = "widget_clock",
                    classification = LayoutClassification.NOT_CONVERTIBLE,
                    reason = "RemoteViews Widget",
                    rootTag = "LinearLayout"
                )
            ),
            stage2CandidatesCount = 1,
            detectedTheme = "Theme.Material3",
            hasVersionCatalog = true
        )

        override suspend fun scanProject(projectPath: String): ComposeScanResult = scanResult

        override suspend fun createPlan(projectPath: String, preselectedLayout: String?): X2cPlan = plan
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeScanner = FakeComposeMigrationScanner()
        fakeRepository = FakeX2cRepository()
        fakeDataStore = FakeDataStore()
        settingsRepository = SettingsRepository(fakeDataStore)
        fakeBridge = FakeOpenFileRequestBridge()
        fakeChecker = FakeBuildChecker()
        fakeNotifier = FakeRunNotifier()
        viewModel = X2cViewModel(fakeScanner, fakeRepository, settingsRepository, fakeBridge, fakeChecker, fakeNotifier)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `scanProject opens dialog and preselects convertible layouts`() = runTest {
        viewModel.handleIntent(X2cIntent.ScanProject("/project"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Dialog should be shown", state.showDialog)
        assertNotNull(state.plan)
        assertEquals(3, state.plan!!.totalLayouts)

        // Only AUTOMATIC and WITH_NOTES should be preselected, NOT_CONVERTIBLE excluded
        assertEquals(2, state.selectedLayouts.size)
        assertTrue(state.selectedLayouts.contains("app/src/main/res/layout/activity_main.xml"))
        assertTrue(state.selectedLayouts.contains("app/src/main/res/layout/item_user.xml"))
        assertFalse(state.selectedLayouts.contains("app/src/main/res/layout/widget_clock.xml"))

        assertTrue("Version catalog toggle should be on if catalog exists", state.useVersionCatalog)
        assertTrue("Theme toggle should be on if theme detected", state.generateTheme)
    }

    @Test
    fun `scanProject with manualTrigger opens dialog even if auto prompt doesn't qualify`() = runTest {
        fakeScanner.scanResult = fakeScanner.scanResult.copy(
            hasExistingCompose = true,
            qualifiesForAutoPrompt = false
        )

        // Automatic scan should not open dialog
        viewModel.handleIntent(X2cIntent.ScanProject("/project", manualTrigger = false))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showDialog)

        // Manual trigger should open dialog
        viewModel.handleIntent(X2cIntent.ScanProject("/project", manualTrigger = true))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showDialog)
    }

    @Test
    fun `scanProject with preselectedLayout selects only that layout`() = runTest {
        viewModel.handleIntent(
            X2cIntent.ScanProject(
                projectPath = "/project",
                manualTrigger = true,
                preselectedLayout = "app/src/main/res/layout/item_user.xml"
            )
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.showDialog)
        assertEquals(1, state.selectedLayouts.size)
        assertTrue(state.selectedLayouts.contains("app/src/main/res/layout/item_user.xml"))
    }

    @Test
    fun `dismiss with dontAskAgain saves preference to settings and suppresses auto prompt`() = runTest {
        viewModel.handleIntent(X2cIntent.ScanProject("/project"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showDialog)

        // User clicks "Nicht mehr fragen"
        viewModel.handleIntent(X2cIntent.DismissDialog(dontAskAgain = true))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.showDialog)

        // Subsequent auto-prompt is suppressed
        viewModel.handleIntent(X2cIntent.ScanProject("/project", manualTrigger = false))
        advanceUntilIdle()
        assertFalse("Auto prompt must be suppressed after dontAskAgain", viewModel.uiState.value.showDialog)

        // Manual trigger still opens dialog
        viewModel.handleIntent(X2cIntent.ScanProject("/project", manualTrigger = true))
        advanceUntilIdle()
        assertTrue("Manual trigger should always work", viewModel.uiState.value.showDialog)
    }

    @Test
    fun `layout selection toggling and select all deselect all`() = runTest {
        viewModel.handleIntent(X2cIntent.ScanProject("/project"))
        advanceUntilIdle()

        // Deselect all
        viewModel.handleIntent(X2cIntent.DeselectAllLayouts)
        assertTrue(viewModel.uiState.value.selectedLayouts.isEmpty())

        // Select one
        viewModel.handleIntent(X2cIntent.ToggleLayoutSelection("app/src/main/res/layout/activity_main.xml"))
        assertEquals(1, viewModel.uiState.value.selectedLayouts.size)

        // Select all (should select only convertible ones)
        viewModel.handleIntent(X2cIntent.SelectAllLayouts)
        assertEquals(2, viewModel.uiState.value.selectedLayouts.size)
        assertFalse(viewModel.uiState.value.selectedLayouts.contains("app/src/main/res/layout/widget_clock.xml"))
    }

    @Test
    fun `search query filters displayed layouts`() = runTest {
        viewModel.handleIntent(X2cIntent.ScanProject("/project"))
        advanceUntilIdle()

        viewModel.handleIntent(X2cIntent.SetSearchQuery("item"))
        assertEquals(1, viewModel.uiState.value.filteredLayouts.size)
        assertEquals("item_user", viewModel.uiState.value.filteredLayouts[0].layoutName)

        viewModel.handleIntent(X2cIntent.SetSearchQuery(""))
        assertEquals(3, viewModel.uiState.value.filteredLayouts.size)
    }

    @Test
    fun `openFile requests navigation via bridge`() = runTest {
        viewModel.handleIntent(X2cIntent.OpenFile("app/src/main/res/layout/activity_main.xml"))
        advanceUntilIdle()

        assertEquals("app/src/main/res/layout/activity_main.xml", fakeBridge.lastOpenedPath)
    }

    // --- Task 52.11 ---

    private fun kotlinx.coroutines.test.TestScope.collectEffects(): MutableList<X2cEffect> {
        val effects = mutableListOf<X2cEffect>()
        // Sofort abonnieren (Unconfined): `effects` ist ein SharedFlow ohne Puffer, Emissionen ohne Abonnent gingen verloren.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect { effects += it } }
        return effects
    }

    private fun kotlinx.coroutines.test.TestScope.scanAndStart() {
        viewModel.handleIntent(X2cIntent.ScanProject("/project"))
        advanceUntilIdle()
        viewModel.handleIntent(X2cIntent.StartMigration)
    }

    @Test
    fun `startMigration runs, reports and keeps the notifier in sync`() = runTest {
        viewModel.handleIntent(X2cIntent.ScanProject("/project"))
        advanceUntilIdle()
        viewModel.handleIntent(X2cIntent.SetBuildBaselineFirst(true))
        viewModel.handleIntent(X2cIntent.StartMigration)
        assertTrue(viewModel.uiState.value.isConverting)
        assertFalse(viewModel.uiState.value.showDialog)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isConverting)
        assertTrue(state.showResultScreen)
        assertNotNull(state.report)
        assertEquals(1, fakeNotifier.startedCount)
        assertEquals(1, fakeNotifier.finishedCount)
        assertTrue("baseline option is passed on", fakeRepository.lastOptions!!.buildBaselineFirst)
    }

    @Test
    fun `second start while running is ignored`() = runTest {
        fakeRepository.gate = CompletableDeferred()
        scanAndStart()
        advanceUntilIdle()
        viewModel.handleIntent(X2cIntent.StartMigration)
        advanceUntilIdle()
        assertEquals(1, fakeNotifier.startedCount)
        fakeRepository.gate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `progress drives phase tracker and notification`() = runTest {
        fakeRepository.gate = CompletableDeferred()
        scanAndStart()
        advanceUntilIdle()

        fakeRepository.emit(X2cProgress.GeneratingComposables(current = 1, total = 2, currentLayout = "activity_main"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(X2cPhase.LAYOUTS, state.phaseTracker.active)
        assertEquals(X2cPhase.LAYOUTS, fakeNotifier.lastPhase)
        assertNotNull(fakeNotifier.lastPercent)

        fakeRepository.gate!!.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `cancel informs repository, waits for rollback and then ends the run`() = runTest {
        fakeRepository.gate = CompletableDeferred()
        val effects = collectEffects()
        scanAndStart()
        advanceUntilIdle()

        viewModel.handleIntent(X2cIntent.CancelMigration)
        assertEquals(1, fakeRepository.cancelCalls)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isConverting)
        assertFalse(state.showResultScreen)
        assertNull(state.report)
        assertEquals(1, fakeNotifier.finishedCount)
        assertTrue(effects.contains(X2cEffect.ShowMessage(UiText.of(R.string.x2c_msg_cancelled))))
    }

    @Test
    fun `cancel from the notification behaves like cancel in the screen`() = runTest {
        fakeRepository.gate = CompletableDeferred()
        scanAndStart()
        advanceUntilIdle()

        fakeNotifier.onCancel!!.invoke()
        advanceUntilIdle()

        assertEquals(1, fakeRepository.cancelCalls)
        assertFalse(viewModel.uiState.value.isConverting)
    }

    @Test
    fun `cancel without a run does nothing`() = runTest {
        viewModel.handleIntent(X2cIntent.CancelMigration)
        advanceUntilIdle()
        assertEquals(0, fakeRepository.cancelCalls)
    }

    @Test
    fun `baseline option needs the build check`() = runTest {
        viewModel.handleIntent(X2cIntent.ScanProject("/project"))
        advanceUntilIdle()
        viewModel.handleIntent(X2cIntent.SetBuildBaselineFirst(true))
        assertTrue(viewModel.uiState.value.buildBaselineFirst)
        viewModel.handleIntent(X2cIntent.SetRunBuildCheck(false))
        assertFalse(viewModel.uiState.value.buildBaselineFirst)
        viewModel.handleIntent(X2cIntent.SetBuildBaselineFirst(true))
        assertFalse(viewModel.uiState.value.buildBaselineFirst)
    }

    @Test
    fun `undo asks about changed files and passes the answer on`() = runTest {
        fakeRepository.conflictPaths = listOf("app/src/main/kotlin/Foo.kt")
        val effects = collectEffects()
        scanAndStart()
        advanceUntilIdle()

        viewModel.handleIntent(X2cIntent.RestoreBackup)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isUndoing)
        assertEquals("app/src/main/kotlin/Foo.kt", viewModel.uiState.value.undoConflictPath)

        viewModel.handleIntent(X2cIntent.ResolveUndoConflict(UndoConflictPolicy.SAVE_AS_ORIG))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(UndoConflictPolicy.SAVE_AS_ORIG), fakeRepository.conflictAnswers)
        assertFalse(state.isUndoing)
        assertNull(state.undoConflictPath)
        assertEquals(2, state.undoResult!!.restored.size)
        assertTrue(effects.contains(X2cEffect.ShowMessage(UiText.of(R.string.x2c_undo_done, 2, 1))))
        assertEquals(2, fakeNotifier.finishedCount)
    }

    @Test
    fun `undo failure is reported and ends the run`() = runTest {
        fakeRepository.undoResult = Result.failure(IllegalStateException("Journal fehlt"))
        val effects = collectEffects()
        scanAndStart()
        advanceUntilIdle()

        viewModel.handleIntent(X2cIntent.RestoreBackup)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isUndoing)
        assertNull(viewModel.uiState.value.undoResult)
        assertTrue(effects.contains(X2cEffect.ShowMessage(UiText.of(R.string.x2c_undo_failed, "Journal fehlt"))))
    }

    @Test
    fun `run build again uses the tasks of the first real build step`() = runTest {
        fakeRepository.reportToReturn = testReport(
            buildSteps = listOf(
                X2cBuildStepResult(listOf(":app:compileDebugKotlin"), X2cBuildStepStatus.SUCCESS),
                X2cBuildStepResult(listOf(":app:assembleDebug"), X2cBuildStepStatus.SKIPPED)
            )
        )
        fakeChecker.result = X2cBuildStepResult(listOf(":app:compileDebugKotlin"), X2cBuildStepStatus.FAILED)
        val effects = collectEffects()
        scanAndStart()
        advanceUntilIdle()

        viewModel.handleIntent(X2cIntent.RunBuildAgain)
        advanceUntilIdle()

        assertEquals(listOf(":app:compileDebugKotlin"), fakeChecker.lastTasks)
        assertEquals("/project", fakeChecker.lastProject)
        assertEquals(X2cBuildStepStatus.FAILED, viewModel.uiState.value.rerunBuildResult?.status)
        assertFalse(viewModel.uiState.value.isRerunningBuild)
        assertTrue(effects.contains(X2cEffect.ShowMessage(UiText.of(R.string.x2c_msg_build_failed))))
    }

    @Test
    fun `run build again falls back to assembleDebug without earlier steps`() = runTest {
        scanAndStart()
        advanceUntilIdle()
        viewModel.handleIntent(X2cIntent.RunBuildAgain)
        advanceUntilIdle()
        assertEquals(listOf("assembleDebug"), fakeChecker.lastTasks)
    }

    @Test
    fun `open report without report file shows a message`() = runTest {
        fakeRepository.reportToReturn = testReport(reportPath = null)
        val effects = collectEffects()
        scanAndStart()
        advanceUntilIdle()

        viewModel.handleIntent(X2cIntent.OpenReport)
        advanceUntilIdle()

        assertNull(fakeBridge.lastOpenedPath)
        assertTrue(effects.contains(X2cEffect.ShowMessage(UiText.of(R.string.x2c_msg_no_report))))
    }

    @Test
    fun `open report and compose file resolve paths against the project`() = runTest {
        scanAndStart()
        advanceUntilIdle()

        viewModel.handleIntent(X2cIntent.OpenReport)
        advanceUntilIdle()
        assertEquals("/project/Backup/x2c-report.md", fakeBridge.lastOpenedPath)

        viewModel.handleIntent(X2cIntent.OpenFirstComposeFile)
        advanceUntilIdle()
        assertEquals("/project/MainScreen.kt", fakeBridge.lastOpenedPath)
    }

    @Test
    fun `backup folder message names the folder, not the zip`() = runTest {
        val effects = collectEffects()
        scanAndStart()
        advanceUntilIdle()
        viewModel.handleIntent(X2cIntent.ShowBackupFolder)
        advanceUntilIdle()
        assertTrue(effects.contains(X2cEffect.ShowMessage(UiText.of(R.string.x2c_msg_backup_folder, "/project/Backup"))))
    }

    @Test
    fun `result can be closed and shown again, filters apply`() = runTest {
        scanAndStart()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showResultScreen)

        viewModel.handleIntent(X2cIntent.DismissResult)
        assertFalse(viewModel.uiState.value.showResultScreen)
        assertNotNull(viewModel.uiState.value.report)

        viewModel.handleIntent(X2cIntent.ShowResult)
        assertTrue(viewModel.uiState.value.showResultScreen)

        assertEquals(2, viewModel.uiState.value.filteredResults.size)
        viewModel.handleIntent(X2cIntent.SetResultFilter(X2cResultFilter.TODOS))
        assertEquals(listOf("UserItem.kt"), viewModel.uiState.value.filteredResults.map { it.outputComposePath })
        viewModel.handleIntent(X2cIntent.SetResultFilter(X2cResultFilter.PROBLEMS))
        assertTrue(viewModel.uiState.value.filteredResults.isEmpty())
    }

    @Test
    fun `failed migration reports error and ends the run`() = runTest {
        val failing = object : com.codeforge.core.domain.repository.X2cRepository by fakeRepository {
            override suspend fun executeMigration(projectPath: String, options: com.codeforge.core.domain.model.X2cOptions) =
                throw IllegalStateException("kaputt")
        }
        viewModel = X2cViewModel(fakeScanner, failing, settingsRepository, fakeBridge, fakeChecker, fakeNotifier)
        val effects = collectEffects()
        scanAndStart()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isConverting)
        assertEquals("kaputt", state.errorMessage)
        assertEquals(1, fakeNotifier.finishedCount)
        assertTrue(effects.contains(X2cEffect.ShowMessage(UiText.of(R.string.x2c_msg_failed, "kaputt"))))
    }
}
