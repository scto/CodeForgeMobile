// Modul: :feature:signing
package com.codeforge.feature.signing

import android.content.Context
import com.codeforge.core.domain.model.CreateKeystoreRequest
import com.codeforge.core.domain.model.DistinguishedName
import com.codeforge.core.domain.model.GradleSigningSnippet
import com.codeforge.core.domain.model.KeyAlgorithm
import com.codeforge.core.domain.model.SignApkRequest
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.domain.security.SecretStorage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SigningRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var keystoreDir: File
    private lateinit var repository: SigningRepositoryImpl
    private val fakeSecretStorage = FakeSecretStorage()
    private val fakeGradleRepository = FakeGradleBuildRepository()

    private class FakeSecretStorage : SecretStorage {
        val secrets = mutableMapOf<String, String>()
        override suspend fun getSecret(key: String): String? = secrets[key]
        override suspend fun storeSecret(key: String, secret: String) { secrets[key] = secret }
        override suspend fun removeSecret(key: String) { secrets.remove(key) }
    }

    private class FakeGradleBuildRepository : GradleBuildRepository {
        override val currentSession: StateFlow<com.codeforge.core.domain.model.BuildSession?> = MutableStateFlow(null)
        override suspend fun connect(projectRootPath: String): Result<Unit> = Result.success(Unit)
        override fun runTask(taskName: String) = emptyFlow<com.codeforge.core.domain.repository.BuildEvent>()
        override suspend fun cancelBuild() {}
        override suspend fun disconnect() {}
        var extraEnv = mapOf<String, String>()
        override fun setExtraEnvironment(env: Map<String, String>) { extraEnv = env }
        override fun getExtraEnvironment(): Map<String, String> = extraEnv
    }

    private class FakeProcess(
        private val exitCode: Int = 0,
        private val outputText: String = ""
    ) : Process() {
        override fun getOutputStream(): OutputStream = ByteArrayOutputStream()
        override fun getInputStream(): InputStream = outputText.byteInputStream()
        override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
        override fun waitFor(): Int = exitCode
        override fun exitValue(): Int = exitCode
        override fun destroy() {}
    }

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("app_files")
        keystoreDir = File(filesDir, "keystores").apply { mkdirs() }

        repository = object : SigningRepositoryImpl(keystoreDir, fakeSecretStorage, fakeGradleRepository) {
            override fun resolveKeytoolBinary(): File = File(filesDir, "keytool").apply { writeText(""); setExecutable(true) }
            override fun resolveApksignerBinary(): File = File(filesDir, "apksigner").apply { writeText(""); setExecutable(true) }
        }
    }

    @Test
    fun createAndInspectKeystore() = runBlocking {
        repository.processLauncher = { cmd, _, _ ->
            if (cmd.contains("-genkeypair")) {
                File(keystoreDir, "release.p12").writeText("fake keystore bytes")
                FakeProcess(0, "Generating key pair...")
            } else if (cmd.contains("-list")) {
                FakeProcess(
                    0,
                    """
                        Keystore type: PKCS12
                        Alias name: prod_key
                        Creation date: Oct 20, 2026
                        Entry type: PrivateKeyEntry
                        Certificate[1]:
                        Owner: CN=Production, O=CodeForge, C=DE
                        Serial number: 12345
                        Valid from: Oct 20, 2026 until: Oct 20, 2051
                        Certificate fingerprints:
                        	 SHA256: 11:22:33:44:55:66:77:88:99:00:11:22:33:44:55:66:77:88:99:00:11:22:33:44:55:66:77:88:99:00:11:22
                        Subject Public Key Algorithm: 4096-bit RSA key
                    """.trimIndent()
                )
            } else {
                FakeProcess(0, "")
            }
        }

        val request = CreateKeystoreRequest(
            fileName = "release.p12",
            alias = "prod_key",
            validityYears = 25,
            distinguishedName = DistinguishedName(commonName = "Production", organization = "CodeForge", country = "DE"),
            algorithm = KeyAlgorithm.RSA_4096,
            storePassword = "SecurePassword123!",
            keyPassword = "SecurePassword123!"
        )

        val result = repository.createKeystore(request)
        assertTrue(result.isSuccess)

        val ks = result.getOrNull()
        assertNotNull(ks)
        assertEquals("release.p12", ks?.name)
        assertTrue(ks?.hasStoredPassword == true)
        assertEquals("prod_key", ks?.primaryAlias)
        assertEquals("PKCS12", ks?.type)
        assertFalse(ks?.isDebugKeystore ?: true)

        // Geheimnis in SecretStorage
        assertEquals("SecurePassword123!", fakeSecretStorage.getSecret("keystore_release.p12_storepass"))

        // In Keystore-Liste auffindbar
        val all = repository.getKeystores()
        assertEquals(1, all.size)
        assertEquals("release.p12", all.first().id)
    }

    @Test
    fun exportAndDeleteKeystore() = runBlocking {
        val ksFile = File(keystoreDir, "my-key.keystore").apply { writeText("mock keystore content") }
        fakeSecretStorage.storeSecret("keystore_my-key.keystore_storepass", "pass123")

        val targetExport = File(tempFolder.newFolder("downloads"), "exported.keystore")
        val exportResult = repository.exportKeystore("my-key.keystore", targetExport)
        assertTrue(exportResult.isSuccess)
        assertTrue(targetExport.exists())
        assertEquals("mock keystore content", targetExport.readText())

        val deleteResult = repository.deleteKeystore("my-key.keystore")
        assertTrue(deleteResult.isSuccess)
        assertFalse(ksFile.exists())
        assertNull(fakeSecretStorage.getSecret("keystore_my-key.keystore_storepass"))
    }

    @Test
    fun signApkAndVerify() = runBlocking {
        val ksFile = File(keystoreDir, "release.p12").apply { writeText("mock keystore") }
        fakeSecretStorage.storeSecret("keystore_release.p12_storepass", "store123")
        fakeSecretStorage.storeSecret("keystore_release.p12_alias_keypass", "key123")

        val inApk = File(filesDir, "app-release-unsigned.apk").apply { writeText("mock unsigned apk") }

        repository.processLauncher = { cmd, _, _ ->
            if (cmd.contains("sign")) {
                // Erzeuge Ausgabedatei
                val outIdx = cmd.indexOf("--out")
                if (outIdx >= 0 && outIdx + 1 < cmd.size) {
                    File(cmd[outIdx + 1]).writeText("mock signed apk")
                }
                FakeProcess(0, "Signed successfully")
            } else if (cmd.contains("verify")) {
                FakeProcess(
                    0,
                    """
                        Verifies
                        Verified using v1 scheme (JAR signing): true
                        Verified using v2 scheme (APK Signature Scheme v2): true
                        Number of signers: 1
                        Signer #1 certificate DN: CN=Production, O=CodeForge, C=DE
                        Signer #1 certificate SHA-256 digest: 1122334455667788990011223344556677889900112233445566778899001122
                    """.trimIndent()
                )
            } else {
                FakeProcess(0, "")
            }
        }

        val request = SignApkRequest(
            inputApkPath = inApk.absolutePath,
            keystoreId = "release.p12",
            alias = "alias"
        )

        val result = repository.signApk(request)
        assertTrue(result.isSuccess)

        val signRes = result.getOrNull()
        assertNotNull(signRes)
        assertTrue(signRes?.success == true)
        assertTrue(signRes?.outputApkPath?.endsWith("app-release-signed.apk") == true)
        assertTrue(signRes?.verificationResult?.isVerified == true)
        assertTrue(signRes?.verificationResult?.v2SchemeVerified == true)
    }

    @Test
    fun configureBuildSigningSetsEnvironmentOnGradle() = runBlocking {
        val ksFile = File(keystoreDir, "release.p12").apply { writeText("mock keystore") }
        fakeSecretStorage.storeSecret("keystore_release.p12_storepass", "mypass123")
        fakeSecretStorage.storeSecret("keystore_release.p12_prod_keypass", "mypass123")

        val res = repository.configureBuildSigning("release.p12", "prod")
        assertTrue(res.isSuccess)

        val env = repository.activeBuildSigning.value
        assertEquals(ksFile.absolutePath, env[GradleSigningSnippet.ENV_KEYSTORE_PATH])
        assertEquals("prod", env[GradleSigningSnippet.ENV_KEYSTORE_ALIAS])
        assertEquals("mypass123", env[GradleSigningSnippet.ENV_KEYSTORE_PASSWORD])
        assertEquals("mypass123", env[GradleSigningSnippet.ENV_KEY_PASSWORD])

        // Auch auf fakeGradleRepository gesetzt
        assertEquals(env, fakeGradleRepository.extraEnv)

        repository.clearBuildSigning()
        assertTrue(repository.activeBuildSigning.value.isEmpty())
        assertTrue(fakeGradleRepository.extraEnv.isEmpty())
    }
}
