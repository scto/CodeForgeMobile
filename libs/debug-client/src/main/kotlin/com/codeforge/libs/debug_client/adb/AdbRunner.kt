/**
 * Modul: :libs:debug-client
 * @author Thomas Schmid
 */
package com.codeforge.libs.debug_client.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class AdbResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

interface AdbRunner {
    fun isAvailable(): Boolean
    fun getAdbPath(): String?
    suspend fun execute(vararg args: String): AdbResult
}

@Singleton
class DefaultAdbRunner @Inject constructor() : AdbRunner {

    private val adbBinaryPath: String? by lazy {
        findAdbBinary()
    }

    override fun isAvailable(): Boolean = adbBinaryPath != null

    override fun getAdbPath(): String? = adbBinaryPath

    override suspend fun execute(vararg args: String): AdbResult = withContext(Dispatchers.IO) {
        val binary = adbBinaryPath ?: "adb"
        val command = listOf(binary) + args.toList()

        try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(false)
                .start()

            val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

            val stdout = stdoutReader.readText()
            val stderr = stderrReader.readText()

            val exitCode = process.waitFor()
            AdbResult(exitCode = exitCode, stdout = stdout, stderr = stderr)
        } catch (e: Exception) {
            AdbResult(exitCode = -1, stdout = "", stderr = e.message ?: "ADB-Befehlsausführung fehlgeschlagen")
        }
    }

    private fun findAdbBinary(): String? {
        val candidates = listOf(
            "/data/data/com.termux/files/home/android-sdk/platform-tools/adb",
            "/data/data/com.termux/files/usr/bin/adb",
            "adb"
        )
        for (candidate in candidates) {
            val file = File(candidate)
            if (file.exists() && file.canExecute()) {
                return file.absolutePath
            }
        }
        // Prüfe ob adb im System-PATH ausführbar ist
        return try {
            val p = ProcessBuilder("which", "adb").start()
            val path = p.inputStream.bufferedReader().readLine()?.trim()
            if (p.waitFor() == 0 && !path.isNullOrEmpty() && File(path).canExecute()) {
                path
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
