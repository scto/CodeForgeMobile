// Modul: :core:data
/**
 * @author Thomas Schmid
 */
package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.SdkInstallEvent
import com.codeforge.core.domain.model.ToolItem
import com.codeforge.core.domain.model.ToolType
import com.codeforge.core.domain.repository.SdkRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandlineSdkRepository @Inject constructor() : SdkRepository {

    private val progressRegex = Regex("""\[(=*)\s*\]\s+(\d+)%\s*(.*)""")

    override fun getAvailableTools(): Flow<List<ToolItem>> = flow {
        val dummyList = listOf(
            ToolItem("jdk-17", "OpenJDK 17", "17.0.9", ToolType.JDK, isInstalled = true, path = "/usr/lib/jvm/java-17"),
            ToolItem("jdk-21", "OpenJDK 21", "21.0.1", ToolType.JDK, isInstalled = false),
            ToolItem("platforms;android-35", "Android SDK Platform 35", "35.0.0", ToolType.PLATFORM, isInstalled = true, path = "/sdcard/Android/sdk/platforms/android-35"),
            ToolItem("build-tools;35.0.0", "Android SDK Build-Tools 35.0.0", "35.0.0", ToolType.BUILD_TOOLS, isInstalled = true),
            ToolItem("ndk;26.1.10909125", "NDK (Side by side) 26.1", "26.1.10909125", ToolType.NDK, isInstalled = false),
            ToolItem("cmake;3.22.1", "CMake 3.22.1", "3.22.1", ToolType.CMAKE, isInstalled = false)
        )
        emit(dummyList)
    }.flowOn(Dispatchers.IO)

    override fun getInstalledTools(): Flow<List<ToolItem>> = flow {
        val dummyList = listOf(
            ToolItem("jdk-17", "OpenJDK 17", "17.0.9", ToolType.JDK, isInstalled = true, path = "/usr/lib/jvm/java-17"),
            ToolItem("platforms;android-35", "Android SDK Platform 35", "35.0.0", ToolType.PLATFORM, isInstalled = true, path = "/sdcard/Android/sdk/platforms/android-35"),
            ToolItem("build-tools;35.0.0", "Android SDK Build-Tools 35.0.0", "35.0.0", ToolType.BUILD_TOOLS, isInstalled = true)
        )
        emit(dummyList)
    }.flowOn(Dispatchers.IO)

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
                emit(SdkInstallEvent.Error(RuntimeException("sdkmanager exited with code $exitCode")))
            }
        } catch (e: Exception) {
            emit(SdkInstallEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)

    override fun uninstallSdkTool(packagePath: String): Flow<SdkInstallEvent> = flow {
        try {
            val process = ProcessBuilder("sdkmanager", "--uninstall", packagePath)
                .redirectErrorStream(true)
                .start()

            val exitCode = process.waitFor()
            if (exitCode == 0) {
                emit(SdkInstallEvent.Success(packagePath))
            } else {
                emit(SdkInstallEvent.Error(RuntimeException("Uninstall failed with code $exitCode")))
            }
        } catch (e: Exception) {
            emit(SdkInstallEvent.Error(e))
        }
    }.flowOn(Dispatchers.IO)
}
