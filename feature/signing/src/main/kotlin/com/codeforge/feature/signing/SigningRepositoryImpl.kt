// Modul: :feature:signing
package com.codeforge.feature.signing

import android.content.Context
import com.codeforge.core.domain.model.ApkSignResult
import com.codeforge.core.domain.model.ApkVerificationResult
import com.codeforge.core.domain.model.CreateKeystoreRequest
import com.codeforge.core.domain.model.GradleSigningSnippet
import com.codeforge.core.domain.model.KeystoreEntryInfo
import com.codeforge.core.domain.model.KeystoreInfo
import com.codeforge.core.domain.model.ProjectSigningAssignment
import com.codeforge.core.domain.model.SignApkRequest
import com.codeforge.core.domain.parser.ApksignerOutputParser
import com.codeforge.core.domain.parser.KeytoolOutputParser
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.domain.repository.SigningRepository
import com.codeforge.core.domain.security.SecretStorage
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.terminal_engine.TermuxEnvironment
import com.codeforge.shared.termux.shell.command.environment.TermuxDevEnvironment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
open class SigningRepositoryImpl internal constructor(
    private val keystoresDir: File,
    private val secretStorage: SecretStorage,
    private val gradleBuildRepository: GradleBuildRepository
) : SigningRepository {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        secretStorage: SecretStorage,
        gradleBuildRepository: GradleBuildRepository
    ) : this(
        File(context.filesDir, "keystores"),
        secretStorage,
        gradleBuildRepository
    )

    init {
        keystoresDir.mkdirs()
    }

    private val _activeBuildSigning = MutableStateFlow<Map<String, String>>(emptyMap())
    override val activeBuildSigning: StateFlow<Map<String, String>> = _activeBuildSigning.asStateFlow()

    private val assignmentsFile = File(keystoresDir, "project_assignments.json")
    private val _projectAssignments = MutableStateFlow<List<ProjectSigningAssignment>>(loadAssignments())

    private fun loadAssignments(): List<ProjectSigningAssignment> {
        return try {
            if (assignmentsFile.exists() && assignmentsFile.isFile) {
                ProjectSigningAssignmentJson.fromJson(assignmentsFile.readText(Charsets.UTF_8))
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAssignments(assignments: List<ProjectSigningAssignment>) {
        try {
            assignmentsFile.parentFile?.mkdirs()
            assignmentsFile.writeText(ProjectSigningAssignmentJson.toJson(assignments), Charsets.UTF_8)
            _projectAssignments.value = assignments
        } catch (_: Exception) {
        }
    }

    private fun normalizePath(path: String): String {
        return try {
            File(path).canonicalPath.trimEnd('/')
        } catch (_: Exception) {
            File(path).absolutePath.trimEnd('/')
        }
    }

    override fun getProjectAssignments(): Flow<List<ProjectSigningAssignment>> = _projectAssignments.asStateFlow()

    override suspend fun getAssignmentForProject(projectPath: String): ProjectSigningAssignment? = withContext(Dispatchers.IO) {
        val norm = normalizePath(projectPath)
        _projectAssignments.value.firstOrNull { normalizePath(it.projectPath) == norm }
    }

    override suspend fun assignKeyToProject(projectPath: String, keystoreId: String, alias: String): Result<Unit> = withContext(Dispatchers.IO) {
        val norm = normalizePath(projectPath)
        val current = _projectAssignments.value.filterNot { normalizePath(it.projectPath) == norm }
        val updated = current + ProjectSigningAssignment(projectPath, keystoreId, alias)
        saveAssignments(updated)
        Result.success(Unit)
    }

    override suspend fun removeAssignmentForProject(projectPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        val norm = normalizePath(projectPath)
        val updated = _projectAssignments.value.filterNot { normalizePath(it.projectPath) == norm }
        saveAssignments(updated)
        Result.success(Unit)
    }

    override suspend fun removeAssignmentsForKeystore(keystoreId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val updated = _projectAssignments.value.filterNot { it.keystoreId == keystoreId }
        saveAssignments(updated)
        Result.success(Unit)
    }

    override suspend fun removeAssignmentsForAlias(keystoreId: String, alias: String): Result<Unit> = withContext(Dispatchers.IO) {
        val updated = _projectAssignments.value.filterNot { it.keystoreId == keystoreId && it.alias == alias }
        saveAssignments(updated)
        Result.success(Unit)
    }

    override suspend fun resolveBuildSigning(projectPath: String?): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        // 1. Zuweisung des Projekts
        if (!projectPath.isNullOrBlank()) {
            val assignment = getAssignmentForProject(projectPath)
            if (assignment != null) {
                val keystoreFile = File(keystoresDir, assignment.keystoreId).takeIf { it.exists() }
                    ?: File(assignment.keystoreId).takeIf { it.exists() }
                if (keystoreFile != null) {
                    val storePass = secretStorage.getSecret("keystore_${keystoreFile.name}_storepass")
                    if (storePass != null) {
                        val keyPass = secretStorage.getSecret("keystore_${keystoreFile.name}_${assignment.alias}_keypass") ?: storePass
                        val envMap = mapOf(
                            GradleSigningSnippet.ENV_KEYSTORE_PATH to keystoreFile.absolutePath,
                            GradleSigningSnippet.ENV_KEYSTORE_ALIAS to assignment.alias,
                            GradleSigningSnippet.ENV_KEYSTORE_PASSWORD to storePass,
                            GradleSigningSnippet.ENV_KEY_PASSWORD to keyPass
                        )
                        return@withContext Result.success(envMap)
                    }
                }
            }
        }

        // 2. Bisherige globale Aktivierung ("Standardschlüssel", bleibt für Projekte ohne Zuweisung)
        val global = _activeBuildSigning.value
        if (global.isNotEmpty()) {
            return@withContext Result.success(global)
        }

        // 3. Kein Signing
        Result.success(emptyMap())
    }

    override suspend fun importKeystoreFromStream(
        inputStream: InputStream,
        originalFileName: String,
        storePassword: String,
        customName: String?
    ): Result<KeystoreInfo> = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile("import_saf_", ".tmp", keystoresDir)
        try {
            tempFile.outputStream().use { out ->
                inputStream.copyTo(out)
            }
            importKeystore(tempFile, storePassword, customName ?: originalFileName)
        } finally {
            tempFile.delete()
        }
    }

    override suspend fun exportKeystoreToStream(
        id: String,
        outputStream: OutputStream
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val source = File(keystoresDir, id)
        if (!source.exists() || !source.isFile) {
            return@withContext Result.failure(IllegalStateException(Res.string(R.string.signing_keystore_nicht_gefunden, id)))
        }
        source.inputStream().use { input ->
            input.copyTo(outputStream)
        }
        Result.success(Unit)
    }

    // Testbarer Launcher
    internal var processLauncher: (command: List<String>, env: Map<String, String>, workingDir: File) -> Process = { cmd, env, dir ->
        ProcessBuilder(cmd).apply {
            directory(dir)
            environment().clear()
            environment().putAll(env)
            redirectErrorStream(true)
        }.start()
    }

    override fun isJdkAvailable(): Boolean = resolveKeytoolBinary() != null

    override fun isApksignerAvailable(): Boolean = resolveApksignerBinary() != null

    override suspend fun getKeystores(): List<KeystoreInfo> = withContext(Dispatchers.IO) {
        val files = keystoresDir.listFiles { f ->
            f.isFile && (f.name.endsWith(".keystore") || f.name.endsWith(".jks") || f.name.endsWith(".p12"))
        } ?: emptyArray()

        files.sortedByDescending { it.lastModified() }.map { file ->
            inspectKeystore(file)
        }
    }

    override suspend fun getKeystore(id: String): KeystoreInfo? = withContext(Dispatchers.IO) {
        val file = File(keystoresDir, id)
        if (!file.exists() || !file.isFile) return@withContext null
        inspectKeystore(file)
    }

    override suspend fun createKeystore(request: CreateKeystoreRequest): Result<KeystoreInfo> = withContext(Dispatchers.IO) {
        val keytool = resolveKeytoolBinary()
            ?: return@withContext Result.failure(IllegalStateException(Res.string(R.string.signing_jdk_nicht_installiert)))

        val cleanFileName = if (request.fileName.endsWith(".keystore") || request.fileName.endsWith(".p12") || request.fileName.endsWith(".jks")) {
            request.fileName
        } else {
            "${request.fileName}.p12"
        }

        val targetFile = File(keystoresDir, cleanFileName)
        if (targetFile.exists()) {
            return@withContext Result.failure(IllegalStateException(Res.string(R.string.signing_keystore_existiert_bereits, cleanFileName)))
        }

        val command = buildList {
            add(keytool.absolutePath)
            add("-genkeypair")
            add("-alias")
            add(request.alias)
            add("-keyalg")
            add(request.algorithm.algorithmName)
            val ecGroup = request.algorithm.groupName
            if (request.algorithm.isEc && ecGroup != null) {
                add("-groupname")
                add(ecGroup)
            } else {
                add("-keysize")
                add(request.algorithm.keySize.toString())
            }
            add("-validity")
            add(request.validityDays.toString())
            add("-keystore")
            add(targetFile.absolutePath)
            add("-storetype")
            add("PKCS12")
            add("-dname")
            add(request.distinguishedName.toDnameString())
            // Sicherheitsvorgabe 34.1: Passwörter NIEMALS als Befehlsargumente übergeben
            add("-storepass:env")
            add("CF_STORE_PASS")
            add("-keypass:env")
            add("CF_KEY_PASS")
        }

        val env = TermuxEnvironment.environment().toMutableMap().apply {
            put("CF_STORE_PASS", request.storePassword)
            put("CF_KEY_PASS", request.keyPassword)
        }

        val runResult = runProcess(command, env, keystoresDir, timeoutSeconds = 60)
        if (runResult.exitCode != 0) {
            targetFile.delete()
            val errorMsg = runResult.output.lines().filter { it.isNotBlank() }.takeLast(3).joinToString("; ")
            return@withContext Result.failure(IllegalStateException(Res.string(R.string.signing_erstellung_fehlgeschlagen, errorMsg)))
        }

        val id = targetFile.name
        secretStorage.storeSecret("keystore_${id}_storepass", request.storePassword)
        secretStorage.storeSecret("keystore_${id}_${request.alias}_keypass", request.keyPassword)

        Result.success(inspectKeystore(targetFile))
    }

    override suspend fun importKeystore(
        sourceFile: File,
        storePassword: String,
        customName: String?
    ): Result<KeystoreInfo> = withContext(Dispatchers.IO) {
        if (!sourceFile.exists() || !sourceFile.isFile) {
            return@withContext Result.failure(IllegalArgumentException(Res.string(R.string.signing_datei_nicht_gefunden, sourceFile.absolutePath)))
        }

        val keytool = resolveKeytoolBinary()
            ?: return@withContext Result.failure(IllegalStateException(Res.string(R.string.signing_jdk_nicht_installiert)))

        // Vorab-Passwortprüfung via keytool -list
        val checkCommand = listOf(
            keytool.absolutePath,
            "-list",
            "-keystore",
            sourceFile.absolutePath,
            "-storepass:env",
            "CF_STORE_PASS"
        )
        val env = TermuxEnvironment.environment().toMutableMap().apply {
            put("CF_STORE_PASS", storePassword)
        }

        val checkResult = runProcess(checkCommand, env, keystoresDir, timeoutSeconds = 30)
        if (checkResult.exitCode != 0) {
            val errorMsg = checkResult.output.lines().filter { it.isNotBlank() }.takeLast(2).joinToString("; ")
            return@withContext Result.failure(IllegalArgumentException(Res.string(R.string.signing_passwort_ungueltig, errorMsg)))
        }

        val destName = customName?.takeIf { it.isNotBlank() } ?: sourceFile.name
        var targetFile = File(keystoresDir, destName)
        var counter = 1
        val baseName = destName.substringBeforeLast(".")
        val ext = destName.substringAfterLast(".", "keystore")
        while (targetFile.exists()) {
            targetFile = File(keystoresDir, "$baseName-$counter.$ext")
            counter++
        }

        sourceFile.copyTo(targetFile, overwrite = true)
        val id = targetFile.name
        secretStorage.storeSecret("keystore_${id}_storepass", storePassword)

        val info = inspectKeystore(targetFile)
        info.primaryAlias?.let { alias ->
            secretStorage.storeSecret("keystore_${id}_${alias}_keypass", storePassword)
        }

        Result.success(info)
    }

    override suspend fun deleteKeystore(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val file = File(keystoresDir, id)
        if (file.exists()) {
            file.delete()
        }
        secretStorage.removeSecret("keystore_${id}_storepass")

        if (_activeBuildSigning.value[GradleSigningSnippet.ENV_KEYSTORE_PATH] == file.absolutePath) {
            clearBuildSigning()
        }

        removeAssignmentsForKeystore(id)

        Result.success(Unit)
    }

    override suspend fun exportKeystore(id: String, targetFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        val source = File(keystoresDir, id)
        if (!source.exists() || !source.isFile) {
            return@withContext Result.failure(IllegalStateException(Res.string(R.string.signing_keystore_nicht_gefunden, id)))
        }
        targetFile.parentFile?.mkdirs()
        source.copyTo(targetFile, overwrite = true)
        Result.success(Unit)
    }

    override suspend fun signApk(request: SignApkRequest): Result<ApkSignResult> = withContext(Dispatchers.IO) {
        val apksigner = resolveApksignerBinary()
            ?: return@withContext Result.failure(IllegalStateException(Res.string(R.string.signing_apksigner_nicht_installiert)))

        val inputFile = File(request.inputApkPath)
        if (!inputFile.exists() || !inputFile.isFile) {
            return@withContext Result.failure(IllegalArgumentException(Res.string(R.string.signing_apk_nicht_gefunden, request.inputApkPath)))
        }

        val keystoreFile = File(keystoresDir, request.keystoreId).takeIf { it.exists() }
            ?: File(request.keystoreId).takeIf { it.exists() }
            ?: return@withContext Result.failure(IllegalStateException(Res.string(R.string.signing_keystore_nicht_gefunden, request.keystoreId)))

        val storePassword = secretStorage.getSecret("keystore_${keystoreFile.name}_storepass")
            ?: return@withContext Result.failure(IllegalStateException(Res.string(R.string.signing_keystore_passwort_fehlt)))

        val keyPassword = secretStorage.getSecret("keystore_${keystoreFile.name}_${request.alias}_keypass") ?: storePassword

        val outputPath = request.resolveOutputPath()
        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()

        val command = buildList {
            add(apksigner.absolutePath)
            add("sign")
            add("--ks")
            add(keystoreFile.absolutePath)
            add("--ks-key-alias")
            add(request.alias)
            // Sicherheitsvorgabe 34.1: Passwörter NIEMALS über Argumente weitergeben
            add("--ks-pass")
            add("env:CF_STORE_PASS")
            add("--key-pass")
            add("env:CF_KEY_PASS")
            add("--in")
            add(inputFile.absolutePath)
            add("--out")
            add(outputFile.absolutePath)
            add("-v")

            request.minSdkVersion?.let {
                add("--min-sdk-version")
                add(it.toString())
            }
            request.v1SigningEnabled?.let {
                add("--v1-signing-enabled")
                add(it.toString())
            }
            add("--v2-signing-enabled")
            add(request.v2SigningEnabled.toString())
            add("--v3-signing-enabled")
            add(request.v3SigningEnabled.toString())
            add("--v4-signing-enabled")
            add(request.v4SigningEnabled.toString())
        }

        val env = TermuxEnvironment.environment().toMutableMap().apply {
            put("CF_STORE_PASS", storePassword)
            put("CF_KEY_PASS", keyPassword)
        }

        val runResult = runProcess(command, env, inputFile.parentFile ?: keystoresDir, timeoutSeconds = 120)

        if (runResult.exitCode != 0) {
            return@withContext Result.success(
                ApkSignResult(
                    success = false,
                    outputApkPath = null,
                    errorMessages = runResult.output.lines().filter { it.isNotBlank() },
                    verificationResult = null
                )
            )
        }

        // Anschließend apksigner verify --verbose --print-certs
        val verifyRes = verifyApk(outputFile).getOrNull()

        Result.success(
            ApkSignResult(
                success = true,
                outputApkPath = outputFile.absolutePath,
                outputApkSizeBytes = outputFile.length(),
                outputMessages = runResult.output.lines().filter { it.isNotBlank() },
                verificationResult = verifyRes
            )
        )
    }

    override suspend fun verifyApk(apkFile: File): Result<ApkVerificationResult> = withContext(Dispatchers.IO) {
        val apksigner = resolveApksignerBinary()
            ?: return@withContext Result.failure(IllegalStateException(Res.string(R.string.signing_apksigner_nicht_installiert)))

        if (!apkFile.exists() || !apkFile.isFile) {
            return@withContext Result.failure(IllegalArgumentException(Res.string(R.string.signing_apk_nicht_gefunden, apkFile.absolutePath)))
        }

        val command = listOf(
            apksigner.absolutePath,
            "verify",
            "--verbose",
            "--print-certs",
            apkFile.absolutePath
        )

        val env = TermuxEnvironment.environment()
        val runResult = runProcess(command, env, apkFile.parentFile ?: keystoresDir, timeoutSeconds = 60)
        val parsed = ApksignerOutputParser.parse(runResult.output)

        Result.success(parsed)
    }

    override suspend fun configureBuildSigning(keystoreId: String, alias: String): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        val keystoreFile = File(keystoresDir, keystoreId).takeIf { it.exists() }
            ?: return@withContext Result.failure(IllegalStateException(Res.string(R.string.signing_keystore_nicht_gefunden, keystoreId)))

        val storePassword = secretStorage.getSecret("keystore_${keystoreFile.name}_storepass")
            ?: return@withContext Result.failure(IllegalStateException(Res.string(R.string.signing_keystore_passwort_fehlt)))

        val keyPassword = secretStorage.getSecret("keystore_${keystoreFile.name}_${alias}_keypass") ?: storePassword

        val envMap = mapOf(
            GradleSigningSnippet.ENV_KEYSTORE_PATH to keystoreFile.absolutePath,
            GradleSigningSnippet.ENV_KEYSTORE_ALIAS to alias,
            GradleSigningSnippet.ENV_KEYSTORE_PASSWORD to storePassword,
            GradleSigningSnippet.ENV_KEY_PASSWORD to keyPassword
        )

        _activeBuildSigning.value = envMap
        gradleBuildRepository.setExtraEnvironment(envMap)

        Result.success(envMap)
    }

    override suspend fun clearBuildSigning() {
        _activeBuildSigning.value = emptyMap()
        gradleBuildRepository.setExtraEnvironment(emptyMap())
    }

    private suspend fun inspectKeystore(file: File): KeystoreInfo {
        val id = file.name
        val hasPassword = secretStorage.getSecret("keystore_${id}_storepass") != null
        val keytool = resolveKeytoolBinary()

        if (!hasPassword || keytool == null) {
            return KeystoreInfo(
                id = id,
                name = id,
                filePath = file.absolutePath,
                fileSizeBytes = file.length(),
                lastModifiedMillis = file.lastModified(),
                hasStoredPassword = hasPassword
            )
        }

        val storePassword = secretStorage.getSecret("keystore_${id}_storepass") ?: ""
        val command = listOf(
            keytool.absolutePath,
            "-list",
            "-v",
            "-keystore",
            file.absolutePath,
            "-storepass:env",
            "CF_STORE_PASS"
        )
        val env = TermuxEnvironment.environment().toMutableMap().apply {
            put("CF_STORE_PASS", storePassword)
        }

        val runResult = runProcess(command, env, keystoresDir, timeoutSeconds = 30)
        if (runResult.exitCode == 0) {
            val parsed = KeytoolOutputParser.parse(runResult.output)
            return KeystoreInfo(
                id = id,
                name = id,
                filePath = file.absolutePath,
                fileSizeBytes = file.length(),
                type = parsed.keystoreType,
                entries = parsed.entries,
                lastModifiedMillis = file.lastModified(),
                hasStoredPassword = true
            )
        }

        return KeystoreInfo(
            id = id,
            name = id,
            filePath = file.absolutePath,
            fileSizeBytes = file.length(),
            lastModifiedMillis = file.lastModified(),
            hasStoredPassword = true
        )
    }

    internal open fun resolveKeytoolBinary(): File? {
        val candidates = mutableListOf<File>()

        TermuxDevEnvironment.findJavaHome()?.let {
            candidates.add(File(it, "bin/keytool"))
        }

        candidates.add(File(TermuxEnvironment.prefix, "lib/jvm/java-17-openjdk/bin/keytool"))
        candidates.add(File(TermuxEnvironment.prefix, "bin/keytool"))

        val jvmDir = File(TermuxEnvironment.prefix, "lib/jvm")
        if (jvmDir.isDirectory) {
            jvmDir.listFiles()?.filter { it.isDirectory }?.forEach { dir ->
                candidates.add(File(dir, "bin/keytool"))
            }
        }

        System.getenv("PATH")?.split(":")?.forEach { p ->
            if (p.isNotBlank()) candidates.add(File(p, "keytool"))
        }

        return candidates.firstOrNull { it.isFile && it.canExecute() }
    }

    internal open fun resolveApksignerBinary(): File? {
        val candidates = mutableListOf<File>()

        val sdkRoot = File(TermuxEnvironment.sdkRoot)
        val buildTools = File(sdkRoot, "build-tools")
        if (buildTools.isDirectory) {
            buildTools.listFiles()
                ?.filter { it.isDirectory }
                ?.sortedByDescending { it.name }
                ?.forEach { dir ->
                    candidates.add(File(dir, "apksigner"))
                }
        }

        candidates.add(File(TermuxEnvironment.prefix, "bin/apksigner"))

        System.getenv("PATH")?.split(":")?.forEach { p ->
            if (p.isNotBlank()) candidates.add(File(p, "apksigner"))
        }

        return candidates.firstOrNull { it.isFile && it.canExecute() }
    }

    private data class ProcessRunResult(val exitCode: Int, val output: String)

    private fun runProcess(
        command: List<String>,
        env: Map<String, String>,
        workingDir: File,
        timeoutSeconds: Long
    ): ProcessRunResult {
        return try {
            val process = processLauncher(command, env, workingDir)
            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
            val outputSb = StringBuilder()

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return ProcessRunResult(-1, "Process timed out after ${timeoutSeconds}s")
            }

            var line = reader.readLine()
            while (line != null) {
                outputSb.appendLine(line)
                line = reader.readLine()
            }

            ProcessRunResult(process.exitValue(), outputSb.toString())
        } catch (e: Exception) {
            ProcessRunResult(-1, e.message ?: "Unknown process execution error")
        }
    }
}
