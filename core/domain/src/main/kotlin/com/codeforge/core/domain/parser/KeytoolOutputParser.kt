// Modul: :core:domain
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.KeystoreCertificateInfo
import com.codeforge.core.domain.model.KeystoreEntryInfo

/**
 * Reiner Kotlin-Parser für die Ausgabe von `keytool -list -v`.
 * Robust gegenüber englischen und deutschen Lokalisierungen.
 */
object KeytoolOutputParser {

    private val KEYSTORE_TYPE_REGEX = Regex("""(?i)(?:Keystore\s+type|Keystore-Typ)\s*:\s*(.*)""")
    private val ALIAS_REGEX = Regex("""(?i)(?:Alias\s+name|Aliasname)\s*:\s*(.*)""")
    private val CREATION_DATE_REGEX = Regex("""(?i)(?:Creation\s+date|Erstellungsdatum)\s*:\s*(.*)""")
    private val ENTRY_TYPE_REGEX = Regex("""(?i)(?:Entry\s+type|Eintragstyp)\s*:\s*(.*)""")
    private val CHAIN_LENGTH_REGEX = Regex("""(?i)(?:Certificate\s+chain\s+length|Zertifikatskettenlänge)\s*:\s*(\d+)""")

    private val OWNER_REGEX = Regex("""(?i)(?:Owner|Eigentümer|Inhaber)\s*:\s*(.*)""")
    private val ISSUER_REGEX = Regex("""(?i)(?:Issuer|Aussteller)\s*:\s*(.*)""")
    private val SERIAL_REGEX = Regex("""(?i)(?:Serial\s+number|Seriennummer)\s*:\s*(.*)""")
    private val VALIDITY_REGEX = Regex("""(?i)(?:Valid\s+from|Gültig\s+von)\s*:\s*(.*?)\s*(?:until|bis)\s*:\s*(.*)""")
    private val SHA1_REGEX = Regex("""(?i)SHA(?:-)?1\s*:\s*([0-9A-Fa-f:]+)""")
    private val SHA256_REGEX = Regex("""(?i)SHA(?:-)?256\s*:\s*([0-9A-Fa-f:]+)""")
    private val MD5_REGEX = Regex("""(?i)MD5\s*:\s*([0-9A-Fa-f:]+)""")
    private val SIG_ALG_REGEX = Regex("""(?i)(?:Signature\s+algorithm\s+name|Signaturalgorithmusname)\s*:\s*(.*)""")
    private val KEY_ALG_REGEX = Regex("""(?i)(?:Subject\s+Public\s+Key\s+Algorithm|Algorithmus\s+des\s+öffentlichen\s+(?:Subjekt)?schlüssels)\s*:\s*(.*)""")
    private val VERSION_REGEX = Regex("""(?i)Version\s*:\s*(\d+)""")

    data class ParsedKeystore(
        val keystoreType: String,
        val entries: List<KeystoreEntryInfo>
    )

    fun parse(output: String): ParsedKeystore {
        var keystoreType = "PKCS12"
        val entries = mutableListOf<KeystoreEntryInfo>()

        var currentAlias: String? = null
        var currentCreationDate = ""
        var currentEntryType = "PrivateKeyEntry"
        var currentChainLength = 1
        val currentCertificates = mutableListOf<KeystoreCertificateInfo>()

        var certOwner = ""
        var certIssuer = ""
        var certSerial = ""
        var certValidFrom = ""
        var certValidUntil = ""
        var certSha1 = ""
        var certSha256 = ""
        var certMd5 = ""
        var certSigAlg = ""
        var certKeyAlg = ""
        var certVersion = 3
        var inCertificate = false

        fun flushCertificate() {
            if (inCertificate) {
                currentCertificates.add(
                    KeystoreCertificateInfo(
                        owner = certOwner,
                        issuer = certIssuer,
                        serialNumber = certSerial,
                        validFrom = certValidFrom,
                        validUntil = certValidUntil,
                        sha1Fingerprint = certSha1,
                        sha256Fingerprint = certSha256,
                        md5Fingerprint = certMd5,
                        signatureAlgorithm = certSigAlg,
                        publicKeyAlgorithm = certKeyAlg,
                        version = certVersion
                    )
                )
                certOwner = ""
                certIssuer = ""
                certSerial = ""
                certValidFrom = ""
                certValidUntil = ""
                certSha1 = ""
                certSha256 = ""
                certMd5 = ""
                certSigAlg = ""
                certKeyAlg = ""
                certVersion = 3
                inCertificate = false
            }
        }

        fun flushEntry() {
            flushCertificate()
            val alias = currentAlias
            if (alias != null) {
                entries.add(
                    KeystoreEntryInfo(
                        alias = alias,
                        creationDate = currentCreationDate,
                        entryType = currentEntryType,
                        certificateChainLength = currentChainLength,
                        certificates = currentCertificates.toList()
                    )
                )
                currentAlias = null
                currentCreationDate = ""
                currentEntryType = "PrivateKeyEntry"
                currentChainLength = 1
                currentCertificates.clear()
            }
        }

        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            KEYSTORE_TYPE_REGEX.matchEntire(trimmed)?.let {
                keystoreType = it.groupValues[1].trim()
                return@let
            }

            ALIAS_REGEX.matchEntire(trimmed)?.let {
                flushEntry()
                currentAlias = it.groupValues[1].trim()
                return@let
            }

            CREATION_DATE_REGEX.matchEntire(trimmed)?.let {
                currentCreationDate = it.groupValues[1].trim()
                return@let
            }

            ENTRY_TYPE_REGEX.matchEntire(trimmed)?.let {
                currentEntryType = it.groupValues[1].trim()
                return@let
            }

            CHAIN_LENGTH_REGEX.matchEntire(trimmed)?.let {
                currentChainLength = it.groupValues[1].toIntOrNull() ?: 1
                return@let
            }

            if (trimmed.startsWith("Certificate[", ignoreCase = true) ||
                trimmed.startsWith("Zertifikat[", ignoreCase = true)
            ) {
                flushCertificate()
                inCertificate = true
                continue
            }

            OWNER_REGEX.matchEntire(trimmed)?.let {
                inCertificate = true
                certOwner = it.groupValues[1].trim()
                return@let
            }

            ISSUER_REGEX.matchEntire(trimmed)?.let {
                certIssuer = it.groupValues[1].trim()
                return@let
            }

            SERIAL_REGEX.matchEntire(trimmed)?.let {
                certSerial = it.groupValues[1].trim()
                return@let
            }

            VALIDITY_REGEX.matchEntire(trimmed)?.let {
                certValidFrom = it.groupValues[1].trim()
                certValidUntil = it.groupValues[2].trim()
                return@let
            }

            SHA1_REGEX.find(trimmed)?.let {
                certSha1 = it.groupValues[1].trim().uppercase()
                return@let
            }

            SHA256_REGEX.find(trimmed)?.let {
                certSha256 = it.groupValues[1].trim().uppercase()
                return@let
            }

            MD5_REGEX.find(trimmed)?.let {
                certMd5 = it.groupValues[1].trim().uppercase()
                return@let
            }

            SIG_ALG_REGEX.matchEntire(trimmed)?.let {
                certSigAlg = it.groupValues[1].trim()
                return@let
            }

            KEY_ALG_REGEX.matchEntire(trimmed)?.let {
                certKeyAlg = it.groupValues[1].trim()
                return@let
            }

            VERSION_REGEX.matchEntire(trimmed)?.let {
                certVersion = it.groupValues[1].toIntOrNull() ?: 3
                return@let
            }
        }

        flushEntry()

        return ParsedKeystore(
            keystoreType = keystoreType,
            entries = entries
        )
    }
}
