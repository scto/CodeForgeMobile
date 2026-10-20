// Modul: :feature:build
package com.codeforge.feature.build.analyzer

import com.codeforge.core.domain.model.ApkAnalysisException
import com.codeforge.core.domain.model.ApkAnalysisResult
import com.codeforge.core.domain.model.ApkComparisonResult
import com.codeforge.core.domain.model.ApkEntryDiff
import com.codeforge.core.domain.model.ApkEntryDiffType
import com.codeforge.core.domain.model.ApkEntryInfo
import com.codeforge.core.domain.model.ApkSection
import com.codeforge.core.domain.repository.ApkAnalyzerRepository
import com.codeforge.core.testing.TestRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ApkAnalyzerViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeRepository: FakeApkAnalyzerRepository
    private lateinit var viewModel: ApkAnalyzerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        TestRes.install()
        fakeRepository = FakeApkAnalyzerRepository()
        viewModel = ApkAnalyzerViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        TestRes.uninstall()
        Dispatchers.resetMain()
    }

    @Test
    fun initialStateIsDefault() {
        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.errorMessage)
        assertNull(state.analysisResult)
        assertEquals(ApkAnalyzerTab.OVERVIEW, state.activeTab)
    }

    @Test
    fun loadApkSuccessUpdatesState() = testScope.runTest {
        val testFile = tempFolder.newFile("sample.apk")
        val sampleResult = createSampleResult(testFile)
        fakeRepository.analyzeResult = Result.success(sampleResult)

        viewModel.onEvent(ApkAnalyzerUiEvent.LoadApk(testFile.absolutePath))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(sampleResult, state.analysisResult)
        assertEquals(testFile.absolutePath, state.baseApkPath)
    }

    @Test
    fun loadApkFailureSetsErrorMessage() = testScope.runTest {
        val testFile = tempFolder.newFile("slip.apk")
        fakeRepository.analyzeResult = Result.failure(ApkAnalysisException.ZipSlipDetected("../evil.txt"))

        viewModel.onEvent(ApkAnalyzerUiEvent.LoadApk(testFile.absolutePath))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(false, state.isLoading)
        assertNotNull(state.errorMessage)
        assertTrue(state.errorMessage!!.contains("Zip-Slip") || state.errorMessage!!.isNotBlank())
    }

    @Test
    fun selectTabUpdatesActiveTab() {
        viewModel.onEvent(ApkAnalyzerUiEvent.SelectTab(ApkAnalyzerTab.PERMISSIONS))
        assertEquals(ApkAnalyzerTab.PERMISSIONS, viewModel.uiState.value.activeTab)

        viewModel.onEvent(ApkAnalyzerUiEvent.SelectTab(ApkAnalyzerTab.COMPONENTS))
        assertEquals(ApkAnalyzerTab.COMPONENTS, viewModel.uiState.value.activeTab)
    }

    @Test
    fun filterAndSortFiles() = testScope.runTest {
        val testFile = tempFolder.newFile("filter.apk")
        val entries = listOf(
            ApkEntryInfo("classes.dex", 500, 1000, ApkSection.DEX),
            ApkEntryInfo("res/layout/main.xml", 200, 400, ApkSection.RES),
            ApkEntryInfo("lib/arm64-v8a/liba.so", 800, 1600, ApkSection.LIB)
        )
        val sampleResult = createSampleResult(testFile, entries)
        fakeRepository.analyzeResult = Result.success(sampleResult)

        viewModel.onEvent(ApkAnalyzerUiEvent.LoadApk(testFile.absolutePath))
        testScheduler.advanceUntilIdle()

        // Suche nach "layout"
        viewModel.onEvent(ApkAnalyzerUiEvent.UpdateSearchQuery("layout"))
        assertEquals(1, viewModel.uiState.value.filteredFiles.size)
        assertEquals("res/layout/main.xml", viewModel.uiState.value.filteredFiles.first().path)

        // Reset Suche
        viewModel.onEvent(ApkAnalyzerUiEvent.UpdateSearchQuery(""))

        // Kategorie-Filter: LIB
        viewModel.onEvent(ApkAnalyzerUiEvent.SetCategoryFilter(FileCategoryFilter.LIB))
        assertEquals(1, viewModel.uiState.value.filteredFiles.size)
        assertEquals("lib/arm64-v8a/liba.so", viewModel.uiState.value.filteredFiles.first().path)
    }

    @Test
    fun compareWithApkSuccessUpdatesComparisonResult() = testScope.runTest {
        val baseFile = tempFolder.newFile("base.apk")
        val compFile = tempFolder.newFile("comp.apk")

        val sampleResult = createSampleResult(baseFile)
        fakeRepository.analyzeResult = Result.success(sampleResult)

        val sampleComparison = ApkComparisonResult(
            baseApkPath = baseFile.absolutePath,
            comparedApkPath = compFile.absolutePath,
            baseFileName = baseFile.name,
            comparedFileName = compFile.name,
            baseFileSizeBytes = 1000L,
            comparedFileSizeBytes = 1200L,
            totalSizeDeltaBytes = 200L,
            totalSizeDeltaPercent = 20.0,
            totalUncompressedDeltaBytes = 400L,
            sectionDiffs = emptyList(),
            entryDiffs = listOf(
                ApkEntryDiff("new.txt", ApkSection.OTHER, ApkEntryDiffType.ADDED, 0, 100, 100, 0, 200, 200)
            ),
            addedCount = 1,
            removedCount = 0,
            changedCount = 0,
            unchangedCount = 0
        )
        fakeRepository.compareResult = Result.success(sampleComparison)

        viewModel.onEvent(ApkAnalyzerUiEvent.LoadApk(baseFile.absolutePath))
        testScheduler.advanceUntilIdle()

        viewModel.onEvent(ApkAnalyzerUiEvent.CompareWithApk(compFile.absolutePath))
        testScheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(sampleComparison, state.comparisonResult)
        assertEquals(ApkAnalyzerTab.COMPARE, state.activeTab)
    }

    @Test
    fun shareMarkdownReportEmitsEffect() = testScope.runTest {
        val testFile = tempFolder.newFile("share.apk")
        val sampleResult = createSampleResult(testFile)
        fakeRepository.analyzeResult = Result.success(sampleResult)

        viewModel.onEvent(ApkAnalyzerUiEvent.LoadApk(testFile.absolutePath))
        testScheduler.advanceUntilIdle()

        var emittedEffect: ApkAnalyzerUiEffect? = null
        val job = launch {
            emittedEffect = viewModel.effect.first()
        }

        viewModel.onEvent(ApkAnalyzerUiEvent.ShareMarkdownReport)
        testScheduler.advanceUntilIdle()

        assertNotNull(emittedEffect)
        assertTrue(emittedEffect is ApkAnalyzerUiEffect.ShareReport)
        val share = emittedEffect as ApkAnalyzerUiEffect.ShareReport
        assertTrue(share.markdownText.contains("# APK-Analysebericht"))
        job.cancel()
    }

    private fun createSampleResult(
        file: File,
        entries: List<ApkEntryInfo> = listOf(
            ApkEntryInfo("classes.dex", 500, 1000, ApkSection.DEX)
        )
    ): ApkAnalysisResult {
        return ApkAnalysisResult(
            apkFile = file,
            fileName = file.name,
            fileSizeBytes = 1024L,
            totalCompressedSize = entries.sumOf { it.compressedSize },
            totalUncompressedSize = entries.sumOf { it.uncompressedSize },
            entries = entries,
            sections = emptyList(),
            topEntries = entries,
            abiSummaries = emptyList()
        )
    }

    private class FakeApkAnalyzerRepository : ApkAnalyzerRepository {
        var analyzeResult: Result<ApkAnalysisResult> = Result.failure(IllegalStateException("Not set"))
        var compareResult: Result<ApkComparisonResult> = Result.failure(IllegalStateException("Not set"))

        override suspend fun analyzeApk(apkFile: File): Result<ApkAnalysisResult> = analyzeResult

        override suspend fun compareApks(baseFile: File, comparedFile: File): Result<ApkComparisonResult> = compareResult
    }
}
