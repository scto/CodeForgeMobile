/**
 * Modul: :libs:terminal-engine
 * Automated Android SDK Package & License Manager with Daemon Input Feeder
 * and Side-by-Side Batch Installation.
 */
package com.codeforge.libs.terminal_engine

import com.codeforge.core.common.logging.AppLogger
import java.io.File
import java.io.IOException
import kotlin.concurrent.thread

open class AndroidSdkInstaller(
    customAndroidHome: File? = null,
    private val prootRunner: ((command: List<String>, onOutputLine: (String) -> Unit) -> Int)? = null
) {
    val androidHome: File = customAndroidHome
        ?: System.getenv("ANDROID_HOME")?.let { File(it) }
        ?: System.getenv("ANDROID_SDK_ROOT")?.let { File(it) }
        ?: File(System.getProperty("user.home") ?: "/root", "android-sdk")

    private val isWindows = System.getProperty("os.name")?.lowercase()?.contains("win") == true
    private val sdkManagerBinaryName = if (isWindows) "sdkmanager.bat" else "sdkmanager"

    val sdkManagerFile: File = File(androidHome, "cmdline-tools/latest/bin/$sdkManagerBinaryName")

    fun isSdkManagerAvailable(): Boolean {
        return androidHome.exists() && (sdkManagerFile.exists() || prootRunner != null)
    }

    /**
     * Akzeptiert alle ausstehenden SDK-Lizenzen automatisch über den Daemon Input Feeder.
     */
    fun acceptAllLicenses(onOutputLine: ((String) -> Unit)? = null): Boolean {
        AppLogger.step("SdkManager", "==> Akzeptiere Android-SDK-Lizenzen via Daemon-Thread...")
        val cmd = listOf(
            sdkManagerFile.absolutePath,
            "--sdk_root=${androidHome.absolutePath}",
            "--licenses"
        )
        return runInteractiveProcess(cmd, onOutputLine)
    }

    /**
     * Installiert eine Liste von Paketen (z. B. mehrere NDKs und CMakes parallel / Side-by-Side).
     */
    fun installPackages(packages: List<String>, onOutputLine: ((String) -> Unit)? = null): Boolean {
        if (packages.isEmpty()) return true

        AppLogger.step("SdkManager", "==> Starte Batch-Installation von ${packages.size} Paketen...")
        val cmd = mutableListOf(
            sdkManagerFile.absolutePath,
            "--sdk_root=${androidHome.absolutePath}"
        ).apply { addAll(packages) }

        return runInteractiveProcess(cmd, onOutputLine)
    }

    /**
     * Gibt alle auf der Festplatte installierten Versionen einer Komponente zurueck.
     */
    fun getInstalledVersions(component: String): List<String> {
        val dir = File(androidHome, component)
        if (!dir.exists() || !dir.isDirectory) return emptyList()

        return dir.listFiles { file -> file.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    private fun runInteractiveProcess(
        command: List<String>,
        onOutputLine: ((String) -> Unit)? = null
    ): Boolean {
        if (prootRunner != null) {
            val exitCode = prootRunner.invoke(command) { line ->
                AppLogger.step("SdkManager", "  [CLI-OUT] $line")
                onOutputLine?.invoke(line)
            }
            return exitCode == 0
        }

        return try {
            val processBuilder = ProcessBuilder(command)
                .redirectErrorStream(true)

            val process = processBuilder.start()

            // Hintergrund-Thread (Daemon) simuliert den 'yes'-Befehl fuer Lizenzabfragen & Prompts
            val inputFeeder = thread(isDaemon = true) {
                try {
                    process.outputStream.bufferedWriter().use { writer ->
                        while (process.isAlive) {
                            writer.write("y\n")
                            writer.flush()
                            Thread.sleep(150)
                        }
                    }
                } catch (_: IOException) {
                    // Pipe geschlossen, Prozess beendet
                }
            }

            // Live-Ausgabe im Terminal streamen (verhindert Buffer-Stau)
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    AppLogger.step("SdkManager", "  [CLI-OUT] $line")
                    onOutputLine?.invoke(line)
                }
            }

            val exitCode = process.waitFor()
            inputFeeder.interrupt()
            exitCode == 0
        } catch (e: Exception) {
            AppLogger.e("SdkManager", "Fehler bei interaktivem SdkManager-Prozess", e)
            false
        }
    }
}

class AndroidPlatformManager(
    private val installer: AndroidSdkInstaller
) {
    fun installPlatforms(
        platformApiLevels: List<Int>,
        includeSources: Boolean = false,
        onOutputLine: ((String) -> Unit)? = null
    ): Boolean {
        val pkgs = mutableListOf<String>()
        for (api in platformApiLevels) {
            pkgs.add("platforms;android-$api")
            if (includeSources) {
                pkgs.add("sources;android-$api")
            }
        }
        return installer.installPackages(pkgs, onOutputLine)
    }

    fun getInstalledPlatforms(): List<String> {
        return installer.getInstalledVersions("platforms")
    }
}

class AndroidBuildToolsManager(
    private val installer: AndroidSdkInstaller
) {
    fun installBuildTools(
        versions: List<String>,
        onOutputLine: ((String) -> Unit)? = null
    ): Boolean {
        val pkgs = versions.map { "build-tools;$it" }
        return installer.installPackages(pkgs, onOutputLine)
    }

    fun getInstalledBuildTools(): List<String> {
        return installer.getInstalledVersions("build-tools")
    }
}
