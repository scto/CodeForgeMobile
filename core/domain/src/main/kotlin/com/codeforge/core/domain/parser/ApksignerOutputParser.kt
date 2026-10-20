// Modul: :core:domain
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.ApkVerificationResult
import com.codeforge.core.domain.model.KeystoreCertificateInfo

/**
 * Reiner Kotlin-Parser für die Ausgabe von `apksigner verify --verbose --print-certs`.
 */
object ApksignerOutputParser {

    private val V1_REGEX = Regex("""(?i)Verified\s+using\s+v1\s+scheme.*:\s*(true|false)""")
    private val V2_REGEX = Regex("""(?i)Verified\s+using\s+v2\s+scheme.*:\s*(true|false)""")
    private val V3_REGEX = Regex("""(?i)Verified\s+using\s+v3\s+scheme.*:\s*(true|false)""")
    private val V4_REGEX = Regex("""(?i)Verified\s+using\s+v4\s+scheme.*:\s*(true|false)""")
    private val SIGNER_COUNT_REGEX = Regex("""(?i)Number\s+of\s+signers:\s*(\d+)""")

    private val CERT_DN_REGEX = Regex("""(?i).*certificate\s+DN:\s*(.*)""")
    private val CERT_SHA256_REGEX = Regex("""(?i).*certificate\s+SHA-256\s+digest:\s*([0-9a-fA-F]+)""")
    private val CERT_SHA1_REGEX = Regex("""(?i).*certificate\s+SHA-1\s+digest:\s*([0-9a-fA-F]+)""")
    private val CERT_MD5_REGEX = Regex("""(?i).*certificate\s+MD5\s+digest:\s*([0-9a-fA-F]+)""")
    private val KEY_ALG_REGEX = Regex("""(?i).*key\s+algorithm:\s*(.*)""")
    private val KEY_SIZE_REGEX = Regex("""(?i).*key\s+size\s*\(bits\):\s*(\d+)""")

    fun parse(output: String): ApkVerificationResult {
        val lines = output.lines()
        val isVerified = lines.any { it.trim().equals("Verifies", ignoreCase = true) }

        var v1 = false
        var v2 = false
        var v3 = false
        var v4 = false
        var signerCount = 0

        val certificates = mutableListOf<KeystoreCertificateInfo>()
        val warnings = mutableListOf<String>()

        var currentDn = ""
        var currentSha256 = ""
        var currentSha1 = ""
        var currentMd5 = ""
        var currentKeyAlg = ""
        var currentKeySize = ""

        fun flushCert() {
            if (currentDn.isNotBlank() || currentSha256.isNotBlank()) {
                certificates.add(
                    KeystoreCertificateInfo(
                        owner = currentDn,
                        issuer = currentDn,
                        sha256Fingerprint = formatFingerprint(currentSha256),
                        sha1Fingerprint = formatFingerprint(currentSha1),
                        md5Fingerprint = formatFingerprint(currentMd5),
                        publicKeyAlgorithm = if (currentKeySize.isNotBlank()) "$currentKeySize-bit $currentKeyAlg".trim() else currentKeyAlg
                    )
                )
                currentDn = ""
                currentSha256 = ""
                currentSha1 = ""
                currentMd5 = ""
                currentKeyAlg = ""
                currentKeySize = ""
            }
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("WARNING:", ignoreCase = true)) {
                warnings.add(trimmed)
            }

            V1_REGEX.find(trimmed)?.let { v1 = it.groupValues[1].toBoolean() }
            V2_REGEX.find(trimmed)?.let { v2 = it.groupValues[1].toBoolean() }
            V3_REGEX.find(trimmed)?.let { v3 = it.groupValues[1].toBoolean() }
            V4_REGEX.find(trimmed)?.let { v4 = it.groupValues[1].toBoolean() }
            SIGNER_COUNT_REGEX.find(trimmed)?.let { signerCount = it.groupValues[1].toIntOrNull() ?: 0 }

            CERT_DN_REGEX.find(trimmed)?.let {
                flushCert()
                currentDn = it.groupValues[1].trim()
                return@let
            }

            CERT_SHA256_REGEX.find(trimmed)?.let {
                currentSha256 = it.groupValues[1].trim()
                return@let
            }

            CERT_SHA1_REGEX.find(trimmed)?.let {
                currentSha1 = it.groupValues[1].trim()
                return@let
            }

            CERT_MD5_REGEX.find(trimmed)?.let {
                currentMd5 = it.groupValues[1].trim()
                return@let
            }

            KEY_ALG_REGEX.find(trimmed)?.let {
                currentKeyAlg = it.groupValues[1].trim()
                return@let
            }

            KEY_SIZE_REGEX.find(trimmed)?.let {
                currentKeySize = it.groupValues[1].trim()
                return@let
            }
        }

        flushCert()

        return ApkVerificationResult(
            isVerified = isVerified,
            v1SchemeVerified = v1,
            v2SchemeVerified = v2,
            v3SchemeVerified = v3,
            v4SchemeVerified = v4,
            signerCount = if (signerCount > 0) signerCount else certificates.size,
            certificates = certificates,
            warnings = warnings,
            rawOutput = output
        )
    }

    private fun formatFingerprint(hex: String): String {
        val clean = hex.replace(":", "").uppercase()
        if (clean.isEmpty()) return ""
        return clean.chunked(2).joinToString(":")
    }
}
