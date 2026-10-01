/**
 * Modul: :libs:terminal-engine
 * @author Thomas Schmid
 */
package com.codeforge.libs.terminal_engine

import android.content.Context
import android.util.Log
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.ToolItemProto
import com.codeforge.core.datastore.proto.UpdateIntervalProto
import com.codeforge.core.domain.model.JavaInfo
import com.codeforge.core.domain.model.SdkInstallEvent
import com.codeforge.core.domain.model.SdkUpdateInterval
import com.codeforge.core.domain.model.ToolItem
import com.codeforge.core.domain.repository.DistroBootstrapRepository
import com.codeforge.core.domain.repository.SdkRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

import com.codeforge.core.common.logging.AppLogger

@Singleton
class CommandlineSdkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val distroBootstrapRepository: DistroBootstrapRepository,
    private val settingsRepository: SettingsRepository
) : SdkRepository {

    private val progressRegex = Regex("""\[(=*)\s*]\s+(\d+)%\s*(.*)""")
    private val packageRowRegex = Regex("""^([\w.\-;]+)\s*\|\s*([\w.\-]+)\s*\|""")

    private val jdkInstaller = JdkInstaller(context, distroBootstrapRepository)

    private enum class ListSection { NONE, INSTALLED, AVAILABLE }

    private suspend fun currentDistro(): String =
        settingsRepository.appSettings.first().terminal.defaultDistro.ifBlank { "ubuntu" }

    private fun rootfsShellCommand(distro: String, script: String): List<String> {
        val rootfsDir = File(distroBootstrapRepository.rootfsPath(distro))
        val prootBinaryPath = "${context.applicationInfo.nativeLibraryDir}/libproot.so"
        val envSetup = "export ANDROID_HOME=/root/android-sdk; export ANDROID_SDK_ROOT=/root/android-sdk; export ANDROID_SDK_HOME=/root/android-sdk; export JAVA_HOME=\$(dirname \$(dirname \$(readlink -f \$(which java 2>/dev/null || echo /usr/bin/java)))); export PATH=\$PATH:\$JAVA_HOME/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$ANDROID_HOME/build-tools/36.0.0:\$ANDROID_HOME/build-tools; "
        return ProotCommandBuilder.build(
            prootBinaryPath = prootBinaryPath,
            rootfsDir = rootfsDir,
            command = listOf("/bin/sh", "-c", envSetup + script)
        )
    }

    private fun createProcessBuilder(distro: String, script: String): ProcessBuilder {
        val prootTmpDir = File(context.cacheDir, "proot_tmp").apply { mkdirs() }
        return ProcessBuilder(rootfsShellCommand(distro, script)).apply {
            environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
            environment()["PROOT_TMP_DIR"] = prootTmpDir.absolutePath
            environment()["TMPDIR"] = prootTmpDir.absolutePath
        }
    }

    override suspend fun sdkRootPath(): String? = withContext(Dispatchers.IO) {
        val distro = currentDistro()
        val rootfsDir = File(distroBootstrapRepository.rootfsPath(distro))
        val sdkDir = File(rootfsDir, "root/android-sdk")
        if (sdkDir.exists()) "/root/android-sdk" else {
            val script = "if [ -n \"\$ANDROID_HOME\" ]; then echo \"\$ANDROID_HOME\"; " +
                "elif [ -n \"\$ANDROID_SDK_ROOT\" ]; then echo \"\$ANDROID_SDK_ROOT\"; fi"
            runCatching {
                val process = createProcessBuilder(distro, script).redirectErrorStream(true).start()
                val output = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                output.ifBlank { null }
            }.getOrNull()
        }
    }

    override suspend fun getUpdateInterval(): SdkUpdateInterval = withContext(Dispatchers.IO) {
        val config = settingsRepository.appSettings.first().sdkManager
        when (config.updateInterval) {
            UpdateIntervalProto.UPDATE_INTERVAL_HOURLY -> SdkUpdateInterval.HOURLY
            UpdateIntervalProto.UPDATE_INTERVAL_SIX_HOURS -> SdkUpdateInterval.EVERY_6_HOURS
            UpdateIntervalProto.UPDATE_INTERVAL_DAILY -> SdkUpdateInterval.DAILY
            UpdateIntervalProto.UPDATE_INTERVAL_WEEKLY -> SdkUpdateInterval.WEEKLY
            UpdateIntervalProto.UPDATE_INTERVAL_MONTHLY -> SdkUpdateInterval.MONTHLY
            else -> SdkUpdateInterval.DAILY
        }
    }

    override suspend fun setUpdateInterval(interval: SdkUpdateInterval): Unit = withContext(Dispatchers.IO) {
        val proto = when (interval) {
            SdkUpdateInterval.HOURLY -> UpdateIntervalProto.UPDATE_INTERVAL_HOURLY
            SdkUpdateInterval.EVERY_6_HOURS -> UpdateIntervalProto.UPDATE_INTERVAL_SIX_HOURS
            SdkUpdateInterval.DAILY -> UpdateIntervalProto.UPDATE_INTERVAL_DAILY
            SdkUpdateInterval.WEEKLY -> UpdateIntervalProto.UPDATE_INTERVAL_WEEKLY
            SdkUpdateInterval.MONTHLY -> UpdateIntervalProto.UPDATE_INTERVAL_MONTHLY
        }
        settingsRepository.setSdkUpdateInterval(proto)
    }

    override suspend fun getCmdlineToolsInstalled(): Boolean = withContext(Dispatchers.IO) {
        settingsRepository.appSettings.first().sdkManager.cmdlineToolsInstalled
    }

    override suspend fun getLastDiagnosticMessage(): String = withContext(Dispatchers.IO) {
        settingsRepository.appSettings.first().sdkManager.lastDiagnosticMessage
    }

    override suspend fun listAvailablePackages(): Result<List<ToolItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val config = settingsRepository.appSettings.first().sdkManager
            val interval = getUpdateInterval()
            val now = System.currentTimeMillis()
            val timeElapsed = now - config.lastUpdateTimestamp
            val isExpired = timeElapsed >= interval.durationMs
            val isEmptyCache = (config.cachedBuildToolsCount == 0 && config.cachedPlatformToolsCount == 0) || (config.cachedBuildToolsCount == 0 && config.cachedJavaToolsCount == 0)

            if (!config.firstRunCompleted || !config.cmdlineToolsInstalled || isExpired || isEmptyCache) {
                refreshAndCachePackages().getOrThrow()
            } else {
                loadFromCache()
            }
        }
    }

    private suspend fun loadFromCache(): List<ToolItem> {
        val distro = currentDistro()
        val inRootfsSdkRoot = sdkRootPath() ?: "/root/android-sdk"
        val config = settingsRepository.appSettings.first().sdkManager

        val allProtos = config.cachedBuildToolsList +
                config.cachedPlatformToolsList +
                config.cachedNdkToolsList +
                config.cachedCmakeToolsList +
                config.cachedJavaToolsList

        return allProtos.map { proto ->
            val isJdk = proto.id.startsWith("jdk;")
            val isInstalled = proto.isInstalled || (if (isJdk) {
                jdkInstaller.isInstalled(distro, proto.version)
            } else {
                resolveInstalledPath(distro, proto.id, inRootfsSdkRoot) != null
            })
            val installedPath = if (isInstalled) {
                proto.path.takeIf { it.isNotBlank() }
                    ?: if (isJdk) jdkInstaller.jdkInstallDir(File(distroBootstrapRepository.rootfsPath(distro)), proto.version).absolutePath
                    else resolveInstalledPath(distro, proto.id, inRootfsSdkRoot)
            } else null

            ToolItem(
                id = proto.id,
                version = proto.version,
                description = proto.description,
                isInstalled = isInstalled,
                path = installedPath
            )
        }
    }

    override suspend fun isJavaInstalled(): Boolean = withContext(Dispatchers.IO) {
        getJavaInfo().isInstalled
    }

    override suspend fun getJavaInfo(): JavaInfo = withContext(Dispatchers.IO) {
        val distro = currentDistro()
        val rootfsDir = File(distroBootstrapRepository.rootfsPath(distro))
        if (!rootfsDir.exists()) {
            settingsRepository.setJavaInstalled(false, "", "")
            return@withContext JavaInfo(isInstalled = false, details = "Rootfs nicht eingerichtet")
        }

        // 1. Datastore pre-check
        val sdkConfig = settingsRepository.appSettings.first().sdkManager
        val dsInstalled = sdkConfig.javaInstalled
        val dsPath = sdkConfig.javaPath
        val dsVersion = sdkConfig.javaVersion

        if (dsInstalled && dsPath.isNotBlank()) {
            return@withContext JavaInfo(
                isInstalled = true,
                path = dsPath,
                version = dsVersion.ifBlank { "OpenJDK 17 (installiert)" },
                details = "Java aus DataStore verifiziert ($dsPath)"
            )
        }

        // 2. Filesystem probe
        val javaInOpt = JdkCatalog.all.any { jdkInstaller.isInstalled(distro, it.version) }
        val javaInUsrBin = File(rootfsDir, "usr/bin/java").isFile || File(rootfsDir, "bin/java").isFile
        val javaInJvm = File(rootfsDir, "usr/lib/jvm").listFiles()?.any { File(it, "bin/java").isFile } == true

        if (dsInstalled || javaInOpt || javaInUsrBin || javaInJvm) {
            val javaPath = if (dsPath.isNotBlank()) dsPath
            else if (javaInUsrBin) "/usr/bin/java"
            else if (javaInOpt) "/opt/jdk-17/bin/java"
            else "/usr/lib/jvm/default-java/bin/java"

            val versionLine = dsVersion.ifBlank { "OpenJDK 17 (installiert)" }
            settingsRepository.setJavaInstalled(true, versionLine, javaPath)
            return@withContext JavaInfo(
                isInstalled = true,
                path = javaPath,
                version = versionLine,
                details = "Java in Rootfs gefunden unter $javaPath"
            )
        }

        // 3. PRoot query ONLY if Datastore & Filesystem pre-checks did not find Java
        val lines = runRootfsScript(distro, "which java 2>/dev/null && java -version 2>&1", isLoggingEnabled = false)
        val javaPath = lines.firstOrNull { it.contains("java") && !it.contains("version", ignoreCase = true) }?.trim()
            ?: "/usr/lib/jvm/default-java/bin/java"

        val versionLine = lines.firstOrNull { it.contains("version", ignoreCase = true) || it.contains("OpenJDK", ignoreCase = true) }?.trim()
            ?: "OpenJDK 17 (installiert)"

        val isInstalled = lines.any { it.contains("java") || it.contains("version", ignoreCase = true) }

        // Persist verified Java status in DataStore
        settingsRepository.setJavaInstalled(isInstalled, if (isInstalled) versionLine else "", if (isInstalled) javaPath else "")

        JavaInfo(
            isInstalled = isInstalled,
            path = if (isInstalled) javaPath else null,
            version = if (isInstalled) versionLine else null,
            details = if (isInstalled) "Java gefunden unter $javaPath (DataStore verifiziert)" else "Keine Java (JDK) Installation in Rootfs gefunden"
        )
    }

    override suspend fun refreshAndCachePackages(): Result<List<ToolItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val isLoggingEnabled = settingsRepository.appSettings.first().debug.loggingEnabled
            val distro = currentDistro()

            if (isLoggingEnabled) AppLogger.step("SdkManager", "Starte Hintergrund-Aktualisierung der Paketlisten (Distro: $distro)...")

            // 1. ERST Google Commandline-Tools installieren
            val cmdlineInstalled = ensureCmdlineToolsInstalled(distro, isLoggingEnabled)
            if (!cmdlineInstalled) {
                val errMsg = "Abfrage der Paketlisten abgebrochen: Google Commandline-Tools konnte in PRoot Rootfs nicht eingerichtet werden."
                if (isLoggingEnabled) AppLogger.e("SdkManager", errMsg)
                settingsRepository.setSdkDiagnosticMessage(errMsg)
                error(errMsg)
            }

            // 2. ERST DANNACH: Java-Installation prüfen!
            val javaInfo = getJavaInfo()
            val javaList = fetchJavaPackages(distro, isLoggingEnabled)

            if (!javaInfo.isInstalled) {
                val javaMsg = "Java (JDK) ist im PRoot Rootfs noch nicht installiert. sdkmanager CLI benötigt Java. Bitte zuerst im Java-Tab OpenJDK installieren."
                if (isLoggingEnabled) AppLogger.w("SdkManager", javaMsg)
                settingsRepository.setSdkDiagnosticMessage(javaMsg)

                // Speichere die verfugbaren Java-Pakete im Cache, damit der Nutzer diese sofort im Java-Tab sieht
                settingsRepository.saveSdkManagerCache(
                    buildTools = emptyList(),
                    platformTools = emptyList(),
                    ndkTools = emptyList(),
                    cmakeTools = emptyList(),
                    javaTools = javaList.map { it.toProto() },
                    diagnosticMessage = javaMsg
                )

                error(javaMsg)
            }

            if (isLoggingEnabled) AppLogger.step("SdkManager", "Java verifiziert: ${javaInfo.path} (${javaInfo.version})")

            // 3. Wenn Java installiert ist: sdkmanager --sdk_root=/root/android-sdk --list ausführen
            val inRootfsSdkRoot = sdkRootPath() ?: "/root/android-sdk"
            
            // Testing: sdkmanager Aufruf ohne --sdk_root aber mit --verbose
            val sdkTestScript = "sdkmanager --verbose --list"
            if (isLoggingEnabled) AppLogger.step("SdkManager", "TESTING: Führe SDK-Manager CLI ohne --sdk_root aus: $sdkTestScript")
            runRootfsScript(distro, sdkTestScript, isLoggingEnabled)

            // Regulärer Aufruf mit --sdk_root und --verbose
            val sdkScript = "sdkmanager --sdk_root=$inRootfsSdkRoot --verbose --list"
            if (isLoggingEnabled) AppLogger.step("SdkManager", "Führe SDK-Manager CLI aus: $sdkScript")
            val sdkRawLines = runRootfsScript(distro, sdkScript, isLoggingEnabled)
            var allSdkItems = parseSdkManagerFullOutput(sdkRawLines, distro, inRootfsSdkRoot)

            if (allSdkItems.isEmpty()) {
                if (isLoggingEnabled) AppLogger.step("SdkManager", "sdkmanager CLI ergab 0 Pakete. Nutze AndroidRepoCrawler (Google XML Repositories Fallback)...")
                val crawlerItems = AndroidRepoCrawler.fetchPackages("linux")
                allSdkItems = crawlerItems.map { item ->
                    val isInstalled = resolveInstalledPath(distro, item.id, inRootfsSdkRoot) != null
                    val installedPath = if (isInstalled) resolveInstalledPath(distro, item.id, inRootfsSdkRoot) else null
                    item.copy(isInstalled = isInstalled, path = installedPath)
                }
            }

            val buildToolsList = allSdkItems.filter { it.id.startsWith("build-tools;") }
            val platformToolsList = allSdkItems.filter { it.id.startsWith("platform-tools") || it.id.startsWith("platforms;") }
            val ndkList = allSdkItems.filter { it.id.startsWith("ndk;") }
            val cmakeList = allSdkItems.filter { it.id.startsWith("cmake;") }

            val totalCount = buildToolsList.size + platformToolsList.size + ndkList.size + cmakeList.size + javaList.size
            val diagMsg = if (totalCount > 0) {
                "Paketlisten im Hintergrund erstellt: ${buildToolsList.size} Build-Tools, ${platformToolsList.size} Platform-Tools, ${ndkList.size} NDK, ${cmakeList.size} CMake, ${javaList.size} Java (Gesamt: $totalCount Pakete)."
            } else {
                val rawSample = if (sdkRawLines.isNotEmpty()) sdkRawLines.take(15).joinToString("\n") else "<Keine Antwort von CLI '$sdkScript'>"
                "Keine Pakete von sdkmanager CLI gefunden ($sdkScript). Roh-Ausgabe der CLI (${sdkRawLines.size} Zeilen):\n$rawSample"
            }

            if (isLoggingEnabled) AppLogger.step("SdkManager", diagMsg)

            // Save to Datastore
            settingsRepository.saveSdkManagerCache(
                buildTools = buildToolsList.map { it.toProto() },
                platformTools = platformToolsList.map { it.toProto() },
                ndkTools = ndkList.map { it.toProto() },
                cmakeTools = cmakeList.map { it.toProto() },
                javaTools = javaList.map { it.toProto() },
                diagnosticMessage = diagMsg
            )

            buildToolsList + platformToolsList + ndkList + cmakeList + javaList
        }
    }

    private fun ToolItem.toProto(): ToolItemProto = ToolItemProto.newBuilder()
        .setId(id)
        .setVersion(version)
        .setDescription(description)
        .setIsInstalled(isInstalled)
        .setPath(path.orEmpty())
        .build()

    private fun parseSdkManagerFullOutput(
        lines: List<String>,
        distro: String,
        inRootfsSdkRoot: String?
    ): List<ToolItem> {
        val items = mutableListOf<ToolItem>()
        var section = ListSection.NONE

        for (rawLine in lines) {
            val line = rawLine.trim()
            when {
                line.isEmpty() || line.startsWith("---") || line.startsWith("Path") -> Unit
                line.startsWith("Installed packages", ignoreCase = true) -> section = ListSection.INSTALLED
                line.startsWith("Available Packages", ignoreCase = true) -> section = ListSection.AVAILABLE
                else -> parsePackageRow(line, section, distro, inRootfsSdkRoot)?.let(items::add)
            }
        }
        return items
    }

    private suspend fun fetchJavaPackages(distro: String, isLoggingEnabled: Boolean): List<ToolItem> {
        val config = settingsRepository.appSettings.first().sdkManager
        if (config.cachedJavaToolsList.isNotEmpty()) {
            return config.cachedJavaToolsList.map { proto ->
                val installed = config.javaInstalled || jdkInstaller.isInstalled(distro, proto.version)
                ToolItem(
                    id = proto.id,
                    version = proto.version,
                    description = proto.description,
                    isInstalled = installed,
                    path = if (installed) (config.javaPath.ifBlank { jdkInstaller.jdkInstallDir(File(distroBootstrapRepository.rootfsPath(distro)), proto.version).absolutePath }) else null
                )
            }
        }
        val script = if (distro == "alpine") {
            "apk update && apk search -q \"^openjdk[0-9]+-jdk\" | sort -rV"
        } else {
            "apt update && apt-cache search \"^openjdk-[0-9]+-jdk\$\" | sort -rV"
        }

        val lines = runRootfsScript(distro, script, isLoggingEnabled)
        val items = mutableListOf<ToolItem>()
        val versionRegex = if (distro == "alpine") Regex("""openjdk(\d+)-jdk""") else Regex("""openjdk-(\d+)-jdk""")

        for (rawLine in lines) {
            val line = rawLine.trim()
            val match = versionRegex.find(line)
            if (match != null) {
                val version = match.groupValues[1]
                val installed = config.javaInstalled || jdkInstaller.isInstalled(distro, version)
                items.add(
                    ToolItem(
                        id = "jdk;$version",
                        version = version,
                        description = "Java Development Kit $version (OpenJDK)",
                        isInstalled = installed,
                        path = if (installed) (config.javaPath.ifBlank { jdkInstaller.jdkInstallDir(File(distroBootstrapRepository.rootfsPath(distro)), version).absolutePath }) else null
                    )
                )
            }
        }

        if (items.isEmpty()) {
            if (isLoggingEnabled) AppLogger.step("SdkManager", "Java Paketmanager ergab 0 Treffer. Nutze JdkCatalog Fallback.")
            return JdkCatalog.all.map { source ->
                val installed = config.javaInstalled || jdkInstaller.isInstalled(distro, source.version)
                ToolItem(
                    id = "jdk;${source.version}",
                    version = source.version,
                    description = "Java Development Kit ${source.version} (OpenJDK)",
                    isInstalled = installed,
                    path = if (installed) (config.javaPath.ifBlank { jdkInstaller.jdkInstallDir(File(distroBootstrapRepository.rootfsPath(distro)), source.version).absolutePath }) else null
                )
            }
        }
        return items
    }

    private fun runRootfsScript(distro: String, script: String, isLoggingEnabled: Boolean = true): List<String> {
        return runCatching {
            AppLogger.step("SdkManager", "REQ CLI: $script")
            val process = createProcessBuilder(distro, script)
                .redirectErrorStream(true)
                .start()
            val lines = process.inputStream.bufferedReader().readLines()
            val exitCode = process.waitFor()
            AppLogger.step("SdkManager", "RESP CLI (exitCode=$exitCode, totalLines=${lines.size}):")
            if (lines.isEmpty()) {
                AppLogger.step("SdkManager", "  [CLI-OUT] <Keine Textausgabe vom Befehl>")
            } else {
                lines.forEach { line ->
                    AppLogger.step("SdkManager", "  [CLI-OUT] $line")
                }
            }
            lines
        }.getOrElse { ex ->
            AppLogger.e("SdkManager", "Fehler beim Ausführen von CLI script: $script", ex)
            emptyList()
        }
    }

    private suspend fun ensureCmdlineToolsInstalled(distro: String, isLoggingEnabled: Boolean): Boolean {
        val rootfsDir = File(distroBootstrapRepository.rootfsPath(distro))
        if (!rootfsDir.exists()) {
            val msg = "Rootfs Verzeichnis nicht gefunden: ${rootfsDir.absolutePath}"
            if (isLoggingEnabled) AppLogger.e("SdkManager", msg)
            settingsRepository.setSdkDiagnosticMessage(msg)
            settingsRepository.setCmdlineToolsInstalled(false)
            return false
        }

        val sdkDirInRootfs = File(rootfsDir, "root/android-sdk")
        
        // Clean up broken circular symlink at /usr/bin/bash if present
        val usrBinBash = File(rootfsDir, "usr/bin/bash")
        runCatching {
            val path = usrBinBash.toPath()
            if (java.nio.file.Files.isSymbolicLink(path)) {
                val target = java.nio.file.Files.readSymbolicLink(path).toString()
                if (target.contains("bash") && !java.nio.file.Files.exists(path)) {
                    java.nio.file.Files.deleteIfExists(path)
                }
            }
        }

        // Ensure all required Android SDK/NDK/CMake subdirectories exist
        File(sdkDirInRootfs, "build-tools").mkdirs()
        File(sdkDirInRootfs, "platform-tools").mkdirs()
        File(sdkDirInRootfs, "cmdline-tools/latest").mkdirs()
        File(sdkDirInRootfs, "ndk").mkdirs()
        File(sdkDirInRootfs, "cmake").mkdirs()

        // Ensure /root/.bashrc has all required environment variable exports
        val bashrcFile = File(rootfsDir, "root/.bashrc")
        val exports = """
export TERM=xterm-256color
export COLORTERM=truecolor
# Android SDK
export ANDROID_HOME=${'$'}HOME/android-sdk
export PATH=${'$'}PATH:${'$'}ANDROID_HOME/cmdline-tools/latest/bin
export PATH=${'$'}PATH:${'$'}ANDROID_HOME/platform-tools
export PATH=${'$'}PATH:${'$'}ANDROID_HOME/build-tools
# Android NDK
export ANDROID_NDK_HOME=${'$'}ANDROID_HOME/ndk
export PATH=${'$'}PATH:${'$'}ANDROID_NDK_HOME
# CMAKE
export CMAKE_HOME=${'$'}ANDROID_HOME/cmake
export PATH=${'$'}PATH:${'$'}CMAKE_HOME
# JAVA
export JAVA_HOME=${'$'}(dirname ${'$'}(dirname ${'$'}(readlink -f ${'$'}(which java 2>/dev/null || echo /usr/lib/jvm/default-java/bin/java))))
export PATH=${'$'}JAVA_HOME/bin:${'$'}PATH
export JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Xmx2g"
# GRADLE
export GRADLE_HOME=${'$'}(dirname ${'$'}(dirname ${'$'}(readlink -f ${'$'}(which gradle 2>/dev/null || echo /usr/bin/gradle))))
export PATH=${'$'}PATH:${'$'}GRADLE_HOME/bin
# GRADLE USER HOME
export GRADLE_USER_HOME=${'$'}HOME/.gradle
""".trimIndent()

        if (bashrcFile.exists()) {
            val content = bashrcFile.readText()
            if (!content.contains("ANDROID_HOME=")) {
                bashrcFile.writeText(exports + "\n\n" + content)
            }
        } else {
            bashrcFile.parentFile?.mkdirs()
            bashrcFile.writeText(exports + "\n")
        }

        // Also write to /etc/profile.d/codeforge.sh and /etc/environment so ALL login & non-login shells have env vars
        val profileDDir = File(rootfsDir, "etc/profile.d")
        profileDDir.mkdirs()
        File(profileDDir, "codeforge.sh").writeText(exports + "\n")

        val etcEnvFile = File(rootfsDir, "etc/environment")
        etcEnvFile.parentFile?.mkdirs()
        val envContent = """
ANDROID_HOME=/root/android-sdk
ANDROID_SDK_ROOT=/root/android-sdk
ANDROID_NDK_HOME=/root/android-sdk/ndk
CMAKE_HOME=/root/android-sdk/cmake
JAVA_HOME=/usr/lib/jvm/default-java
GRADLE_HOME=/usr/share/gradle
GRADLE_USER_HOME=/root/.gradle
JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Xmx2g"
PATH="/usr/lib/jvm/default-java/bin:/root/android-sdk/cmdline-tools/latest/bin:/root/android-sdk/platform-tools:/root/android-sdk/build-tools:/root/android-sdk/ndk:/root/android-sdk/cmake:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
""".trimIndent()
        etcEnvFile.writeText(envContent + "\n")

        val sdkManagerBin = File(sdkDirInRootfs, "cmdline-tools/latest/bin/sdkmanager")

        if (sdkManagerBin.exists()) {
            AppLogger.step("SdkManager", "Google commandline-tools bereits vorhanden: ${sdkManagerBin.absolutePath}")
            settingsRepository.setCmdlineToolsInstalled(true)
            return true
        }

        AppLogger.step("SdkManager", "Starte automatische Installation der Google Commandline-Tools...")
        settingsRepository.setSdkDiagnosticMessage("Installiere Android Commandline-Tools & Gradle in PRoot Rootfs...")

        // 1. Install prerequisites via package manager in rootfs
        val pkgCmd = if (distro == "alpine") {
            "apk add --no-cache curl wget unzip tar git openjdk17-jdk gradle bash"
        } else {
            "apt-get update && apt-get install -y curl wget unzip tar git openjdk-17-jdk gradle bash"
        }
        AppLogger.step("SdkManager", "Installiere Paketmanager-Abhängigkeiten (curl, wget, tar, unzip, git, openjdk-17, gradle): $pkgCmd")
        runCatching {
            createProcessBuilder(distro, pkgCmd).redirectErrorStream(true).start().waitFor()
        }

        // 2. Download commandlinetools zip
        val toolsZip = File(rootfsDir, "tmp/cmdline-tools.zip")
        toolsZip.parentFile?.mkdirs()

        val downloadUrls = listOf(
            "https://dl.google.com/android/repository/commandlinetools-linux-15859902_latest.zip",
            "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
        )
        
        var downloadSuccess = false
        for (downloadUrl in downloadUrls) {
            AppLogger.step("SdkManager", "Versuche Download von $downloadUrl...")
            downloadSuccess = runCatching {
                java.net.URL(downloadUrl).openStream().use { input ->
                    toolsZip.outputStream().use { output -> input.copyTo(output) }
                }
                true
            }.getOrDefault(false)

            if (downloadSuccess && toolsZip.exists() && toolsZip.length() > 0) {
                AppLogger.step("SdkManager", "Download erfolgreich ($downloadUrl).")
                break
            }
        }

        if (!downloadSuccess || !toolsZip.exists()) {
            settingsRepository.setCmdlineToolsInstalled(false)
            return false
        }

        // 3. Unpack into android-sdk/cmdline-tools/latest
        val latestDir = File(sdkDirInRootfs, "cmdline-tools/latest")
        latestDir.mkdirs()

        AppLogger.step("SdkManager", "Entpacke cmdline-tools.zip nach ${latestDir.absolutePath}...")
        runCatching {
            java.util.zip.ZipInputStream(toolsZip.inputStream().buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    val relativePath = entryName.removePrefix("cmdline-tools/")
                    if (relativePath.isNotEmpty()) {
                        val targetFile = File(latestDir, relativePath)
                        if (entry.isDirectory) {
                            targetFile.mkdirs()
                        } else {
                            targetFile.parentFile?.mkdirs()
                            targetFile.outputStream().use { out -> zip.copyTo(out) }
                            if (relativePath.startsWith("bin/")) {
                                targetFile.setExecutable(true, false)
                            }
                        }
                    }
                    entry = zip.nextEntry
                }
            }
        }
        toolsZip.delete()

        // 4. Write pre-accepted license hashes
        val licensesDir = File(sdkDirInRootfs, "licenses")
        licensesDir.mkdirs()
        File(licensesDir, "android-sdk-license").writeText(
            "24333f8a63718c392d9080e206772cb2e444d1e6\n893307222625902b9f1176141758443f34566164\nd56f51874794514bf1a5f91ec65a85c6a1f76103\n"
        )
        File(licensesDir, "android-sdk-preview-license").writeText("84831b9409646a918e30573bab4c9c91346d8abd\n")
        File(licensesDir, "intel-android-sys-img-license").writeText("d975f751698a77b39ddd0b12179942c370484129\n")

        val installed = sdkManagerBin.exists()
        settingsRepository.setCmdlineToolsInstalled(installed)
        if (installed) {
            AppLogger.step("SdkManager", "cmdline-tools erfolgreich installiert und verifiziert.")
        } else {
            val err = "cmdline-tools Entpacken abgeschlossen, aber sdkmanager Binary wurde nicht unter ${sdkManagerBin.absolutePath} gefunden."
            AppLogger.e("SdkManager", err)
            settingsRepository.setSdkDiagnosticMessage(err)
        }
        return installed
    }

    private fun listViaSdkManager(distro: String, inRootfsSdkRoot: String?, isLoggingEnabled: Boolean): List<ToolItem> {
        val lines = runRootfsScript(distro, "sdkmanager --list", isLoggingEnabled)
        val items = mutableListOf<ToolItem>()
        var section = ListSection.NONE

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() || line.startsWith("---") || line.startsWith("Path") -> Unit
                line.startsWith("Installed packages", ignoreCase = true) -> section = ListSection.INSTALLED
                line.startsWith("Available Packages", ignoreCase = true) -> section = ListSection.AVAILABLE
                else -> parsePackageRow(line, section, distro, inRootfsSdkRoot)?.let(items::add)
            }
        }
        if (isLoggingEnabled) {
            AppLogger.step("SdkManager", "PARSED Fallback 'sdkmanager --list': ${items.size} Pakete aus ${lines.size} Zeilen extrahiert.")
        }
        return items
    }

    private fun parsePackageRow(line: String, section: ListSection, distro: String, inRootfsSdkRoot: String?): ToolItem? {
        if (section == ListSection.NONE) return null
        val match = packageRowRegex.find(line) ?: return null
        val path = match.groupValues[1].trim()
        val version = match.groupValues[2].trim()
        val installed = section == ListSection.INSTALLED
        val description = createDescription(path, version)
        return ToolItem(
            id = path,
            version = version,
            description = description,
            isInstalled = installed,
            path = if (installed) resolveInstalledPath(distro, path, inRootfsSdkRoot) else null
        )
    }

    private fun createDescription(id: String, version: String): String = when {
        id.startsWith("build-tools;") -> "Android SDK Build-Tools $version"
        id.startsWith("platforms;android-") -> {
            val api = id.substringAfter("platforms;android-")
            "Android SDK Platform $version (API Level $api)"
        }
        id.startsWith("ndk;") -> "Android Native Development Kit (NDK) $version"
        id.startsWith("cmake;") -> "CMake Compiler Tool $version"
        else -> "Android SDK Paket $version"
    }

    private fun resolveInstalledPath(distro: String, packagePath: String, inRootfsSdkRoot: String?): String? {
        if (inRootfsSdkRoot == null) return null
        val rootfsDir = File(distroBootstrapRepository.rootfsPath(distro))
        val relPath = packagePath.replace(';', '/')
        val targetDir = File(rootfsDir, inRootfsSdkRoot.removePrefix("/") + "/" + relPath)
        if (targetDir.isDirectory && targetDir.listFiles()?.isNotEmpty() == true) {
            return targetDir.absolutePath
        }
        val parentDir = targetDir.parentFile
        val baseName = targetDir.name
        if (parentDir != null && parentDir.isDirectory) {
            val matchedDir = parentDir.listFiles { f ->
                f.isDirectory && f.listFiles()?.isNotEmpty() == true &&
                    (f.name == baseName || f.name.startsWith(baseName) || baseName.startsWith(f.name.substringBefore('.')))
            }?.firstOrNull()
            if (matchedDir != null) return matchedDir.absolutePath
        }
        return null
    }

    override fun installSdkTool(packagePath: String): Flow<SdkInstallEvent> = flow {
        val distro = currentDistro()
        val isLoggingEnabled = settingsRepository.appSettings.first().debug.loggingEnabled

        if (isLoggingEnabled) AppLogger.step("SdkManager", "Starte Installation von $packagePath...")

        if (packagePath.startsWith("jdk;")) {
            val version = packagePath.substringAfter(';')
            jdkInstaller.install(distro, version).collect { emit(it) }
            return@flow
        }

        try {
            val inRootfsSdkRoot = sdkRootPath() ?: "/root/android-sdk"
            val installScript = "yes | sdkmanager --sdk_root=$inRootfsSdkRoot --verbose \"$packagePath\""
            if (isLoggingEnabled) AppLogger.step("SdkManager", "Führe SDK-Installation aus: $installScript")

            val process = createProcessBuilder(distro, installScript)
                .redirectErrorStream(true)
                .start()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue
                    if (isLoggingEnabled) AppLogger.step("SdkManager", "  [INSTALL-OUT] $currentLine")

                    val match = progressRegex.find(currentLine)
                    if (match != null) {
                        val percent = match.groupValues[2].toIntOrNull() ?: 0
                        val message = match.groupValues[3].trim()
                        emit(SdkInstallEvent.Progress(percent, message))
                    } else if (currentLine.contains("done", ignoreCase = true)) {
                        emit(SdkInstallEvent.Progress(100, "Installation abgeschlossen"))
                    }
                }
            }

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                if (isLoggingEnabled) AppLogger.step("SdkManager", "Installation von $packagePath erfolgreich.")
                emit(SdkInstallEvent.Success(packagePath))
            } else {
                val err = "sdkmanager beendet mit Code $exitCode bei $packagePath"
                if (isLoggingEnabled) AppLogger.e("SdkManager", err)
                emit(SdkInstallEvent.Error(RuntimeException(err)))
            }
        } catch (e: Exception) {
            if (isLoggingEnabled) AppLogger.e("SdkManager", "Fehler bei Installation von $packagePath", e)
            emit(SdkInstallEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun uninstallSdkTool(packagePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val distro = currentDistro()
            val isLoggingEnabled = settingsRepository.appSettings.first().debug.loggingEnabled

            if (isLoggingEnabled) AppLogger.step("SdkManager", "Starte Deinstallation von $packagePath...")

            if (packagePath.startsWith("jdk;")) {
                val version = packagePath.substringAfter(';')
                jdkInstaller.uninstall(distro, version).getOrThrow()
                return@runCatching
            }

            val inRootfsSdkRoot = sdkRootPath() ?: "/root/android-sdk"
            val process = createProcessBuilder(distro, "sdkmanager --sdk_root=$inRootfsSdkRoot --verbose --uninstall \"$packagePath\"")
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().forEachLine { line ->
                if (isLoggingEnabled) AppLogger.step("SdkManager", "  [UNINSTALL-OUT] ${line.trim()}")
            }
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val err = "sdkmanager --uninstall beendet mit Code $exitCode"
                if (isLoggingEnabled) AppLogger.e("SdkManager", err)
                error(err)
            }
            if (isLoggingEnabled) AppLogger.step("SdkManager", "Deinstallation von $packagePath erfolgreich.")
        }
    }

    override suspend fun syncInstalledToolsToDataStore(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val distro = currentDistro()
            val isLoggingEnabled = settingsRepository.appSettings.first().debug.loggingEnabled
            val rootfsDir = File(distroBootstrapRepository.rootfsPath(distro))
            if (!rootfsDir.exists()) return@runCatching

            val inRootfsSdkRoot = sdkRootPath() ?: "/root/android-sdk"
            val sdkDirInRootfs = File(rootfsDir, inRootfsSdkRoot.removePrefix("/"))

            // 1. Verify cmdline-tools
            val sdkManagerBin = File(sdkDirInRootfs, "cmdline-tools/latest/bin/sdkmanager")
            val cmdlineInstalled = sdkManagerBin.exists()
            settingsRepository.setCmdlineToolsInstalled(cmdlineInstalled)

            // 2. Verify Java
            val javaInfo = getJavaInfo()

            // 3. Update existing cache in DataStore or generate default list
            val config = settingsRepository.appSettings.first().sdkManager

            suspend fun updateProtoInstalledStatus(
                list: List<ToolItemProto>,
                checkInstalled: suspend (ToolItemProto) -> Boolean,
                getPath: suspend (ToolItemProto) -> String?
            ): List<ToolItemProto> = list.map { proto ->
                val installed = checkInstalled(proto)
                val p = if (installed) getPath(proto) else ""
                proto.toBuilder().setIsInstalled(installed).setPath(p.orEmpty()).build()
            }

            val isJdkInstalled: suspend (ToolItemProto) -> Boolean = { proto ->
                javaInfo.isInstalled || jdkInstaller.isInstalled(distro, proto.version)
            }
            val getJdkPath: suspend (ToolItemProto) -> String? = { proto ->
                javaInfo.path.takeIf { !it.isNullOrBlank() }
                    ?: jdkInstaller.jdkInstallDir(rootfsDir, proto.version).takeIf { it.exists() }?.absolutePath
            }

            val isToolInstalled: suspend (ToolItemProto) -> Boolean = { proto ->
                resolveInstalledPath(distro, proto.id, inRootfsSdkRoot) != null
            }
            val getToolPath: suspend (ToolItemProto) -> String? = { proto ->
                resolveInstalledPath(distro, proto.id, inRootfsSdkRoot)
            }

            val updatedBuildTools = if (config.cachedBuildToolsList.isNotEmpty()) {
                updateProtoInstalledStatus(config.cachedBuildToolsList, isToolInstalled, getToolPath)
            } else {
                val exists36 = File(sdkDirInRootfs, "build-tools/36.0.0").isDirectory
                listOf(
                    ToolItemProto.newBuilder()
                        .setId("build-tools;36.0.0")
                        .setVersion("36.0.0")
                        .setDescription("Android SDK Build-Tools 36.0.0")
                        .setIsInstalled(exists36)
                        .setPath(if (exists36) File(sdkDirInRootfs, "build-tools/36.0.0").absolutePath else "")
                        .build()
                )
            }

            val updatedPlatformTools = if (config.cachedPlatformToolsList.isNotEmpty()) {
                updateProtoInstalledStatus(config.cachedPlatformToolsList, isToolInstalled, getToolPath)
            } else {
                val platformToolsDir = File(sdkDirInRootfs, "platform-tools")
                val hasPlatformTools = platformToolsDir.isDirectory && platformToolsDir.listFiles()?.isNotEmpty() == true
                listOf(
                    ToolItemProto.newBuilder()
                        .setId("platform-tools")
                        .setVersion("16")
                        .setDescription("Android SDK Platform-Tools")
                        .setIsInstalled(hasPlatformTools)
                        .setPath(if (hasPlatformTools) platformToolsDir.absolutePath else "")
                        .build()
                )
            }

            val updatedNdkTools = if (config.cachedNdkToolsList.isNotEmpty()) {
                updateProtoInstalledStatus(config.cachedNdkToolsList, isToolInstalled, getToolPath)
            } else {
                val ndkDir = File(sdkDirInRootfs, "ndk")
                val exists30 = File(ndkDir, "30").isDirectory || File(ndkDir, "30.0.13298285").isDirectory
                val activePath = if (File(ndkDir, "30").isDirectory) File(ndkDir, "30").absolutePath
                else if (File(ndkDir, "30.0.13298285").isDirectory) File(ndkDir, "30.0.13298285").absolutePath else ""
                listOf(
                    ToolItemProto.newBuilder()
                        .setId("ndk;30")
                        .setVersion("30")
                        .setDescription("Android Native Development Kit (NDK) 30")
                        .setIsInstalled(exists30)
                        .setPath(activePath)
                        .build()
                )
            }

            val updatedCmakeTools = if (config.cachedCmakeToolsList.isNotEmpty()) {
                updateProtoInstalledStatus(config.cachedCmakeToolsList, isToolInstalled, getToolPath)
            } else {
                val cmakeDir = File(sdkDirInRootfs, "cmake")
                val existsCmake = File(cmakeDir, "4.1.2").isDirectory || File(cmakeDir, "3.22.1").isDirectory
                val activePath = if (File(cmakeDir, "4.1.2").isDirectory) File(cmakeDir, "4.1.2").absolutePath
                else if (File(cmakeDir, "3.22.1").isDirectory) File(cmakeDir, "3.22.1").absolutePath else ""
                listOf(
                    ToolItemProto.newBuilder()
                        .setId("cmake;4.1.2")
                        .setVersion("4.1.2")
                        .setDescription("CMake Compiler Tool 4.1.2")
                        .setIsInstalled(existsCmake)
                        .setPath(activePath)
                        .build()
                )
            }

            val updatedJavaTools = if (config.cachedJavaToolsList.isNotEmpty()) {
                updateProtoInstalledStatus(config.cachedJavaToolsList, isJdkInstalled, getJdkPath)
            } else {
                listOf(
                    ToolItemProto.newBuilder()
                        .setId("jdk;17")
                        .setVersion("17")
                        .setDescription("Java Development Kit 17 (OpenJDK)")
                        .setIsInstalled(javaInfo.isInstalled)
                        .setPath(javaInfo.path.orEmpty())
                        .build()
                )
            }

            val diagMsg = "SDK Tools synchronisiert: Build-Tools (${updatedBuildTools.count { it.isInstalled }}), Platform-Tools (${updatedPlatformTools.count { it.isInstalled }}), NDK (${updatedNdkTools.count { it.isInstalled }}), CMake (${updatedCmakeTools.count { it.isInstalled }}), Java (${updatedJavaTools.count { it.isInstalled }})."
            if (isLoggingEnabled) AppLogger.step("SdkManager", diagMsg)

            settingsRepository.saveSdkManagerCache(
                buildTools = updatedBuildTools,
                platformTools = updatedPlatformTools,
                ndkTools = updatedNdkTools,
                cmakeTools = updatedCmakeTools,
                javaTools = updatedJavaTools,
                diagnosticMessage = diagMsg
            )
        }
    }
}
