// Modul: :core:domain
package com.codeforge.core.domain.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApksignerOutputParserTest {

    @Test
    fun parseSuccessfulVerificationWithDebugKey() {
        val output = """
            Verifies
            Verified using v1 scheme (JAR signing): false
            Verified using v2 scheme (APK Signature Scheme v2): true
            Verified using v3 scheme (APK Signature Scheme v3): false
            Verified using v3.1 scheme (APK Signature Scheme v3.1): false
            Verified using v3.2 scheme (APK Signature Scheme v3.2): false
            Verified using v4 scheme (APK Signature Scheme v4): false
            Verified for SourceStamp: false
            Number of signers: 1
            V2 Signer: certificate DN: C=US, O=Android, CN=Android Debug
            V2 Signer: certificate SHA-256 digest: 588aaaacd6ae9fe405f2a9015f90ae2fc9fe83ae9b7f3996c5f5c98d3603bea5
            V2 Signer: certificate SHA-1 digest: e518da54f25b3e1b1782115c4ea356925ad0be53
            V2 Signer: certificate MD5 digest: 8bfa916e23ba91b955adc3213f2ff4bb
            V2 Signer: key algorithm: RSA
            V2 Signer: key size (bits): 2048
            V2 Signer: public key SHA-256 digest: 46d432752f763aec52911b0282fe8a2ec19e39400c1b643a8be719bb0ef0bd64
            V2 Signer: public key SHA-1 digest: 36eb4a56d4faa535de2d8b483e21afc91171bb75
            V2 Signer: public key MD5 digest: b3a49af688ac407506a9ae1e93e6b4ec
        """.trimIndent()

        val result = ApksignerOutputParser.parse(output)

        assertTrue(result.isVerified)
        assertFalse(result.v1SchemeVerified)
        assertTrue(result.v2SchemeVerified)
        assertFalse(result.v3SchemeVerified)
        assertFalse(result.v4SchemeVerified)
        assertEquals(1, result.signerCount)
        assertEquals(1, result.certificates.size)

        val cert = result.certificates.first()
        assertEquals("C=US, O=Android, CN=Android Debug", cert.owner)
        assertEquals("58:8A:AA:AC:D6:AE:9F:E4:05:F2:A9:01:5F:90:AE:2F:C9:FE:83:AE:9B:7F:39:96:C5:F5:C9:8D:36:03:BE:A5", cert.sha256Fingerprint)
        assertEquals("E5:18:DA:54:F2:5B:3E:1B:17:82:11:5C:4E:A3:56:92:5A:D0:BE:53", cert.sha1Fingerprint)
        assertEquals("8B:FA:91:6E:23:BA:91:B9:55:AD:C3:21:3F:2F:F4:BB", cert.md5Fingerprint)
        assertEquals("2048-bit RSA", cert.publicKeyAlgorithm)
        assertTrue(cert.isDebugCertificate)
        assertTrue(result.isSignedWithDebugKey)
    }

    @Test
    fun parseMultiSchemeReleaseSigning() {
        val output = """
            Verifies
            Verified using v1 scheme (JAR signing): true
            Verified using v2 scheme (APK Signature Scheme v2): true
            Verified using v3 scheme (APK Signature Scheme v3): true
            Verified using v4 scheme (APK Signature Scheme v4): false
            Number of signers: 1
            Signer #1 certificate DN: CN=CodeForge Production, O=CodeForge Inc, C=DE
            Signer #1 certificate SHA-256 digest: a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2
            Signer #1 certificate SHA-1 digest: 1122334455667788990011223344556677889900
            Signer #1 key algorithm: RSA
            Signer #1 key size (bits): 4096
        """.trimIndent()

        val result = ApksignerOutputParser.parse(output)

        assertTrue(result.isVerified)
        assertTrue(result.v1SchemeVerified)
        assertTrue(result.v2SchemeVerified)
        assertTrue(result.v3SchemeVerified)
        assertFalse(result.v4SchemeVerified)
        assertEquals(1, result.signerCount)

        val cert = result.certificates.first()
        assertEquals("CN=CodeForge Production, O=CodeForge Inc, C=DE", cert.owner)
        assertFalse(cert.isDebugCertificate)
        assertFalse(result.isSignedWithDebugKey)
        assertEquals("4096-bit RSA", cert.publicKeyAlgorithm)
    }

    @Test
    fun parseFailedVerification() {
        val output = """
            DOES NOT VERIFY
            ERROR: No APK Signature Scheme v2 signature found, nor any other scheme
        """.trimIndent()

        val result = ApksignerOutputParser.parse(output)

        assertFalse(result.isVerified)
        assertFalse(result.v1SchemeVerified)
        assertFalse(result.v2SchemeVerified)
        assertEquals(0, result.certificates.size)
    }
}
