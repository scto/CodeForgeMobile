/**
 * Modul: :core:domain
 * @author Thomas Schmid
 *
 * Signaturen von Plugin-Archiven (Vertrauen beim ersten Mal, „TOFU“): Ein Plugin darf eine `signature.json`
 * mitbringen. Sie enthält den öffentlichen Schlüssel (EC P-256) und die Signatur über die Liste aller Dateien mit
 * ihren SHA-256-Werten. Die App merkt sich beim ersten Installieren den Fingerabdruck des Schlüssels; ein Update muss
 * mit demselben Schlüssel signiert sein. Unsignierte Plugins bleiben installierbar, ein signiertes Plugin lässt sich
 * aber nicht durch ein unsigniertes oder fremd signiertes ersetzen.
 *
 * Signieren: `python3 scripts/plugin-sign.py <ordner> <schlüssel.pem>` (siehe docs/dev/PluginSigning.md).
 */
package com.codeforge.core.domain.extension

import java.io.File
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object PluginSignatures {
    const val FILE_NAME = "signature.json"
    const val ALGORITHM = "SHA256withECDSA"

    /** Ergebnis der Prüfung eines entpackten Plugins. */
    sealed interface Result {
        /** Ohne `signature.json`. */
        data object Unsigned : Result

        /** Gültig signiert; [fingerprint] = SHA-256 des öffentlichen Schlüssels (hex). */
        data class Signed(val fingerprint: String) : Result
    }

    /**
     * Prüft die Signatur im Ordner [dir]. Wirft [SecurityException], wenn eine Signatur vorhanden, aber ungültig ist
     * (falsches Format, falscher Schlüssel, veränderte oder zusätzliche Dateien).
     */
    fun verify(dir: File): Result {
        val sigFile = File(dir, FILE_NAME).takeIf { it.isFile } ?: return Result.Unsigned
        val obj = runCatching { Json.parseToJsonElement(sigFile.readText()) as JsonObject }.getOrNull()
            ?: throw SecurityException("signature.json ist kein gültiges JSON")
        fun field(name: String) = (obj[name] as? JsonPrimitive)?.contentOrNull
            ?: throw SecurityException("signature.json: '$name' fehlt")
        if (field("algorithm") != ALGORITHM) throw SecurityException("Nicht unterstützter Signatur-Algorithmus")
        val keyBytes = decode(field("publicKey"))
        val sigBytes = decode(field("signature"))
        val key = runCatching { KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes)) }.getOrNull()
            ?: throw SecurityException("Ungültiger öffentlicher Schlüssel")
        val ok = runCatching {
            Signature.getInstance(ALGORITHM).run {
                initVerify(key)
                update(manifestOf(dir).toByteArray(Charsets.UTF_8))
                verify(sigBytes)
            }
        }.getOrDefault(false)
        if (!ok) throw SecurityException("Signatur ungültig – das Plugin wurde nach dem Signieren verändert")
        return Result.Signed(sha256Hex(keyBytes))
    }

    /**
     * Regel für Updates: Ist [previousFingerprint] gesetzt, muss [incoming] mit demselben Schlüssel signiert sein.
     * Liefert eine Fehlermeldung oder `null`.
     */
    fun updateViolation(previousFingerprint: String, incoming: Result): String? = when {
        previousFingerprint.isBlank() -> null
        incoming !is Result.Signed -> "Das installierte Plugin ist signiert, das Update nicht – Installation abgelehnt"
        incoming.fingerprint != previousFingerprint -> "Das Update ist mit einem anderen Schlüssel signiert – Installation abgelehnt"
        else -> null
    }

    /**
     * Signierter Inhalt: je Datei (außer `signature.json`) eine Zeile `<sha256-hex>  <pfad>\n`, nach Pfad sortiert,
     * Pfade mit `/`. Dieselbe Regel nutzt `scripts/plugin-sign.py`.
     */
    fun manifestOf(dir: File): String = dir.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(dir).invariantSeparatorsPath to it }
        .filter { (path, _) -> path != FILE_NAME }
        .sortedBy { it.first }
        .joinToString("") { (path, file) -> sha256Hex(file.readBytes()) + "  " + path + "\n" }

    /** Kurzform eines Fingerabdrucks für die Anzeige (`AB:CD:…`, 8 Bytes). */
    fun shortFingerprint(fingerprint: String): String = fingerprint.take(16).uppercase().chunked(2).joinToString(":")

    private fun decode(b64: String): ByteArray = runCatching { Base64.getDecoder().decode(b64.trim()) }.getOrNull()
        ?: throw SecurityException("signature.json: ungültiges Base64")

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
