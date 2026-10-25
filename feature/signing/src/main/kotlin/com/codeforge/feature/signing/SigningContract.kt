// Modul: :feature:signing
package com.codeforge.feature.signing

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ApkSignResult
import com.codeforge.core.domain.model.DistinguishedName
import com.codeforge.core.domain.model.GradleSigningSnippet
import com.codeforge.core.domain.model.KeyAlgorithm
import com.codeforge.core.domain.model.KeystoreInfo
import com.codeforge.core.domain.model.PasswordStrength
import com.codeforge.core.domain.model.ProjectSigningAssignment
import com.codeforge.core.domain.model.RecentProject
import java.io.File
import java.io.InputStream
import java.io.OutputStream

enum class SigningTab {
    KEYSTORES,
    SIGN_APK,
    GRADLE_CONFIG
}

@Immutable
data class CreateKeystoreFormState(
    val fileName: String = "release.keystore",
    val alias: String = "release",
    val validityYears: Int = 25,
    val commonName: String = "",
    val organization: String = "",
    val organizationalUnit: String = "",
    val country: String = "",
    val algorithm: KeyAlgorithm = KeyAlgorithm.RSA_4096,
    val storePassword: String = "",
    val keyPassword: String = "",
    val useSamePassword: Boolean = true,
    val passwordStrength: PasswordStrength = PasswordStrength.VERY_WEAK,
    val isCreating: Boolean = false,
    val errorMessage: String? = null
) {
    val canSubmit: Boolean
        get() = fileName.isNotBlank() &&
            alias.isNotBlank() &&
            commonName.isNotBlank() &&
            storePassword.length >= 6 &&
            (useSamePassword || keyPassword.length >= 6) &&
            !isCreating
}

@Immutable
data class ImportKeystoreFormState(
    val filePath: String = "",
    val storePassword: String = "",
    val customName: String = "",
    val isImporting: Boolean = false,
    val errorMessage: String? = null
) {
    val canSubmit: Boolean
        get() = filePath.isNotBlank() && storePassword.isNotBlank() && !isImporting
}

@Immutable
data class SignApkFormState(
    val inputApkPath: String = "",
    val outputApkPath: String = "",
    val selectedKeystoreId: String = "",
    val selectedAlias: String = "",
    val v1SigningEnabled: Boolean = true,
    val v2SigningEnabled: Boolean = true,
    val v3SigningEnabled: Boolean = true,
    val v4SigningEnabled: Boolean = true,
    val isSigning: Boolean = false,
    val signResult: ApkSignResult? = null,
    val errorMessage: String? = null
) {
    val canSubmit: Boolean
        get() = inputApkPath.isNotBlank() && selectedKeystoreId.isNotBlank() && selectedAlias.isNotBlank() && !isSigning
}

@Immutable
data class SigningUiState(
    val activeTab: SigningTab = SigningTab.KEYSTORES,
    val isLoading: Boolean = false,
    val keystores: List<KeystoreInfo> = emptyList(),
    val isJdkAvailable: Boolean = true,
    val isApksignerAvailable: Boolean = true,

    // Dialog-Status
    val isCreateDialogVisible: Boolean = false,
    val createForm: CreateKeystoreFormState = CreateKeystoreFormState(),

    val isImportDialogVisible: Boolean = false,
    val importForm: ImportKeystoreFormState = ImportKeystoreFormState(),

    val isDetailsDialogVisible: Boolean = false,
    val selectedKeystore: KeystoreInfo? = null,

    val isExportConfirmDialogVisible: Boolean = false,
    val keystoreToExport: KeystoreInfo? = null,
    val exportTargetPath: String = "",

    val isDeleteConfirmDialogVisible: Boolean = false,
    val keystoreToDelete: KeystoreInfo? = null,
    val deleteWarningProjects: List<String> = emptyList(),

    // 47.5: Projektzuweisung Dialog
    val isAssignProjectDialogVisible: Boolean = false,
    val assignProjectKeystore: KeystoreInfo? = null,
    val assignSelectedProjectPath: String = "",
    val assignSelectedAlias: String = "",

    // 47.5: Zuweisungen & Kürzliche Projekte
    val projectAssignments: List<ProjectSigningAssignment> = emptyList(),
    val recentProjects: List<RecentProject> = emptyList(),

    // APK Signieren
    val signForm: SignApkFormState = SignApkFormState(),

    // Gradle Config
    val activeBuildSigning: Map<String, String> = emptyMap(),
    val gradleSnippet: String = GradleSigningSnippet.generateSnippet(),

    val userMessage: String? = null
)

sealed interface SigningUiIntent {
    data class SelectTab(val tab: SigningTab) : SigningUiIntent
    data object RefreshKeystores : SigningUiIntent

    // Anlegen
    data object OpenCreateDialog : SigningUiIntent
    data object DismissCreateDialog : SigningUiIntent
    data class UpdateCreateFileName(val name: String) : SigningUiIntent
    data class UpdateCreateAlias(val alias: String) : SigningUiIntent
    data class UpdateCreateValidityYears(val years: Int) : SigningUiIntent
    data class UpdateCreateCommonName(val cn: String) : SigningUiIntent
    data class UpdateCreateOrganization(val org: String) : SigningUiIntent
    data class UpdateCreateOrganizationalUnit(val ou: String) : SigningUiIntent
    data class UpdateCreateCountry(val country: String) : SigningUiIntent
    data class UpdateCreateAlgorithm(val alg: KeyAlgorithm) : SigningUiIntent
    data class UpdateCreateStorePassword(val pass: String) : SigningUiIntent
    data class UpdateCreateKeyPassword(val pass: String) : SigningUiIntent
    data class SetCreateUseSamePassword(val same: Boolean) : SigningUiIntent
    data object SubmitCreateKeystore : SigningUiIntent

    // Importieren
    data object OpenImportDialog : SigningUiIntent
    data object DismissImportDialog : SigningUiIntent
    data class UpdateImportFilePath(val path: String) : SigningUiIntent
    data class UpdateImportPassword(val pass: String) : SigningUiIntent
    data class UpdateImportCustomName(val name: String) : SigningUiIntent
    data object SubmitImportKeystore : SigningUiIntent

    // Details
    data class OpenDetails(val keystore: KeystoreInfo) : SigningUiIntent
    data object DismissDetails : SigningUiIntent

    // 47.5: Zuweisung Projekt ↔ Schlüssel
    data class OpenAssignProjectDialog(val keystore: KeystoreInfo) : SigningUiIntent
    data object DismissAssignProjectDialog : SigningUiIntent
    data class SelectAssignProject(val projectPath: String) : SigningUiIntent
    data class SelectAssignAlias(val alias: String) : SigningUiIntent
    data object SubmitAssignProject : SigningUiIntent
    data class RemoveProjectAssignment(val projectPath: String) : SigningUiIntent
    data class RemoveAliasAssignment(val keystoreId: String, val alias: String) : SigningUiIntent

    // 47.5: SAF Import & Export
    data class ImportKeystoreFromStreamData(val inputStream: InputStream, val fileName: String, val storePass: String, val customName: String? = null) : SigningUiIntent
    data class ExportKeystoreToStreamData(val keystoreId: String, val outputStream: OutputStream) : SigningUiIntent

    // Exportieren
    data class OpenExportConfirm(val keystore: KeystoreInfo) : SigningUiIntent
    data class UpdateExportTargetPath(val path: String) : SigningUiIntent
    data object ConfirmExport : SigningUiIntent
    data object DismissExportConfirm : SigningUiIntent

    // Löschen
    data class OpenDeleteConfirm(val keystore: KeystoreInfo) : SigningUiIntent
    data object ConfirmDelete : SigningUiIntent
    data object DismissDeleteConfirm : SigningUiIntent

    // Signieren
    data class UpdateSignInputPath(val path: String) : SigningUiIntent
    data class UpdateSignOutputPath(val path: String) : SigningUiIntent
    data class SelectSignKeystore(val keystoreId: String) : SigningUiIntent
    data class SelectSignAlias(val alias: String) : SigningUiIntent
    data class SetV1Signing(val enabled: Boolean) : SigningUiIntent
    data class SetV2Signing(val enabled: Boolean) : SigningUiIntent
    data class SetV3Signing(val enabled: Boolean) : SigningUiIntent
    data class SetV4Signing(val enabled: Boolean) : SigningUiIntent
    data object SubmitSignApk : SigningUiIntent
    data object ClearSignResult : SigningUiIntent

    // Gradle Integration
    data class ActivateBuildSigning(val keystoreId: String, val alias: String) : SigningUiIntent
    data object DeactivateBuildSigning : SigningUiIntent

    data object DismissUserMessage : SigningUiIntent
}
