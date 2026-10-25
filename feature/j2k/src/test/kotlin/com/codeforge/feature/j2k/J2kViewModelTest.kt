package com.codeforge.feature.j2k

import com.codeforge.core.domain.model.J2kFileResult
import com.codeforge.core.domain.model.J2kFileStatus
import com.codeforge.core.domain.model.J2kOptions
import com.codeforge.core.domain.model.J2kPlan
import com.codeforge.core.domain.model.J2kProgress
import com.codeforge.core.domain.model.J2kReport
import com.codeforge.core.domain.model.J2kScanResult
import com.codeforge.core.domain.repository.J2kRepository
import com.codeforge.core.navigation.JumpTarget
import com.codeforge.core.navigation.OpenFileRequestBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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
class J2kViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeRepository: FakeJ2kRepository
    private lateinit var fakeBridge: FakeOpenFileRequestBridge
    private lateinit var viewModel: J2kViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakeJ2kRepository()
        fakeBridge = FakeOpenFileRequestBridge()
        viewModel = J2kViewModel(fakeRepository, fakeBridge)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testScanProjectQualifiesForPrompt() = runTest {
        viewModel.handleIntent(J2kIntent.ScanProject("/project/java"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.scanResult)
        assertTrue(state.showDialog)
        assertEquals(80f, state.scanResult!!.javaPercentage, 0.01f)
    }

    @Test
    fun testToggleOptions() = runTest {
        viewModel.handleIntent(J2kIntent.SetConvertGradle(true))
        viewModel.handleIntent(J2kIntent.SetUseVersionCatalog(true))
        viewModel.handleIntent(J2kIntent.SetRunBuildCheck(false))

        val state = viewModel.uiState.value
        assertTrue(state.convertGradle)
        assertTrue(state.useVersionCatalog)
        assertFalse(state.runBuildCheck)
    }

    @Test
    fun testExecuteMigrationAndFilters() = runTest {
        viewModel.handleIntent(J2kIntent.ScanProject("/project/java"))
        advanceUntilIdle()

        viewModel.handleIntent(J2kIntent.StartMigration)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isConverting)
        assertTrue(state.showResultScreen)
        assertNotNull(state.report)
        assertEquals(2, state.filteredResults.size)

        viewModel.handleIntent(J2kIntent.SetFilter(J2kFilter.WITH_NOTES))
        assertEquals(1, viewModel.uiState.value.filteredResults.size)
        assertEquals(J2kFileStatus.CONVERTED_WITH_NOTES, viewModel.uiState.value.filteredResults[0].status)
    }

    @Test
    fun testOpenFileViaBridge() = runTest {
        viewModel.handleIntent(J2kIntent.OpenFile("app/src/main/java/Main.kt"))
        advanceUntilIdle()

        assertEquals("app/src/main/java/Main.kt", fakeBridge.lastOpenedPath)
    }

    private class FakeJ2kRepository : J2kRepository {
        private val _progress = MutableStateFlow<J2kProgress>(J2kProgress.Idle)
        override val progress: StateFlow<J2kProgress> = _progress

        override suspend fun scanProject(projectPath: String): J2kScanResult {
            return J2kScanResult(
                projectPath = projectPath,
                javaFilesCount = 8,
                javaLinesCount = 800,
                kotlinFilesCount = 2,
                kotlinLinesCount = 200,
                javaPercentage = 80.0f,
                hasGroovyGradle = true,
                hasKotlinDsl = false,
                hasVersionCatalog = false,
                qualifiesForAutoPrompt = true
            )
        }

        override suspend fun createPlan(projectPath: String): J2kPlan {
            return J2kPlan(
                projectPath = projectPath,
                totalJavaFiles = 0,
                totalJavaLines = 0,
                totalKotlinFiles = 0,
                totalKotlinLines = 0,
                javaPercentage = 0f,
                javaFiles = emptyList(),
                groovyFiles = emptyList(),
                ktsFiles = emptyList(),
                hasExistingVersionCatalog = false
            )
        }

        override suspend fun executeMigration(projectPath: String, options: J2kOptions): J2kReport {
            return J2kReport(
                projectPath = projectPath,
                timestampEpochMillis = 1700000000000L,
                convertedJavaCount = 2,
                notesJavaCount = 1,
                skippedJavaCount = 0,
                failedJavaCount = 0,
                convertedGradleCount = 0,
                notesGradleCount = 0,
                skippedGradleCount = 0,
                versionCatalogCreated = false,
                backupPath = "/backup.zip",
                journalPath = "/journal.json",
                fileResults = listOf(
                    J2kFileResult("FileA.kt", J2kFileStatus.CONVERTED, 100, 80),
                    J2kFileResult("FileB.kt", J2kFileStatus.CONVERTED_WITH_NOTES, 100, 90, notes = listOf("Note"))
                )
            )
        }

        override suspend fun undoMigration(projectPath: String, journalPath: String): Boolean = true
        override fun cancelMigration() {}
    }

    private class FakeOpenFileRequestBridge : OpenFileRequestBridge {
        var lastOpenedPath: String? = null
        private val _openRequests = MutableSharedFlow<String>(extraBufferCapacity = 1)
        override val openRequests: SharedFlow<String> = _openRequests
        private val _jumpRequests = MutableSharedFlow<JumpTarget>(extraBufferCapacity = 1)
        override val jumpRequests: SharedFlow<JumpTarget> = _jumpRequests

        override suspend fun requestOpen(path: String) {
            lastOpenedPath = path
            _openRequests.emit(path)
        }

        override suspend fun requestOpenAt(path: String, line: Int, column: Int, length: Int) {
            lastOpenedPath = path
            _openRequests.emit(path)
            _jumpRequests.emit(JumpTarget(path, line, column, length))
        }
    }
}
