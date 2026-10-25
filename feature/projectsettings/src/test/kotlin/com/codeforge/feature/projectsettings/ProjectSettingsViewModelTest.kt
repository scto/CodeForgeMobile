package com.codeforge.feature.projectsettings

import com.codeforge.core.domain.model.BuildTypeSettings
import com.codeforge.core.domain.model.ProjectBuildSettings
import com.codeforge.core.domain.model.ProjectSigningAssignment
import com.codeforge.core.domain.repository.ProjectSettingsRepository
import com.codeforge.core.domain.repository.SigningRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeSettingsRepo: FakeProjectSettingsRepository
    private lateinit var fakeSigningRepo: FakeSigningRepository
    private lateinit var viewModel: ProjectSettingsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeSettingsRepo = FakeProjectSettingsRepository()
        fakeSigningRepo = FakeSigningRepository()
        viewModel = ProjectSettingsViewModel(fakeSettingsRepo, fakeSigningRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testLoadSettings() = runTest {
        viewModel.handleIntent(ProjectSettingsIntent.Load("/path/to/project"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("/path/to/project", state.projectPath)
        assertEquals("key1", state.signingAssignment?.keystoreId)
    }

    @Test
    fun testShrinkDisabledWhenMinifyDisabled() = runTest {
        viewModel.handleIntent(ProjectSettingsIntent.Load("/path/to/project"))
        advanceUntilIdle()

        // Enable Minify and Shrink
        viewModel.handleIntent(ProjectSettingsIntent.ToggleMinify(true))
        viewModel.handleIntent(ProjectSettingsIntent.ToggleShrink(true))
        assertTrue(viewModel.uiState.value.currentTabSettings.shrinkResources)

        // Disable Minify -> Shrink must be forced to false
        viewModel.handleIntent(ProjectSettingsIntent.ToggleMinify(false))
        assertFalse(viewModel.uiState.value.currentTabSettings.minifyEnabled)
        assertFalse(viewModel.uiState.value.currentTabSettings.shrinkResources)
    }

    @Test
    fun testSaveSettings() = runTest {
        viewModel.handleIntent(ProjectSettingsIntent.Load("/path/to/project"))
        advanceUntilIdle()

        viewModel.handleIntent(ProjectSettingsIntent.ToggleCrunch(true))
        viewModel.handleIntent(ProjectSettingsIntent.Save)
        advanceUntilIdle()

        assertTrue(fakeSettingsRepo.savedSettings?.release?.crunchPngs == true)
        assertTrue(viewModel.uiState.value.isSavedMessageVisible)
    }

    private class FakeProjectSettingsRepository : ProjectSettingsRepository {
        var savedSettings: ProjectBuildSettings? = null
        private val state = MutableStateFlow(
            ProjectBuildSettings(
                projectPath = "/path/to/project",
                release = BuildTypeSettings(minifyEnabled = true, shrinkResources = false)
            )
        )

        override fun getSettings(projectPath: String): Flow<ProjectBuildSettings> = state

        override suspend fun saveSettings(settings: ProjectBuildSettings) {
            savedSettings = settings
            state.value = settings
        }

        override suspend fun generateInitScript(settings: ProjectBuildSettings): String = ""
    }

    private class FakeSigningRepository : SigningRepository {
        override fun getProjectAssignments(): Flow<List<ProjectSigningAssignment>> {
            return flowOf(
                listOf(
                    ProjectSigningAssignment(
                        projectPath = "/path/to/project",
                        keystoreId = "key1",
                        alias = "alias1"
                    )
                )
            )
        }

        override suspend fun getKeystores(): List<com.codeforge.core.domain.model.KeystoreInfo> = emptyList()
        override suspend fun getKeystore(id: String): com.codeforge.core.domain.model.KeystoreInfo? = null
        override suspend fun createKeystore(request: com.codeforge.core.domain.model.CreateKeystoreRequest): Result<com.codeforge.core.domain.model.KeystoreInfo> = Result.failure(Exception())
        override suspend fun importKeystore(sourceFile: java.io.File, storePassword: String, customName: String?): Result<com.codeforge.core.domain.model.KeystoreInfo> = Result.failure(Exception())
        override suspend fun deleteKeystore(id: String): Result<Unit> = Result.success(Unit)
        override suspend fun exportKeystore(id: String, targetFile: java.io.File): Result<Unit> = Result.success(Unit)
        override suspend fun signApk(request: com.codeforge.core.domain.model.SignApkRequest): Result<com.codeforge.core.domain.model.ApkSignResult> = Result.failure(Exception())
        override suspend fun verifyApk(apkFile: java.io.File): Result<com.codeforge.core.domain.model.ApkVerificationResult> = Result.failure(Exception())
        override suspend fun configureBuildSigning(keystoreId: String, alias: String): Result<Map<String, String>> = Result.failure(Exception())
        override suspend fun clearBuildSigning() {}
        override val activeBuildSigning: kotlinx.coroutines.flow.StateFlow<Map<String, String>> = MutableStateFlow(emptyMap())
        override suspend fun getAssignmentForProject(projectPath: String): ProjectSigningAssignment? = null
        override suspend fun assignKeyToProject(projectPath: String, keystoreId: String, alias: String): Result<Unit> = Result.success(Unit)
        override suspend fun removeAssignmentForProject(projectPath: String): Result<Unit> = Result.success(Unit)
        override suspend fun removeAssignmentsForKeystore(keystoreId: String): Result<Unit> = Result.success(Unit)
        override suspend fun removeAssignmentsForAlias(keystoreId: String, alias: String): Result<Unit> = Result.success(Unit)
        override suspend fun resolveBuildSigning(projectPath: String?): Result<Map<String, String>> = Result.success(emptyMap())
        override fun isJdkAvailable(): Boolean = true
        override fun isApksignerAvailable(): Boolean = true
    }
}
