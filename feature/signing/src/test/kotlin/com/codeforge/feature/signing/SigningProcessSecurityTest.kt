// Modul: :feature:signing
package com.codeforge.feature.signing

import android.content.Context
import com.codeforge.core.domain.model.CreateKeystoreRequest
import com.codeforge.core.domain.model.DistinguishedName
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Verbindliche Sicherheitsprüfung gemäß Task 34.1 & 34.4:
 * Passwörter dürfen NIEMALS in der Befehlszeilen-Argumentliste (sichtbar via `ps` oder `/proc/$PID/cmdline`)
 * an Kindprozesse übergeben werden!
 */
class SigningProcessSecurityTest {

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
    fun createKeystoreNeverExposesPasswordsInArguments() = runBlocking {
        val secretStorePass = "StorePass123!Sensitive"
        val secretKeyPass = "KeyPass456!Sensitive"

        val capturedCommands = mutableListOf<List<String>>()
        val capturedEnvs = mutableListOf<Map<String, String>>()

        repository.processLauncher = { cmd, env, _ ->
            capturedCommands.add(cmd)
            capturedEnvs.add(env)
            File(keystoreDir, "release.p12").writeText("mock keystore")
            FakeProcess(0, "Generating key pair...\nKeystore type: PKCS12\nAlias name: my_release\n")
        }

        val request = CreateKeystoreRequest(
            fileName = "release.p12",
            alias = "my_release",
            validityYears = 25,
            distinguishedName = DistinguishedName(commonName = "Developer", country = "DE"),
            algorithm = KeyAlgorithm.RSA_4096,
            storePassword = secretStorePass,
            keyPassword = secretKeyPass
        )

        repository.createKeystore(request)

        assertTrue(capturedCommands.isNotEmpty())
        assertTrue(capturedEnvs.isNotEmpty())

        // Verifikation: Kein Passwort in IRGENDEINEM ausgeführten Befehl!
        for (cmd in capturedCommands) {
            for (arg in cmd) {
                assertFalse("Argument contains secretStorePass: $arg", arg.contains(secretStorePass))
                assertFalse("Argument contains secretKeyPass: $arg", arg.contains(secretKeyPass))
            }
        }

        // Verifikation: Passwörter im genkeypair-Aufruf liegen nur in der Prozessumgebung (env)
        val genkeypairEnv = capturedEnvs.first()
        assertEquals(secretStorePass, genkeypairEnv["CF_STORE_PASS"])
        assertEquals(secretKeyPass, genkeypairEnv["CF_KEY_PASS"])

        // Verifikation: Flag verweist auf Umgebungsvariablen
        val genkeypairCmd = capturedCommands.first()
        assertTrue(genkeypairCmd.contains("-storepass:env"))
        assertTrue(genkeypairCmd.contains("CF_STORE_PASS"))
    }

    @Test
    fun importKeystoreNeverExposesPasswordsInArguments() = runBlocking {
        val secretPass = "ImportPassSecret999!"
        val sourceKs = File(filesDir, "external.p12").apply { writeText("mock keystore") }

        var capturedCommand: List<String>? = null
        var capturedEnv: Map<String, String>? = null

        repository.processLauncher = { cmd, env, _ ->
            capturedCommand = cmd
            capturedEnv = env
            FakeProcess(0, "Keystore type: PKCS12\nAlias name: external_alias\n")
        }

        repository.importKeystore(sourceKs, secretPass)

        assertNotNull(capturedCommand)
        for (arg in capturedCommand.orEmpty()) {
            assertFalse("Argument contains password!", arg.contains(secretPass))
        }

        assertEquals(secretPass, capturedEnv?.get("CF_STORE_PASS"))
        assertTrue(capturedCommand?.contains("-storepass:env") == true)
    }

    @Test
    fun signApkNeverExposesPasswordsInArguments() = runBlocking {
        val secretStorePass = "ApkStoreSecretPass"
        val secretKeyPass = "ApkKeySecretPass"

        val ksFile = File(keystoreDir, "release.p12").apply { writeText("mock keystore") }
        fakeSecretStorage.storeSecret("keystore_release.p12_storepass", secretStorePass)
        fakeSecretStorage.storeSecret("keystore_release.p12_release_keypass", secretKeyPass)

        val apkFile = File(filesDir, "test-unsigned.apk").apply { writeText("mock apk") }

        var capturedCommand: List<String>? = null
        var capturedEnv: Map<String, String>? = null

        repository.processLauncher = { cmd, env, _ ->
            capturedCommand = cmd
            capturedEnv = env
            FakeProcess(0, "Signed")
        }

        val request = SignApkRequest(
            inputApkPath = apkFile.absolutePath,
            keystoreId = "release.p12",
            alias = "release"
        )

        repository.signApk(request)

        assertNotNull(capturedCommand)
        for (arg in capturedCommand.orEmpty()) {
            assertFalse("Command contains secretStorePass!", arg.contains(secretStorePass))
            assertFalse("Command contains secretKeyPass!", arg.contains(secretKeyPass))
        }

        assertEquals(secretStorePass, capturedEnv?.get("CF_STORE_PASS"))
        assertEquals(secretKeyPass, capturedEnv?.get("CF_KEY_PASS"))
        assertTrue(capturedCommand?.contains("--ks-pass") == true)
        assertTrue(capturedCommand?.contains("env:CF_STORE_PASS") == true)
    }
}
