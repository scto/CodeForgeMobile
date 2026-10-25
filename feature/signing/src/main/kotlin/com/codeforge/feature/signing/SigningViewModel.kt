// Modul: :feature:signing
package com.codeforge.feature.signing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.CreateKeystoreRequest
import com.codeforge.core.domain.model.DistinguishedName
import com.codeforge.core.domain.model.PasswordStrengthEvaluator
import com.codeforge.core.domain.model.RecentProject
import com.codeforge.core.domain.model.SignApkRequest
import com.codeforge.core.domain.repository.RecentProjectsRepository
import com.codeforge.core.domain.repository.SigningRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SigningViewModel @Inject constructor(
    private val signingRepository: SigningRepository,
    private val recentProjectsRepository: RecentProjectsRepository
) : ViewModel() {

    constructor(signingRepository: SigningRepository) : this(
        signingRepository,
        object : RecentProjectsRepository {
            override val recentProjects = flowOf(emptyList<RecentProject>())
            override suspend fun addOrUpdate(project: RecentProject) {}
            override suspend fun remove(projectId: String) {}
        }
    )

    private val _uiState = MutableStateFlow(
        SigningUiState(
            isJdkAvailable = signingRepository.isJdkAvailable(),
            isApksignerAvailable = signingRepository.isApksignerAvailable()
        )
    )
    val uiState: StateFlow<SigningUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            signingRepository.activeBuildSigning.collect { env ->
                _uiState.update { it.copy(activeBuildSigning = env) }
            }
        }
        viewModelScope.launch {
            signingRepository.getProjectAssignments().collect { assignments ->
                _uiState.update { it.copy(projectAssignments = assignments) }
            }
        }
        viewModelScope.launch {
            recentProjectsRepository.recentProjects.collect { projects ->
                _uiState.update { it.copy(recentProjects = projects) }
            }
        }
        refreshKeystores()
    }

    fun onIntent(intent: SigningUiIntent) {
        when (intent) {
            is SigningUiIntent.SelectTab -> _uiState.update { it.copy(activeTab = intent.tab) }
            SigningUiIntent.RefreshKeystores -> refreshKeystores()

            // Anlegen
            SigningUiIntent.OpenCreateDialog -> _uiState.update {
                it.copy(
                    isCreateDialogVisible = true,
                    createForm = CreateKeystoreFormState()
                )
            }
            SigningUiIntent.DismissCreateDialog -> _uiState.update { it.copy(isCreateDialogVisible = false) }
            is SigningUiIntent.UpdateCreateFileName -> _uiState.update {
                it.copy(createForm = it.createForm.copy(fileName = intent.name, errorMessage = null))
            }
            is SigningUiIntent.UpdateCreateAlias -> _uiState.update {
                it.copy(createForm = it.createForm.copy(alias = intent.alias, errorMessage = null))
            }
            is SigningUiIntent.UpdateCreateValidityYears -> _uiState.update {
                it.copy(createForm = it.createForm.copy(validityYears = intent.years))
            }
            is SigningUiIntent.UpdateCreateCommonName -> _uiState.update {
                it.copy(createForm = it.createForm.copy(commonName = intent.cn, errorMessage = null))
            }
            is SigningUiIntent.UpdateCreateOrganization -> _uiState.update {
                it.copy(createForm = it.createForm.copy(organization = intent.org))
            }
            is SigningUiIntent.UpdateCreateOrganizationalUnit -> _uiState.update {
                it.copy(createForm = it.createForm.copy(organizationalUnit = intent.ou))
            }
            is SigningUiIntent.UpdateCreateCountry -> _uiState.update {
                it.copy(createForm = it.createForm.copy(country = intent.country))
            }
            is SigningUiIntent.UpdateCreateAlgorithm -> _uiState.update {
                it.copy(createForm = it.createForm.copy(algorithm = intent.alg))
            }
            is SigningUiIntent.UpdateCreateStorePassword -> _uiState.update {
                val strength = PasswordStrengthEvaluator.evaluate(intent.pass)
                val keyPass = if (it.createForm.useSamePassword) intent.pass else it.createForm.keyPassword
                it.copy(
                    createForm = it.createForm.copy(
                        storePassword = intent.pass,
                        keyPassword = keyPass,
                        passwordStrength = strength,
                        errorMessage = null
                    )
                )
            }
            is SigningUiIntent.UpdateCreateKeyPassword -> _uiState.update {
                it.copy(createForm = it.createForm.copy(keyPassword = intent.pass, errorMessage = null))
            }
            is SigningUiIntent.SetCreateUseSamePassword -> _uiState.update {
                val keyPass = if (intent.same) it.createForm.storePassword else it.createForm.keyPassword
                it.copy(createForm = it.createForm.copy(useSamePassword = intent.same, keyPassword = keyPass))
            }
            SigningUiIntent.SubmitCreateKeystore -> submitCreateKeystore()

            // Importieren
            SigningUiIntent.OpenImportDialog -> _uiState.update {
                it.copy(
                    isImportDialogVisible = true,
                    importForm = ImportKeystoreFormState()
                )
            }
            SigningUiIntent.DismissImportDialog -> _uiState.update { it.copy(isImportDialogVisible = false) }
            is SigningUiIntent.UpdateImportFilePath -> _uiState.update {
                it.copy(importForm = it.importForm.copy(filePath = intent.path, errorMessage = null))
            }
            is SigningUiIntent.UpdateImportPassword -> _uiState.update {
                it.copy(importForm = it.importForm.copy(storePassword = intent.pass, errorMessage = null))
            }
            is SigningUiIntent.UpdateImportCustomName -> _uiState.update {
                it.copy(importForm = it.importForm.copy(customName = intent.name))
            }
            SigningUiIntent.SubmitImportKeystore -> submitImportKeystore()

            // Details
            is SigningUiIntent.OpenDetails -> _uiState.update {
                it.copy(isDetailsDialogVisible = true, selectedKeystore = intent.keystore)
            }
            SigningUiIntent.DismissDetails -> _uiState.update {
                it.copy(isDetailsDialogVisible = false, selectedKeystore = null)
            }

            // Exportieren
            is SigningUiIntent.OpenExportConfirm -> _uiState.update {
                it.copy(
                    isExportConfirmDialogVisible = true,
                    keystoreToExport = intent.keystore,
                    exportTargetPath = "/sdcard/Download/${intent.keystore.name}"
                )
            }
            is SigningUiIntent.UpdateExportTargetPath -> _uiState.update {
                it.copy(exportTargetPath = intent.path)
            }
            SigningUiIntent.ConfirmExport -> confirmExport()
            SigningUiIntent.DismissExportConfirm -> _uiState.update {
                it.copy(isExportConfirmDialogVisible = false, keystoreToExport = null)
            }

            // Löschen
            is SigningUiIntent.OpenDeleteConfirm -> {
                val warningProjects = _uiState.value.projectAssignments
                    .filter { it.keystoreId == intent.keystore.id }
                    .map { it.projectPath }
                _uiState.update {
                    it.copy(
                        isDeleteConfirmDialogVisible = true,
                        keystoreToDelete = intent.keystore,
                        deleteWarningProjects = warningProjects
                    )
                }
            }
            SigningUiIntent.ConfirmDelete -> confirmDelete()
            SigningUiIntent.DismissDeleteConfirm -> _uiState.update {
                it.copy(isDeleteConfirmDialogVisible = false, keystoreToDelete = null, deleteWarningProjects = emptyList())
            }

            // 47.5: Zuweisung Projekt ↔ Schlüssel
            is SigningUiIntent.OpenAssignProjectDialog -> {
                val firstProject = _uiState.value.recentProjects.firstOrNull()?.path.orEmpty()
                val firstAlias = intent.keystore.primaryAlias.orEmpty()
                _uiState.update {
                    it.copy(
                        isAssignProjectDialogVisible = true,
                        assignProjectKeystore = intent.keystore,
                        assignSelectedProjectPath = firstProject,
                        assignSelectedAlias = firstAlias
                    )
                }
            }
            SigningUiIntent.DismissAssignProjectDialog -> _uiState.update {
                it.copy(isAssignProjectDialogVisible = false, assignProjectKeystore = null)
            }
            is SigningUiIntent.SelectAssignProject -> _uiState.update {
                it.copy(assignSelectedProjectPath = intent.projectPath)
            }
            is SigningUiIntent.SelectAssignAlias -> _uiState.update {
                it.copy(assignSelectedAlias = intent.alias)
            }
            SigningUiIntent.SubmitAssignProject -> submitAssignProject()
            is SigningUiIntent.RemoveProjectAssignment -> {
                viewModelScope.launch {
                    signingRepository.removeAssignmentForProject(intent.projectPath)
                }
            }
            is SigningUiIntent.RemoveAliasAssignment -> {
                viewModelScope.launch {
                    signingRepository.removeAssignmentsForAlias(intent.keystoreId, intent.alias)
                }
            }

            // 47.5: SAF Import / Export
            is SigningUiIntent.ImportKeystoreFromStreamData -> {
                importFromStream(intent.inputStream, intent.fileName, intent.storePass, intent.customName)
            }
            is SigningUiIntent.ExportKeystoreToStreamData -> {
                exportToStream(intent.keystoreId, intent.outputStream)
            }

            // Signieren
            is SigningUiIntent.UpdateSignInputPath -> _uiState.update {
                it.copy(signForm = it.signForm.copy(inputApkPath = intent.path, errorMessage = null))
            }
            is SigningUiIntent.UpdateSignOutputPath -> _uiState.update {
                it.copy(signForm = it.signForm.copy(outputApkPath = intent.path))
            }
            is SigningUiIntent.SelectSignKeystore -> _uiState.update {
                val ks = it.keystores.firstOrNull { k -> k.id == intent.keystoreId }
                val firstAlias = ks?.primaryAlias.orEmpty()
                it.copy(
                    signForm = it.signForm.copy(
                        selectedKeystoreId = intent.keystoreId,
                        selectedAlias = firstAlias,
                        errorMessage = null
                    )
                )
            }
            is SigningUiIntent.SelectSignAlias -> _uiState.update {
                it.copy(signForm = it.signForm.copy(selectedAlias = intent.alias, errorMessage = null))
            }
            is SigningUiIntent.SetV1Signing -> _uiState.update {
                it.copy(signForm = it.signForm.copy(v1SigningEnabled = intent.enabled))
            }
            is SigningUiIntent.SetV2Signing -> _uiState.update {
                it.copy(signForm = it.signForm.copy(v2SigningEnabled = intent.enabled))
            }
            is SigningUiIntent.SetV3Signing -> _uiState.update {
                it.copy(signForm = it.signForm.copy(v3SigningEnabled = intent.enabled))
            }
            is SigningUiIntent.SetV4Signing -> _uiState.update {
                it.copy(signForm = it.signForm.copy(v4SigningEnabled = intent.enabled))
            }
            SigningUiIntent.SubmitSignApk -> submitSignApk()
            SigningUiIntent.ClearSignResult -> _uiState.update {
                it.copy(signForm = it.signForm.copy(signResult = null, errorMessage = null))
            }

            // Gradle
            is SigningUiIntent.ActivateBuildSigning -> activateBuildSigning(intent.keystoreId, intent.alias)
            SigningUiIntent.DeactivateBuildSigning -> deactivateBuildSigning()

            SigningUiIntent.DismissUserMessage -> _uiState.update { it.copy(userMessage = null) }
        }
    }

    private fun refreshKeystores() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val list = signingRepository.getKeystores()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    keystores = list,
                    isJdkAvailable = signingRepository.isJdkAvailable(),
                    isApksignerAvailable = signingRepository.isApksignerAvailable(),
                    signForm = if (it.signForm.selectedKeystoreId.isBlank() && list.isNotEmpty()) {
                        it.signForm.copy(
                            selectedKeystoreId = list.first().id,
                            selectedAlias = list.first().primaryAlias.orEmpty()
                        )
                    } else it.signForm
                )
            }
        }
    }

    private fun submitCreateKeystore() {
        val form = _uiState.value.createForm
        if (!form.canSubmit) return

        viewModelScope.launch {
            _uiState.update { it.copy(createForm = form.copy(isCreating = true, errorMessage = null)) }

            val req = CreateKeystoreRequest(
                fileName = form.fileName,
                alias = form.alias,
                validityYears = form.validityYears,
                distinguishedName = DistinguishedName(
                    commonName = form.commonName,
                    organization = form.organization,
                    organizationalUnit = form.organizationalUnit,
                    country = form.country
                ),
                algorithm = form.algorithm,
                storePassword = form.storePassword,
                keyPassword = form.keyPassword
            )

            val result = signingRepository.createKeystore(req)
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isCreateDialogVisible = false,
                        createForm = CreateKeystoreFormState(),
                        userMessage = Res.string(R.string.signing_keystore_erfolgreich_erstellt)
                    )
                }
                refreshKeystores()
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(createForm = state.createForm.copy(isCreating = false, errorMessage = err.message))
                }
            }
        }
    }

    private fun submitImportKeystore() {
        val form = _uiState.value.importForm
        if (!form.canSubmit) return

        viewModelScope.launch {
            _uiState.update { it.copy(importForm = form.copy(isImporting = true, errorMessage = null)) }

            val file = File(form.filePath)
            val result = signingRepository.importKeystore(
                sourceFile = file,
                storePassword = form.storePassword,
                customName = form.customName.takeIf { it.isNotBlank() }
            )

            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isImportDialogVisible = false,
                        importForm = ImportKeystoreFormState(),
                        userMessage = Res.string(R.string.signing_keystore_erfolgreich_importiert)
                    )
                }
                refreshKeystores()
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(importForm = state.importForm.copy(isImporting = false, errorMessage = err.message))
                }
            }
        }
    }

    private fun confirmExport() {
        val ks = _uiState.value.keystoreToExport ?: return
        val targetPath = _uiState.value.exportTargetPath.takeIf { it.isNotBlank() } ?: return

        viewModelScope.launch {
            val targetFile = File(targetPath)
            val result = signingRepository.exportKeystore(ks.id, targetFile)
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isExportConfirmDialogVisible = false,
                        keystoreToExport = null,
                        userMessage = Res.string(R.string.signing_keystore_erfolgreich_exportiert, targetPath)
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(userMessage = err.message)
                }
            }
        }
    }

    private fun confirmDelete() {
        val ks = _uiState.value.keystoreToDelete ?: return

        viewModelScope.launch {
            val result = signingRepository.deleteKeystore(ks.id)
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isDeleteConfirmDialogVisible = false,
                        keystoreToDelete = null,
                        userMessage = Res.string(R.string.signing_keystore_geloescht)
                    )
                }
                refreshKeystores()
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(userMessage = err.message)
                }
            }
        }
    }

    private fun submitSignApk() {
        val form = _uiState.value.signForm
        if (!form.canSubmit) return

        viewModelScope.launch {
            _uiState.update { it.copy(signForm = form.copy(isSigning = true, errorMessage = null, signResult = null)) }

            val req = SignApkRequest(
                inputApkPath = form.inputApkPath,
                outputApkPath = form.outputApkPath.takeIf { it.isNotBlank() },
                keystoreId = form.selectedKeystoreId,
                alias = form.selectedAlias,
                v1SigningEnabled = form.v1SigningEnabled,
                v2SigningEnabled = form.v2SigningEnabled,
                v3SigningEnabled = form.v3SigningEnabled,
                v4SigningEnabled = form.v4SigningEnabled
            )

            val result = signingRepository.signApk(req)
            result.onSuccess { signResult ->
                _uiState.update { state ->
                    state.copy(
                        signForm = state.signForm.copy(
                            isSigning = false,
                            signResult = signResult,
                            errorMessage = if (!signResult.success) {
                                signResult.errorMessages.joinToString("\n")
                            } else null
                        )
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(
                        signForm = state.signForm.copy(
                            isSigning = false,
                            errorMessage = err.message
                        )
                    )
                }
            }
        }
    }

    private fun activateBuildSigning(keystoreId: String, alias: String) {
        viewModelScope.launch {
            val result = signingRepository.configureBuildSigning(keystoreId, alias)
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(userMessage = Res.string(R.string.signing_build_konfiguriert))
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(userMessage = err.message)
                }
            }
        }
    }

    private fun deactivateBuildSigning() {
        viewModelScope.launch {
            signingRepository.clearBuildSigning()
            _uiState.update { state ->
                state.copy(userMessage = Res.string(R.string.signing_build_entfernt))
            }
        }
    }

    private fun submitAssignProject() {
        val ks = _uiState.value.assignProjectKeystore ?: return
        val path = _uiState.value.assignSelectedProjectPath.trim()
        val alias = _uiState.value.assignSelectedAlias.trim()
        if (path.isBlank() || alias.isBlank()) return

        viewModelScope.launch {
            val res = signingRepository.assignKeyToProject(path, ks.id, alias)
            res.onSuccess {
                _uiState.update {
                    it.copy(
                        isAssignProjectDialogVisible = false,
                        assignProjectKeystore = null,
                        userMessage = Res.string(R.string.signing_build_konfiguriert)
                    )
                }
            }.onFailure { err ->
                _uiState.update { it.copy(userMessage = err.message) }
            }
        }
    }

    private fun importFromStream(inputStream: java.io.InputStream, fileName: String, storePass: String, customName: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(importForm = it.importForm.copy(isImporting = true, errorMessage = null)) }
            val result = signingRepository.importKeystoreFromStream(
                inputStream = inputStream,
                originalFileName = fileName,
                storePassword = storePass,
                customName = customName?.takeIf { it.isNotBlank() }
            )
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isImportDialogVisible = false,
                        importForm = ImportKeystoreFormState(),
                        userMessage = Res.string(R.string.signing_keystore_erfolgreich_importiert)
                    )
                }
                refreshKeystores()
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(importForm = state.importForm.copy(isImporting = false, errorMessage = err.message))
                }
            }
        }
    }

    private fun exportToStream(keystoreId: String, outputStream: java.io.OutputStream) {
        viewModelScope.launch {
            val result = signingRepository.exportKeystoreToStream(keystoreId, outputStream)
            result.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isExportConfirmDialogVisible = false,
                        keystoreToExport = null,
                        userMessage = Res.string(R.string.signing_keystore_erfolgreich_exportiert, "SAF")
                    )
                }
            }.onFailure { err ->
                _uiState.update { state ->
                    state.copy(userMessage = err.message)
                }
            }
        }
    }
}
