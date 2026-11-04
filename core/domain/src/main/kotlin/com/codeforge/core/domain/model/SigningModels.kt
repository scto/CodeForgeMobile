// Modul: :core:domain
package com.codeforge.core.domain.model

import java.io.File

/**
 * Unterstützte Algorithmen für die Schlüsselpaar-Generierung.
 */
enum class KeyAlgorithm(
    val algorithmName: String,
    val keySize: Int,
    val groupName: String? = null
) {
    RSA_4096("RSA", 4096),
    RSA_2048("RSA", 2048),
    EC_256("EC", 256, "secp256r1"),
    EC_384("EC", 384, "secp384r1");

    /** Ob der Schlüssel ein Elliptische-Kurven-Schlüssel ist. */
    val isEc: Boolean get() = this == EC_256 || this == EC_384
}

/**
 * X.500 Distinguished Name Komponenten für selbstsignierte Zertifikate.
 */
data class DistinguishedName(
    val commonName: String,
    val organization: String = "",
    val organizationalUnit: String = "",
    val locality: String = "",
    val state: String = "",
    val country: String = ""
) {
    /** Formatiert den Namen als X.500-String (`CN=…, OU=…`), nur gesetzte Teile, mit Escaping. */
    fun toDnameString(): String = buildList {
        if (commonName.isNotBlank()) add("CN=${escape(commonName)}")
        if (organizationalUnit.isNotBlank()) add("OU=${escape(organizationalUnit)}")
        if (organization.isNotBlank()) add("O=${escape(organization)}")
        if (locality.isNotBlank()) add("L=${escape(locality)}")
        if (state.isNotBlank()) add("ST=${escape(state)}")
        if (country.isNotBlank()) add("C=${escape(country)}")
    }.joinToString(", ")

    private fun escape(value: String): String =
        value.replace(",", "\\,").replace("=", "\\=")
}

/**
 * Parameter zum Erstellen eines neuen Keystores via keytool.
 */
data class CreateKeystoreRequest(
    val fileName: String,
    val alias: String,
    val validityYears: Int = 25,
    val distinguishedName: DistinguishedName,
    val algorithm: KeyAlgorithm = KeyAlgorithm.RSA_4096,
    val storePassword: String,
    val keyPassword: String = storePassword
) {
    /** Gültigkeit in Tagen (Jahre mal 365). */
    val validityDays: Int get() = validityYears * 365
}

/**
 * Metadaten eines einzelnen Zertifikats.
 */
data class KeystoreCertificateInfo(
    val owner: String = "",
    val issuer: String = "",
    val serialNumber: String = "",
    val validFrom: String = "",
    val validUntil: String = "",
    val sha1Fingerprint: String = "",
    val sha256Fingerprint: String = "",
    val md5Fingerprint: String = "",
    val signatureAlgorithm: String = "",
    val publicKeyAlgorithm: String = "",
    val version: Int = 3
) {
    /** Ob es sich um ein Android-Debug-Zertifikat handelt (Besitzer/Aussteller enthalten „Android Debug“). */
    val isDebugCertificate: Boolean
        get() = owner.contains("CN=Android Debug", ignoreCase = true) ||
            issuer.contains("CN=Android Debug", ignoreCase = true) ||
            owner.contains("Android Debug", ignoreCase = true)
}

/**
 * Eintrag innerhalb eines Keystores (z. B. PrivateKeyEntry).
 */
data class KeystoreEntryInfo(
    val alias: String,
    val creationDate: String = "",
    val entryType: String = "PrivateKeyEntry",
    val certificateChainLength: Int = 1,
    val certificates: List<KeystoreCertificateInfo> = emptyList()
) {
    /** Erstes Zertifikat der Kette. */
    val primaryCertificate: KeystoreCertificateInfo?
        get() = certificates.firstOrNull()

    /** Ob der Eintrag der Debug-Schlüssel ist (Alias `androiddebugkey` oder Debug-Zertifikat). */
    val isDebugKey: Boolean
        get() = alias.equals("androiddebugkey", ignoreCase = true) ||
            primaryCertificate?.isDebugCertificate == true
}

/**
 * Ein verwalteter Keystore in `filesDir/keystores/`.
 */
data class KeystoreInfo(
    val id: String,
    val name: String,
    val filePath: String,
    val fileSizeBytes: Long,
    val type: String = "PKCS12",
    val entries: List<KeystoreEntryInfo> = emptyList(),
    val lastModifiedMillis: Long = 0L,
    val hasStoredPassword: Boolean = false
) {
    /** Ob der Keystore einen Debug-Schlüssel enthält oder „debug“ im Namen trägt. */
    val isDebugKeystore: Boolean
        get() = entries.any { it.isDebugKey } || name.contains("debug", ignoreCase = true)

    /** Alias des ersten Eintrags. */
    val primaryAlias: String?
        get() = entries.firstOrNull()?.alias
}

/**
 * Anfrage zum Signieren einer APK.
 */
data class SignApkRequest(
    val inputApkPath: String,
    val outputApkPath: String? = null,
    val keystoreId: String,
    val alias: String,
    val v1SigningEnabled: Boolean? = null,
    val v2SigningEnabled: Boolean = true,
    val v3SigningEnabled: Boolean = true,
    val v4SigningEnabled: Boolean = true,
    val minSdkVersion: Int? = null
) {
    /** Ausgabepfad der signierten APK: [outputApkPath], sonst neben der Eingabe ohne Endung `-unsigned`. */
    fun resolveOutputPath(): String {
        outputApkPath?.let { return it }
        val inputFile = File(inputApkPath)
        val parent = inputFile.parentFile ?: File(".")
        val baseName = inputFile.nameWithoutExtension
        val cleanName = if (baseName.endsWith("-unsigned")) {
            baseName.removeSuffix("-unsigned") + "-signed.apk"
        } else {
            "$baseName-signed.apk"
        }
        return File(parent, cleanName).absolutePath
    }
}

/**
 * Ergebnis der APK-Signierung.
 */
data class ApkSignResult(
    val success: Boolean,
    val outputApkPath: String? = null,
    val outputApkSizeBytes: Long? = null,
    val outputMessages: List<String> = emptyList(),
    val errorMessages: List<String> = emptyList(),
    val verificationResult: ApkVerificationResult? = null
)

/**
 * Verifikationsergebnis von `apksigner verify --verbose --print-certs`.
 */
data class ApkVerificationResult(
    val isVerified: Boolean,
    val v1SchemeVerified: Boolean = false,
    val v2SchemeVerified: Boolean = false,
    val v3SchemeVerified: Boolean = false,
    val v4SchemeVerified: Boolean = false,
    val signerCount: Int = 0,
    val certificates: List<KeystoreCertificateInfo> = emptyList(),
    val warnings: List<String> = emptyList(),
    val rawOutput: String = ""
) {
    /** Ob die APK mit einem Debug-Zertifikat signiert ist. */
    val isSignedWithDebugKey: Boolean
        get() = certificates.any { it.isDebugCertificate }
}

/**
 * Passwortstärke-Kategorien.
 */
enum class PasswordStrength {
    VERY_WEAK,
    WEAK,
    MEDIUM,
    STRONG,
    VERY_STRONG;

    /** Numerischer Wert von 1 (sehr schwach) bis 5 (sehr stark). */
    val score: Int
        get() = when (this) {
            VERY_WEAK -> 1
            WEAK -> 2
            MEDIUM -> 3
            STRONG -> 4
            VERY_STRONG -> 5
        }
}

/**
 * Bewertet Passwortstärke für Keystores ohne externe Bibliotheken.
 */
object PasswordStrengthEvaluator {
    /** Bewertet [password] nach Länge und Zeichenklassen. */
    fun evaluate(password: String): PasswordStrength {
        if (password.length < 6) return PasswordStrength.VERY_WEAK

        var score = 0
        if (password.length >= 8) score++
        if (password.length >= 12) score++
        if (password.any { it.isUpperCase() }) score++
        if (password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        return when {
            score <= 1 -> PasswordStrength.VERY_WEAK
            score == 2 -> PasswordStrength.WEAK
            score in 3..4 -> PasswordStrength.MEDIUM
            score == 5 -> PasswordStrength.STRONG
            else -> PasswordStrength.VERY_STRONG
        }
    }
}

/**
 * Zuweisung eines Schlüssels und Alias zu einem bestimmten Projektpfad.
 * Wird IDE-intern ohne Passwörter gespeichert.
 */
data class ProjectSigningAssignment(
    val projectPath: String,
    val keystoreId: String,
    val alias: String
)

/**
 * Hilfsfunktionen zum Generieren von Gradle-Code für Release-Signierung.
 */
object GradleSigningSnippet {
    /** Umgebungsvariable mit dem Keystore-Pfad. */
    const val ENV_KEYSTORE_PATH = "CODEFORGE_KEYSTORE_PATH"
    /** Umgebungsvariable mit dem Schlüssel-Alias. */
    const val ENV_KEYSTORE_ALIAS = "CODEFORGE_KEYSTORE_ALIAS"
    /** Umgebungsvariable mit dem Keystore-Passwort. */
    const val ENV_KEYSTORE_PASSWORD = "CODEFORGE_KEYSTORE_PASSWORD"
    /** Umgebungsvariable mit dem Schlüsselpasswort. */
    const val ENV_KEY_PASSWORD = "CODEFORGE_KEY_PASSWORD"

    /** Erzeugt einen Gradle-Codeausschnitt für die Release-Signierung über Umgebungsvariablen. */
    fun generateSnippet(): String = """
        // In app/build.gradle.kts:
        android {
            signingConfigs {
                create("release") {
                    storeFile = System.getenv("$ENV_KEYSTORE_PATH")?.let { file(it) }
                    storePassword = System.getenv("$ENV_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("$ENV_KEYSTORE_ALIAS")
                    keyPassword = System.getenv("$ENV_KEY_PASSWORD")
                }
            }

            buildTypes {
                getByName("release") {
                    signingConfig = signingConfigs.getByName("release")
                }
            }
        }
    """.trimIndent()
}
