package com.codeforge.feature.projectsettings.projectinfo

import androidx.lifecycle.SavedStateHandle
import com.codeforge.core.domain.model.ProjectStats
import com.codeforge.core.domain.repository.ProjectStatsRepository
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectInfoTest {

    @Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun slicesGroupTheTailAsOther() {
        val s = toSlices(listOf("A" to 50L, "B" to 30L, "C" to 10L, "D" to 6L, "E" to 4L, "F" to 0L), max = 3, otherLabel = "Andere")
        assertEquals(listOf("A", "B", "Andere"), s.map { it.label })
        assertEquals(20L, s.last().value)
        assertEquals(1f, s.sumOf { it.fraction.toDouble() }.toFloat(), 0.0001f)
        assertTrue(toSlices(listOf("A" to 0L), 3, "x").isEmpty())
    }

    @Test fun compactNumbers() {
        assertEquals("9.999", compact(9_999, Locale.GERMANY))
        assertEquals("276k", compact(275_920, Locale.GERMANY))
        assertEquals("1,3M", compact(1_250_000, Locale.GERMANY))
        assertEquals("<1 %", percent(0.004f))
        assertEquals("33 %", percent(0.333f))
    }

    @Test fun viewModelLoadsStats() = runTest {
        val progress = mutableListOf<Int>()
        val stats = sample()
        val repo = object : ProjectStatsRepository {
            override suspend fun analyze(rootPath: String, onProgress: (Int) -> Unit): Result<ProjectStats> {
                assertEquals("/p/Demo", rootPath)
                onProgress(50).also { progress += 50 }
                return Result.success(stats)
            }
        }
        val vm = ProjectInfoViewModel(SavedStateHandle(mapOf(ProjectInfoViewModel.ARG_ROOT_PATH to "/p/Demo")), repo)
        assertTrue(vm.uiState.value is ProjectInfoState.Loading)
        advanceUntilIdle()
        assertEquals(ProjectInfoState.Loaded(stats), vm.uiState.value)
        assertEquals(listOf(50), progress)
    }

    private fun sample() = ProjectStats(
        name = "Demo", rootPath = "/p/Demo", primaryLanguage = "Kotlin", buildSystems = listOf("Gradle (Kotlin DSL)"),
        projectKinds = emptyList(), usesCompose = false, languages = emptyList(), modules = emptyList(), largestFiles = emptyList(),
        totalFiles = 1, sourceFiles = 1, totalLines = 1, codeLines = 1, commentLines = 0, blankLines = 0, sizeBytes = 1,
        createdMillis = null, createdSource = null, lastModifiedMillis = null, gradleWrapper = null, agpVersion = null,
        kotlinVersion = null, minSdk = null, targetSdk = null, compileSdk = null, dependencyCount = null, git = null,
        truncated = false, analyzedAtMillis = 0,
    )
}
