// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ApkSignResult
import com.codeforge.core.domain.model.ApkVerificationResult
import com.codeforge.core.domain.model.CreateKeystoreRequest
import com.codeforge.core.domain.model.KeystoreInfo
import com.codeforge.core.domain.model.SignApkRequest
import java.io.File
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
     * Prüft, ob ein JDK (`keytool`) im Termux-Prefix oder System verfügbar ist.
     */
    fun isJdkAvailable(): Boolean

    /**
     * Prüft, ob `apksigner` im Android-SDK oder System verfügbar ist.
     */
    fun isApksignerAvailable(): Boolean
}
