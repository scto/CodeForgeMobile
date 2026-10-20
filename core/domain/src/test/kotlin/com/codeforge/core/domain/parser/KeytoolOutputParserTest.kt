// Modul: :core:domain
package com.codeforge.core.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeytoolOutputParserTest {

    @Test
    fun parseEnglishKeytoolOutput() {
        val output = """
            Keystore type: PKCS12
            Keystore provider: SUN

            Your keystore contains 1 entry

            Alias name: release_key
            Creation date: Oct 20, 2026
            Entry type: PrivateKeyEntry
            Certificate chain length: 1
            Certificate[1]:
            Owner: CN=Release Owner, OU=Mobile, O=CodeForge, C=DE
            Issuer: CN=Release Owner, OU=Mobile, O=CodeForge, C=DE
            Serial number: 7a8b9c0d1e2f3a4b
            Valid from: Tue Oct 20 12:00:00 GMT 2026 until: Sun Oct 20 12:00:00 GMT 2051
            Certificate fingerprints:
            	 SHA1: 11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44
            	 SHA256: AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99
            Signature algorithm name: SHA256withRSA
            Subject Public Key Algorithm: 4096-bit RSA key
            Version: 3
        """.trimIndent()

        val parsed = KeytoolOutputParser.parse(output)

        assertEquals("PKCS12", parsed.keystoreType)
        assertEquals(1, parsed.entries.size)

        val entry = parsed.entries.first()
        assertEquals("release_key", entry.alias)
        assertEquals("Oct 20, 2026", entry.creationDate)
        assertEquals("PrivateKeyEntry", entry.entryType)
        assertEquals(1, entry.certificateChainLength)
        assertFalse(entry.isDebugKey)

        val cert = entry.primaryCertificate
        assertNotNull(cert)
        assertEquals("CN=Release Owner, OU=Mobile, O=CodeForge, C=DE", cert?.owner)
        assertEquals("CN=Release Owner, OU=Mobile, O=CodeForge, C=DE", cert?.issuer)
        assertEquals("7a8b9c0d1e2f3a4b", cert?.serialNumber)
        assertEquals("Tue Oct 20 12:00:00 GMT 2026", cert?.validFrom)
        assertEquals("Sun Oct 20 12:00:00 GMT 2051", cert?.validUntil)
        assertEquals("11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44", cert?.sha1Fingerprint)
        assertEquals("AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99", cert?.sha256Fingerprint)
        assertEquals("SHA256withRSA", cert?.signatureAlgorithm)
        assertEquals("4096-bit RSA key", cert?.publicKeyAlgorithm)
        assertEquals(3, cert?.version)
        assertFalse(cert?.isDebugCertificate ?: true)
    }

    @Test
    fun parseGermanDebugKeytoolOutput() {
        val output = """
            Keystore-Typ: PKCS12
            Keystore-Provider: SUN

            Keystore-Einträge: 1

            Aliasname: androiddebugkey
            Erstellungsdatum: 20.10.2026
            Eintragstyp: PrivateKeyEntry
            Zertifikatskettenlänge: 1
            Zertifikat[1]:
            Eigentümer: C=US, O=Android, CN=Android Debug
            Aussteller: C=US, O=Android, CN=Android Debug
            Seriennummer: 1
            Gültig von: Tue Oct 20 10:00:00 GMT 2026 bis: Wed Oct 20 10:00:00 GMT 2056
            Zertifikat-Fingerabdrücke:
            	 SHA1: 58:8A:AA:AC:D6:AE:9F:E4:05:F2:A9:01:5F:90:AE:2F:C9:FE:83:AE
            	 SHA256: E5:18:DA:54:F2:5B:3E:1B:17:82:11:5C:4E:A3:56:92:5A:D0:BE:53:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55
            Signaturalgorithmusname: SHA256withRSA
            Algorithmus des öffentlichen Schlüssels: 2048-bit RSA key
            Version: 3
        """.trimIndent()

        val parsed = KeytoolOutputParser.parse(output)

        assertEquals("PKCS12", parsed.keystoreType)
        assertEquals(1, parsed.entries.size)

        val entry = parsed.entries.first()
        assertEquals("androiddebugkey", entry.alias)
        assertTrue(entry.isDebugKey)

        val cert = entry.primaryCertificate
        assertNotNull(cert)
        assertTrue(cert?.isDebugCertificate ?: false)
    }

    @Test
    fun parseMultipleAliases() {
        val output = """
            Keystore type: PKCS12
            Keystore provider: SUN

            Your keystore contains 2 entries

            Alias name: app_key_1
            Creation date: Jan 1, 2026
            Entry type: PrivateKeyEntry
            Certificate chain length: 1
            Certificate[1]:
            Owner: CN=Key One
            Issuer: CN=Key One
            Serial number: 1111
            Valid from: Jan 1, 2026 until: Jan 1, 2050
            Certificate fingerprints:
            	 SHA1: 11:11:11:11:11:11:11:11:11:11:11:11:11:11:11:11:11:11:11:11
            	 SHA256: 22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22:22
            Signature algorithm name: SHA256withECDSA
            Subject Public Key Algorithm: 256-bit EC key
            Version: 3

            Alias name: app_key_2
            Creation date: Feb 1, 2026
            Entry type: PrivateKeyEntry
            Certificate chain length: 1
            Certificate[1]:
            Owner: CN=Key Two
            Issuer: CN=Key Two
            Serial number: 2222
            Valid from: Feb 1, 2026 until: Feb 1, 2050
            Certificate fingerprints:
            	 SHA1: 33:33:33:33:33:33:33:33:33:33:33:33:33:33:33:33:33:33:33:33
            	 SHA256: 44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44:44
            Signature algorithm name: SHA256withRSA
            Subject Public Key Algorithm: 4096-bit RSA key
            Version: 3
        """.trimIndent()

        val parsed = KeytoolOutputParser.parse(output)

        assertEquals(2, parsed.entries.size)
        assertEquals("app_key_1", parsed.entries[0].alias)
        assertEquals("app_key_2", parsed.entries[1].alias)
        assertEquals("256-bit EC key", parsed.entries[0].primaryCertificate?.publicKeyAlgorithm)
        assertEquals("4096-bit RSA key", parsed.entries[1].primaryCertificate?.publicKeyAlgorithm)
    }
}
