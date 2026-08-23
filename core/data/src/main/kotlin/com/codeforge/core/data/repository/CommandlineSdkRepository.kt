/**
 * Modul: :core:data
 * @author Thomas Schmid
 */
package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.SdkInstallEvent
import com.codeforge.core.domain.model.ToolItem
import com.codeforge.core.domain.repository.SdkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * TODO: "sdkmanager" muss auf das Binary innerhalb der PRoot-Rootfs zeigen (analog zu
 * den anderen ProcessBuilder-Aufrufen im Projekt, siehe ProotCommandBuilder in
 * :libs:terminal-engine), sobald die Commandline-Tools dort bereitgestellt werden.
 * Aktuell wird der Befehl direkt ausgeführt, wie es das Skill vorgibt.
 */
@Singleton
class CommandlineSdkRepository @Inject constructor() : SdkRepository {

    private val progressRegex = Regex("""\[(=*)\s*]\s+(\d+)%\s*(.*)""")
    private val packageRowRegex = Regex("""^([\w.\-;]+)\s*\|\s*([\w.\-]+)\s*\|""")

    private enum class ListSection { NONE, INSTALLED, AVAILABLE }

    override suspend fun listAvailablePackages(): Result<List<ToolItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("sdkmanager", "--list")
                .redirectErrorStream(true)
                .start()

            val items = mutableListOf<ToolItem>()
            var section = ListSection.NONE

            BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line.isEmpty() || line.startsWith("---") || line.startsWith("Path") -> Unit
                        line.startsWith("Installed packages", ignoreCase = true) -> section = ListSection.INSTALLED
                        line.startsWith("Available Packages", ignoreCase = true) -> section = ListSection.AVAILABLE
                        else -> parsePackageRow(line, section)?.let(items::add)
                    }
                }
            }

            process.waitFor()
            items
        }
    }

    private fun parsePackageRow(line: String, section: ListSection): ToolItem? {
        if (section == ListSection.NONE) return null
        val match = packageRowRegex.find(line) ?: return null
        val path = match.groupValues[1].trim()
        val version = match.groupValues[2].trim()
        val installed = section == ListSection.INSTALLED
        return ToolItem(
            id = path,
            version = version,
            isInstalled = installed,
            path = if (installed) resolveInstalledPath(path) else null
        )
    }

    /**
     * Sdkmanager installiert Pakete konventionsgemäß unter <sdkRoot>/<path mit ';' -> '/'>
     * (z.B. "build-tools;34.0.0" -> "<sdkRoot>/build-tools/34.0.0"). Wird nur als echter
     * Pfad zurückgegeben, wenn das Verzeichnis auch tatsächlich existiert — kein bloßes
     * String-Zusammensetzen ohne Verifikation.
     */
    private fun resolveInstalledPath(packagePath: String): String? {
        val root = sdkRootPath() ?: return null
        val dir = File(root, packagePath.replace(';', '/'))
        return dir.takeIf { it.isDirectory }?.absolutePath
    }

    override fun sdkRootPath(): String? =
        System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")

    override fun installSdkTool(packagePath: String): Flow<SdkInstallEvent> = flow {
        try {
            val process = ProcessBuilder("sdkmanager", packagePath)
                .redirectErrorStream(true)
                .start()

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val currentLine = line?.trim() ?: continue

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
                emit(SdkInstallEvent.Success(packagePath))
            } else {
                emit(SdkInstallEvent.Error(RuntimeException("Code $exitCode")))
            }
        } catch (e: Exception) {
            emit(SdkInstallEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun uninstallSdkTool(packagePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val process = ProcessBuilder("sdkmanager", "--uninstall", packagePath)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().forEachLine { /* Ausgabe wird verworfen, nur Exit-Code zählt */ }
            val exitCode = process.waitFor()
            if (exitCode != 0) error("sdkmanager --uninstall beendet mit Code $exitCode")
        }
    }
}
