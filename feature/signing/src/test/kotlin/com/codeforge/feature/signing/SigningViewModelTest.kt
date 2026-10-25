// Modul: :feature:signing
package com.codeforge.feature.signing

import com.codeforge.core.domain.model.ApkSignResult
import com.codeforge.core.domain.model.ApkVerificationResult
import com.codeforge.core.domain.model.CreateKeystoreRequest
import com.codeforge.core.domain.model.GradleSigningSnippet
import com.codeforge.core.domain.model.KeyAlgorithm
import com.codeforge.core.domain.model.KeystoreEntryInfo
import com.codeforge.core.domain.model.KeystoreInfo
import com.codeforge.core.domain.model.PasswordStrength
import com.codeforge.core.domain.model.ProjectSigningAssignment
import com.codeforge.core.domain.model.SignApkRequest
import com.codeforge.core.domain.repository.SigningRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class SigningViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeRepository = FakeSigningRepository()
    private lateinit var viewModel: SigningViewModel

    private class FakeSigningRepository : SigningRepository {
        val keystores = mutableListOf<KeystoreInfo>()
        private val _activeBuildSigning = MutableStateFlow<Map<String, String>>(emptyMap())
        override val activeBuildSigning: StateFlow<Map<String, String>> = _activeBuildSigning

        private val _assignments = MutableStateFlow<List<ProjectSigningAssignment>>(emptyList())
        override fun getProjectAssignments(): Flow<List<ProjectSigningAssignment>> = _assignments.asStateFlow()

        override suspend fun getAssignmentForProject(projectPath: String): ProjectSigningAssignment? =
            _assignments.value.firstOrNull { it.projectPath == projectPath }

        override suspend fun assignKeyToProject(projectPath: String, keystoreId: String, alias: String): Result<Unit> {
            val updated = _assignments.value.filterNot { it.projectPath == projectPath } +
                ProjectSigningAssignment(projectPath, keystoreId, alias)
            _assignments.value = updated
            return Result.success(Unit)
        }

        override suspend fun removeAssignmentForProject(projectPath: String): Result<Unit> {
            _assignments.value = _assignments.value.filterNot { it.projectPath == projectPath }
            return Result.success(Unit)
        }

        override suspend fun removeAssignmentsForKeystore(keystoreId: String): Result<Unit> {
            _assignments.value = _assignments.value.filterNot { it.keystoreId == keystoreId }
            return Result.success(Unit)
        }

        override suspend fun removeAssignmentsForAlias(keystoreId: String, alias: String): Result<Unit> {
            _assignments.value = _assignments.value.filterNot { it.keystoreId == keystoreId && it.alias == alias }
            return Result.success(Unit)
        }

        override suspend fun resolveBuildSigning(projectPath: String?): Result<Map<String, String>> {
            if (projectPath != null) {
                val a = getAssignmentForProject(projectPath)
                if (a != null) {
                    return Result.success(
                        mapOf(
                            GradleSigningSnippet.ENV_KEYSTORE_PATH to "/data/keystores/${a.keystoreId}",
                            GradleSigningSnippet.ENV_KEYSTORE_ALIAS to a.alias,
                            GradleSigningSnippet.ENV_KEYSTORE_PASSWORD to "pass",
                            GradleSigningSnippet.ENV_KEY_PASSWORD to "pass"
                        )
                    )
                }
            }
            return Result.success(_activeBuildSigning.value)
        }

        override suspend fun getKeystores(): List<KeystoreInfo> = keystores.toList()
        override suspend fun getKeystore(id: String): KeystoreInfo? = keystores.firstOrNull { it.id == id }

        override suspend fun createKeystore(request: CreateKeystoreRequest): Result<KeystoreInfo> {
            val ks = KeystoreInfo(
                id = request.fileName,
                name = request.fileName,
                filePath = "/data/keystores/${request.fileName}",
                fileSizeBytes = 2048,
                entries = listOf(KeystoreEntryInfo(alias = request.alias)),
                hasStoredPassword = true
            )
            keystores.add(ks)
            return Result.success(ks)
        }

        override suspend fun importKeystore(sourceFile: File, storePassword: String, customName: String?): Result<KeystoreInfo> {
            val name = customName ?: sourceFile.name
            val ks = KeystoreInfo(
                id = name,
                name = name,
                filePath = "/data/keystores/$name",
                fileSizeBytes = 2048,
                entries = listOf(KeystoreEntryInfo(alias = "imported")),
                hasStoredPassword = true
            )
            keystores.add(ks)
            return Result.success(ks)
        }

        override suspend fun deleteKeystore(id: String): Result<Unit> {
            keystores.removeAll { it.id == id }
            return Result.success(Unit)
        }

        override suspend fun exportKeystore(id: String, targetFile: File): Result<Unit> = Result.success(Unit)

        override suspend fun signApk(request: SignApkRequest): Result<ApkSignResult> {
            return Result.success(
                ApkSignResult(
                    success = true,
                    outputApkPath = request.resolveOutputPath(),
                    outputApkSizeBytes = 10240,
                    verificationResult = ApkVerificationResult(isVerified = true, v2SchemeVerified = true)
                )
            )
        }

        override suspend fun verifyApk(apkFile: File): Result<ApkVerificationResult> {
            return Result.success(ApkVerificationResult(isVerified = true, v2SchemeVerified = true))
        }

        override suspend fun configureBuildSigning(keystoreId: String, alias: String): Result<Map<String, String>> {
            val map = mapOf(
                GradleSigningSnippet.ENV_KEYSTORE_PATH to "/data/keystores/$keystoreId",
                GradleSigningSnippet.ENV_KEYSTORE_ALIAS to alias,
                GradleSigningSnippet.ENV_KEYSTORE_PASSWORD to "pass",
                GradleSigningSnippet.ENV_KEY_PASSWORD to "pass"
            )
            _activeBuildSigning.value = map
            return Result.success(map)
        }

        override suspend fun clearBuildSigning() {
            _activeBuildSigning.value = emptyMap()
        }

        override fun isJdkAvailable(): Boolean = true
        override fun isApksignerAvailable(): Boolean = true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = SigningViewModel(fakeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoadingAndTabSelection() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertEquals(SigningTab.KEYSTORES, viewModel.uiState.value.activeTab)
        assertTrue(viewModel.uiState.value.isJdkAvailable)
        assertTrue(viewModel.uiState.value.isApksignerAvailable)

        viewModel.onIntent(SigningUiIntent.SelectTab(SigningTab.SIGN_APK))
        assertEquals(SigningTab.SIGN_APK, viewModel.uiState.value.activeTab)
    }

    @Test
    fun passwordStrengthCalculationInCreateForm() = runTest(testDispatcher) {
        viewModel.onIntent(SigningUiIntent.OpenCreateDialog)
        assertTrue(viewModel.uiState.value.isCreateDialogVisible)

        viewModel.onIntent(SigningUiIntent.UpdateCreateStorePassword("123"))
        assertEquals(PasswordStrength.VERY_WEAK, viewModel.uiState.value.createForm.passwordStrength)
        assertFalse(viewModel.uiState.value.createForm.canSubmit)

        viewModel.onIntent(SigningUiIntent.UpdateCreateStorePassword("SuperSecure123!@#"))
        assertEquals(PasswordStrength.VERY_STRONG, viewModel.uiState.value.createForm.passwordStrength)
    }

    @Test
    fun createKeystoreSuccessClosesDialogAndUpdatesList() = runTest(testDispatcher) {
        viewModel.onIntent(SigningUiIntent.OpenCreateDialog)
        viewModel.onIntent(SigningUiIntent.UpdateCreateFileName("my-key.p12"))
        viewModel.onIntent(SigningUiIntent.UpdateCreateAlias("release_key"))
        viewModel.onIntent(SigningUiIntent.UpdateCreateCommonName("Developer"))
        viewModel.onIntent(SigningUiIntent.UpdateCreateStorePassword("SecretPass123!"))
        assertTrue(viewModel.uiState.value.createForm.canSubmit)

        viewModel.onIntent(SigningUiIntent.SubmitCreateKeystore)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isCreateDialogVisible)
        assertEquals(1, viewModel.uiState.value.keystores.size)
        assertEquals("my-key.p12", viewModel.uiState.value.keystores.first().name)
        assertNotNull(viewModel.uiState.value.userMessage)
    }

    @Test
    fun signApkSubmission() = runTest(testDispatcher) {
        // Keystore vorbereiten
        fakeRepository.keystores.add(
            KeystoreInfo(
                id = "release.p12",
                name = "release.p12",
                filePath = "/data/keystores/release.p12",
                fileSizeBytes = 2048,
                entries = listOf(KeystoreEntryInfo(alias = "my_alias"))
            )
        )
        viewModel.onIntent(SigningUiIntent.RefreshKeystores)
        advanceUntilIdle()

        viewModel.onIntent(SigningUiIntent.SelectTab(SigningTab.SIGN_APK))
        viewModel.onIntent(SigningUiIntent.UpdateSignInputPath("/sdcard/app-unsigned.apk"))
        viewModel.onIntent(SigningUiIntent.SelectSignKeystore("release.p12"))
        viewModel.onIntent(SigningUiIntent.SelectSignAlias("my_alias"))

        assertTrue(viewModel.uiState.value.signForm.canSubmit)

        viewModel.onIntent(SigningUiIntent.SubmitSignApk)
        advanceUntilIdle()

        val result = viewModel.uiState.value.signForm.signResult
        assertNotNull(result)
        assertTrue(result?.success == true)
        assertTrue(result?.outputApkPath?.endsWith("app-signed.apk") == true)
    }

    @Test
    fun gradleBuildSigningActivationAndDeactivation() = runTest(testDispatcher) {
        viewModel.onIntent(SigningUiIntent.ActivateBuildSigning("release.p12", "my_alias"))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.activeBuildSigning.isNotEmpty())
        assertEquals("/data/keystores/release.p12", viewModel.uiState.value.activeBuildSigning[GradleSigningSnippet.ENV_KEYSTORE_PATH])

        viewModel.onIntent(SigningUiIntent.DeactivateBuildSigning)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.activeBuildSigning.isEmpty())
    }
}
