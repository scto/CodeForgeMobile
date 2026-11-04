package com.codeforge.buildlogic

import java.io.File
import java.util.Properties
import org.gradle.api.GradleException
import org.gradle.api.Project

/** Zugangsdaten für die Release-Signatur. Passwörter werden nie geloggt ([toString] maskiert sie). */
data class ReleaseSigning(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
) {
    override fun toString() = "ReleaseSigning(storeFile=$storeFile, keyAlias=$keyAlias, passwords=***)"
}

/** Namen der Werte in `keystore.properties` und der zugehörigen Umgebungsvariablen. */
object SigningKeys {
    const val PROPERTIES_FILE = "keystore.properties"
    const val STORE_FILE = "storeFile"
    const val STORE_PASSWORD = "storePassword"
    const val KEY_ALIAS = "keyAlias"
    const val KEY_PASSWORD = "keyPassword"

    const val ENV_STORE_FILE = "CODEFORGE_STORE_FILE"
    const val ENV_STORE_PASSWORD = "CODEFORGE_STORE_PASSWORD"
    const val ENV_KEY_ALIAS = "CODEFORGE_KEY_ALIAS"
    const val ENV_KEY_PASSWORD = "CODEFORGE_KEY_PASSWORD"

    /** `-Pcodeforge.signing.required=true` oder `CI=true`: Release-Build ohne Schlüssel schlägt fehl. */
    const val PROP_REQUIRED = "codeforge.signing.required"
}

/**
 * Liest die Release-Signatur. Quellen je Wert in dieser Reihenfolge: `keystore.properties` im Projektstamm,
 * dann Umgebungsvariable. Gibt `null` zurück, wenn gar nichts konfiguriert ist; ein unvollständiger oder
 * fehlerhafter Satz ist ein Fehler (statt still mit dem Debug-Schlüssel zu signieren).
 */
fun Project.loadReleaseSigning(): ReleaseSigning? {
    val props = Properties().apply {
        rootProject.file(SigningKeys.PROPERTIES_FILE).takeIf { it.isFile }?.inputStream()?.use { load(it) }
    }

    fun value(propKey: String, envKey: String): String? =
        (props.getProperty(propKey) ?: System.getenv(envKey))?.trim()?.takeIf { it.isNotEmpty() }

    val storePath = value(SigningKeys.STORE_FILE, SigningKeys.ENV_STORE_FILE)
    val storePassword = value(SigningKeys.STORE_PASSWORD, SigningKeys.ENV_STORE_PASSWORD)
    val keyAlias = value(SigningKeys.KEY_ALIAS, SigningKeys.ENV_KEY_ALIAS)
    val keyPassword = value(SigningKeys.KEY_PASSWORD, SigningKeys.ENV_KEY_PASSWORD)

    if (listOf(storePath, storePassword, keyAlias, keyPassword).all { it == null }) return null

    val missing = buildList {
        if (storePath == null) add("${SigningKeys.STORE_FILE} / ${SigningKeys.ENV_STORE_FILE}")
        if (storePassword == null) add("${SigningKeys.STORE_PASSWORD} / ${SigningKeys.ENV_STORE_PASSWORD}")
        if (keyAlias == null) add("${SigningKeys.KEY_ALIAS} / ${SigningKeys.ENV_KEY_ALIAS}")
        if (keyPassword == null) add("${SigningKeys.KEY_PASSWORD} / ${SigningKeys.ENV_KEY_PASSWORD}")
    }
    if (missing.isNotEmpty()) {
        throw GradleException(
            "Release-Signatur unvollständig, es fehlen: ${missing.joinToString()}. " +
                "Siehe docs/dev/Signing.md.",
        )
    }

    val storeFile = rootProject.file(storePath!!)
    if (!storeFile.isFile) {
        throw GradleException("Keystore nicht gefunden: $storeFile (storeFile=$storePath). Siehe docs/dev/Signing.md.")
    }
    return ReleaseSigning(storeFile, storePassword!!, keyAlias!!, keyPassword!!)
}
