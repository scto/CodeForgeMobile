package com.codeforge.feature.x2c

import androidx.datastore.core.DataStore
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.domain.model.ComposeScanResult
import com.codeforge.core.domain.model.LayoutClassification
import com.codeforge.core.domain.model.LayoutPlanEntry
import com.codeforge.core.domain.model.X2cFileResult
import com.codeforge.core.domain.model.X2cFileStatus
import com.codeforge.core.domain.model.X2cOptions
import com.codeforge.core.domain.model.X2cPlan
import com.codeforge.core.domain.model.X2cProgress
import com.codeforge.core.domain.model.X2cReport
import com.codeforge.core.domain.repository.ComposeMigrationScanner
import com.codeforge.core.domain.repository.X2cRepository
import com.codeforge.core.navigation.JumpTarget
import com.codeforge.core.navigation.OpenFileRequestBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private class FakeX2cRepository : X2cRepository {
        private val _progress = MutableStateFlow<X2cProgress>(X2cProgress.Idle)
        override val progress: StateFlow<X2cProgress> = _progress.asStateFlow()

        var reportToReturn = X2cReport(
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
            )
        )

        override suspend fun analyzeProject(projectPath: String): X2cPlan = error("Not used in fake")

        override suspend fun executeMigration(projectPath: String, options: X2cOptions): X2cReport = reportToReturn

        override suspend fun undoMigration(projectPath: String, journalPath: String): Boolean = true

        override fun cancelMigration() {}
    }

    private class FakeOpenFileRequestBridge : OpenFileRequestBridge {
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeScanner = FakeComposeMigrationScanner()
        fakeRepository = FakeX2cRepository()
        fakeDataStore = FakeDataStore()
        settingsRepository = SettingsRepository(fakeDataStore)
        fakeBridge = FakeOpenFileRequestBridge()
        viewModel = X2cViewModel(fakeScanner, fakeRepository, settingsRepository, fakeBridge)
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
}
