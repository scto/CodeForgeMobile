// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ApkSignResult
import com.codeforge.core.domain.model.ApkVerificationResult
import com.codeforge.core.domain.model.CreateKeystoreRequest
import com.codeforge.core.domain.model.KeystoreInfo
import com.codeforge.core.domain.model.ProjectSigningAssignment
import com.codeforge.core.domain.model.SignApkRequest
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Vertrag zur Keystore- und APK-Signatur-Verwaltung.
 */
interface SigningRepository {
    /**
     * Liefert alle im privaten App-Speicher (`filesDir/keystores/`) abgelegten Keystores.
     */
    suspend fun getKeystores(): List<KeystoreInfo>

    /**
     * Ruft Details zu einem bestimmten Keystore ab.
     */
    suspend fun getKeystore(id: String): KeystoreInfo?

    /**
     * Erstellt ein neues Schlüsselpaar via `keytool -genkeypair` im PKCS12-Format.
     * Passwörter werden sicher via SecretStorage abgelegt und nur über Umgebungsvariablen
     * an den Kindprozess weitergegeben (nie in der Befehlszeilen-Argumentliste).
     */
    suspend fun createKeystore(request: CreateKeystoreRequest): Result<KeystoreInfo>

    /**
     * Importiert einen vorhandenen Keystore (.jks / .keystore / .p12) in den sicheren App-Speicher.
     * Prüft vorab das Passwort via `keytool -list`.
     */
    suspend fun importKeystore(
        sourceFile: File,
        storePassword: String,
        customName: String? = null
    ): Result<KeystoreInfo>

    /**
     * Löscht einen Keystore aus dem privaten Speicher und entfernt hinterlegte Passwörter.
     */
    suspend fun deleteKeystore(id: String): Result<Unit>

    /**
     * Exportiert einen Keystore in ein vom Nutzer gewähltes Verzeichnis (z. B. Download/SAF).
     */
    suspend fun exportKeystore(id: String, targetFile: File): Result<Unit>

    /**
     * Signiert eine APK mit `apksigner sign` und verifiziert sie anschließend
     * mit `apksigner verify --verbose --print-certs`.
     * Passwörter werden sicher über Umgebungsvariablen (`--ks-pass env:...`) übergeben.
     */
    suspend fun signApk(request: SignApkRequest): Result<ApkSignResult>

    /**
     * Prüft die Signatur einer bestehenden APK mit `apksigner verify --verbose --print-certs`.
     */
    suspend fun verifyApk(apkFile: File): Result<ApkVerificationResult>

    /**
     * Konfiguriert die Umgebungsvariablen `CODEFORGE_KEYSTORE_*` für den nachfolgenden
     * Gradle-Build (Task 28).
     */
    suspend fun configureBuildSigning(keystoreId: String, alias: String): Result<Map<String, String>>

    /**
     * Entfernt die gesetzten Signatur-Umgebungsvariablen aus der Gradle-Build-Konfiguration.
     */
    suspend fun clearBuildSigning()

    /**
     * Aktuell aktive Signatur-Umgebungsvariablen für Gradle-Builds (oder leer).
     */
    val activeBuildSigning: StateFlow<Map<String, String>>

    /**
     * Liefert alle Zuweisungen von Projekten zu Schlüsseln als reaktiven Stream.
     */
    fun getProjectAssignments(): Flow<List<ProjectSigningAssignment>>

    /**
     * Ruft die Zuweisung für einen bestimmten Projektpfad ab (oder null).
     */
    suspend fun getAssignmentForProject(projectPath: String): ProjectSigningAssignment?

    /**
     * Weist einem Projekt einen Keystore und Alias zu.
     */
    suspend fun assignKeyToProject(projectPath: String, keystoreId: String, alias: String): Result<Unit>

    /**
     * Entfernt die Zuweisung für einen Projektpfad.
     */
    suspend fun removeAssignmentForProject(projectPath: String): Result<Unit>

    /**
     * Entfernt alle Zuweisungen, die einen bestimmten Keystore verwenden.
     */
    suspend fun removeAssignmentsForKeystore(keystoreId: String): Result<Unit>

    /**
     * Entfernt alle Zuweisungen, die einen bestimmten Alias eines Keystores verwenden.
     */
    suspend fun removeAssignmentsForAlias(keystoreId: String, alias: String): Result<Unit>

    /**
     * Löst die Signatur-Umgebungsvariablen für einen Build auf:
     * 1. Projektspezifische Zuweisung (ProjectSigningAssignment)
     * 2. Bisheriger globaler Standardschlüssel (activeBuildSigning)
     * 3. Kein Signing (leere Map)
     */
    suspend fun resolveBuildSigning(projectPath: String?): Result<Map<String, String>>

    /**
     * Importiert einen Keystore aus einem Datenstrom (z. B. SAF Document Provider).
     */
    suspend fun importKeystoreFromStream(
        inputStream: InputStream,
        originalFileName: String,
        storePassword: String,
        customName: String? = null
    ): Result<KeystoreInfo> = Result.failure(UnsupportedOperationException())

    /**
     * Exportiert einen Keystore in einen Ziel-Datenstrom (z. B. SAF Document Provider).
     */
    suspend fun exportKeystoreToStream(
        id: String,
        outputStream: OutputStream
    ): Result<Unit> = Result.failure(UnsupportedOperationException())

    /**
     * Prüft, ob ein JDK (`keytool`) im Termux-Prefix oder System verfügbar ist.
     */
    fun isJdkAvailable(): Boolean

    /**
     * Prüft, ob `apksigner` im Android-SDK oder System verfügbar ist.
     */
    fun isApksignerAvailable(): Boolean
}
