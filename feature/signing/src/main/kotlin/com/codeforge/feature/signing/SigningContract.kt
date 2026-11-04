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

/** Reiter des Signing-Bildschirms. */
enum class SigningTab {
    KEYSTORES,
    SIGN_APK,
    GRADLE_CONFIG
}

/** Formular „Keystore erstellen“. */
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
    /** Ob das Formular abgeschickt werden kann. */
    val canSubmit: Boolean
        get() = fileName.isNotBlank() &&
            alias.isNotBlank() &&
            commonName.isNotBlank() &&
            storePassword.length >= 6 &&
            (useSamePassword || keyPassword.length >= 6) &&
            !isCreating
}

/** Formular „Keystore importieren“. */
@Immutable
data class ImportKeystoreFormState(
    val filePath: String = "",
    val storePassword: String = "",
    val customName: String = "",
    val isImporting: Boolean = false,
    val errorMessage: String? = null
) {
    /** Ob das Formular abgeschickt werden kann. */
    val canSubmit: Boolean
        get() = filePath.isNotBlank() && storePassword.isNotBlank() && !isImporting
}

/** Formular „APK signieren“. */
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
    /** Ob das Formular abgeschickt werden kann. */
    val canSubmit: Boolean
        get() = inputApkPath.isNotBlank() && selectedKeystoreId.isNotBlank() && selectedAlias.isNotBlank() && !isSigning
}

/** Zustand des Signing-Bildschirms (Keystores, Formulare, Dialoge, Ergebnis). */
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

/** Aktionen im Signing-Bildschirm. */
sealed interface SigningUiIntent {
    /** Reiter wählen. */
    data class SelectTab(val tab: SigningTab) : SigningUiIntent
    /** Keystore-Liste neu laden. */
    data object RefreshKeystores : SigningUiIntent

    // Anlegen
    /** Erstellen-Dialog öffnen. */
    data object OpenCreateDialog : SigningUiIntent
    /** Erstellen-Dialog schließen. */
    data object DismissCreateDialog : SigningUiIntent
    /** Dateiname geändert. */
    data class UpdateCreateFileName(val name: String) : SigningUiIntent
    /** Alias geändert. */
    data class UpdateCreateAlias(val alias: String) : SigningUiIntent
    /** Gültigkeit (Jahre) geändert. */
    data class UpdateCreateValidityYears(val years: Int) : SigningUiIntent
    /** Common Name geändert. */
    data class UpdateCreateCommonName(val cn: String) : SigningUiIntent
    /** Organisation geändert. */
    data class UpdateCreateOrganization(val org: String) : SigningUiIntent
    /** Organisationseinheit geändert. */
    data class UpdateCreateOrganizationalUnit(val ou: String) : SigningUiIntent
    /** Land geändert. */
    data class UpdateCreateCountry(val country: String) : SigningUiIntent
    /** Schlüsselalgorithmus geändert. */
    data class UpdateCreateAlgorithm(val alg: KeyAlgorithm) : SigningUiIntent
    /** Keystore-Passwort geändert. */
    data class UpdateCreateStorePassword(val pass: String) : SigningUiIntent
    /** Schlüssel-Passwort geändert. */
    data class UpdateCreateKeyPassword(val pass: String) : SigningUiIntent
    /** Gleiches Passwort für Keystore und Schlüssel. */
    data class SetCreateUseSamePassword(val same: Boolean) : SigningUiIntent
    /** Keystore erstellen. */
    data object SubmitCreateKeystore : SigningUiIntent

    // Importieren
    /** Import-Dialog öffnen. */
    data object OpenImportDialog : SigningUiIntent
    /** Import-Dialog schließen. */
    data object DismissImportDialog : SigningUiIntent
    /** Importpfad geändert. */
    data class UpdateImportFilePath(val path: String) : SigningUiIntent
    /** Import-Passwort geändert. */
    data class UpdateImportPassword(val pass: String) : SigningUiIntent
    /** Eigener Name geändert. */
    data class UpdateImportCustomName(val name: String) : SigningUiIntent
    /** Keystore importieren. */
    data object SubmitImportKeystore : SigningUiIntent

    // Details
    /** Details eines Keystores öffnen. */
    data class OpenDetails(val keystore: KeystoreInfo) : SigningUiIntent
    /** Details schließen. */
    data object DismissDetails : SigningUiIntent

    // 47.5: Zuweisung Projekt ↔ Schlüssel
    /** Dialog „Projekt zuordnen“ öffnen. */
    data class OpenAssignProjectDialog(val keystore: KeystoreInfo) : SigningUiIntent
    /** Zuordnen-Dialog schließen. */
    data object DismissAssignProjectDialog : SigningUiIntent
    /** Projekt für die Zuordnung wählen. */
    data class SelectAssignProject(val projectPath: String) : SigningUiIntent
    /** Alias für die Zuordnung wählen. */
    data class SelectAssignAlias(val alias: String) : SigningUiIntent
    /** Zuordnung speichern. */
    data object SubmitAssignProject : SigningUiIntent
    /** Zuordnung eines Projekts entfernen. */
    data class RemoveProjectAssignment(val projectPath: String) : SigningUiIntent
    /** Zuordnung eines Alias entfernen. */
    data class RemoveAliasAssignment(val keystoreId: String, val alias: String) : SigningUiIntent

    // 47.5: SAF Import & Export
    /** Keystore aus einem Datenstrom importieren. */
    data class ImportKeystoreFromStreamData(val inputStream: InputStream, val fileName: String, val storePass: String, val customName: String? = null) : SigningUiIntent
    /** Keystore in einen Datenstrom exportieren. */
    data class ExportKeystoreToStreamData(val keystoreId: String, val outputStream: OutputStream) : SigningUiIntent

    // Exportieren
    /** Export-Bestätigung öffnen. */
    data class OpenExportConfirm(val keystore: KeystoreInfo) : SigningUiIntent
    /** Zielpfad des Exports geändert. */
    data class UpdateExportTargetPath(val path: String) : SigningUiIntent
    /** Export bestätigen. */
    data object ConfirmExport : SigningUiIntent
    /** Export-Bestätigung schließen. */
    data object DismissExportConfirm : SigningUiIntent

    // Löschen
    /** Löschbestätigung öffnen. */
    data class OpenDeleteConfirm(val keystore: KeystoreInfo) : SigningUiIntent
    /** Löschen bestätigen. */
    data object ConfirmDelete : SigningUiIntent
    /** Löschbestätigung schließen. */
    data object DismissDeleteConfirm : SigningUiIntent

    // Signieren
    /** Eingabe-APK geändert. */
    data class UpdateSignInputPath(val path: String) : SigningUiIntent
    /** Ausgabe-APK geändert. */
    data class UpdateSignOutputPath(val path: String) : SigningUiIntent
    /** Keystore zum Signieren wählen. */
    data class SelectSignKeystore(val keystoreId: String) : SigningUiIntent
    /** Alias zum Signieren wählen. */
    data class SelectSignAlias(val alias: String) : SigningUiIntent
    /** Signaturschema v1 ein-/ausschalten. */
    data class SetV1Signing(val enabled: Boolean) : SigningUiIntent
    /** Signaturschema v2 ein-/ausschalten. */
    data class SetV2Signing(val enabled: Boolean) : SigningUiIntent
    /** Signaturschema v3 ein-/ausschalten. */
    data class SetV3Signing(val enabled: Boolean) : SigningUiIntent
    /** Signaturschema v4 ein-/ausschalten. */
    data class SetV4Signing(val enabled: Boolean) : SigningUiIntent
    /** APK signieren. */
    data object SubmitSignApk : SigningUiIntent
    /** Ergebnis ausblenden. */
    data object ClearSignResult : SigningUiIntent

    // Gradle Integration
    /** Keystore und Alias für Builds aktivieren. */
    data class ActivateBuildSigning(val keystoreId: String, val alias: String) : SigningUiIntent
    /** Build-Signierung deaktivieren. */
    data object DeactivateBuildSigning : SigningUiIntent

    /** Hinweis ausblenden. */
    data object DismissUserMessage : SigningUiIntent
}
