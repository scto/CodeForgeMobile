// Modul: :core:datastore
package com.codeforge.core.datastore

import androidx.datastore.core.DataStore
import com.codeforge.core.common.logging.AppLogger
import com.codeforge.core.datastore.proto.AppSettings
import com.codeforge.core.datastore.proto.DebugConfig
import com.codeforge.core.datastore.proto.EditorConfig
import com.codeforge.core.datastore.proto.FileTreeConfig
import com.codeforge.core.datastore.proto.SdkManagerConfig
import com.codeforge.core.datastore.proto.TerminalConfig
import com.codeforge.core.datastore.proto.ThemeConfig
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zentrale Read/Write-Schnittstelle auf das AppSettings-Proto.
 * Wird von :feature:themebuilder, :feature:settings und :feature:onboarding konsumiert.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<AppSettings>
) {
    val appSettings: Flow<AppSettings> = dataStore.data

    suspend fun updateTheme(transform: (ThemeConfig) -> ThemeConfig) {
        AppLogger.step("SettingsRepository", "updateTheme called")
        dataStore.updateData { current ->
            current.toBuilder()
                .setTheme(transform(current.theme))
                .build()
        }
    }

    suspend fun updateEditor(transform: (EditorConfig) -> EditorConfig) {
        AppLogger.step("SettingsRepository", "updateEditor called")
        dataStore.updateData { current ->
            current.toBuilder()
                .setEditor(transform(current.editor))
                .build()
        }
    }

    suspend fun updateFileTree(transform: (FileTreeConfig) -> FileTreeConfig) {
        AppLogger.step("SettingsRepository", "updateFileTree called")
        dataStore.updateData { current ->
            current.toBuilder()
                .setFileTree(transform(current.fileTree))
                .build()
        }
    }


    suspend fun updateTerminal(transform: (TerminalConfig) -> TerminalConfig) {
        AppLogger.step("SettingsRepository", "updateTerminal called")
        dataStore.updateData { current ->
            current.toBuilder()
                .setTerminal(transform(current.terminal))
                .build()
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        AppLogger.step("SettingsRepository", "setOnboardingCompleted called with $completed")
        dataStore.updateData { current ->
            current.toBuilder()
                .setOnboardingCompleted(completed)
                .build()
        }
    }

    suspend fun updateTerminalDistro(distro: String) {
        AppLogger.step("SettingsRepository", "updateTerminalDistro called with $distro")
        updateTerminal { it.toBuilder().setDefaultDistro(distro).build() }
    }

    suspend fun setTerminalInstalled(installed: Boolean) {
        AppLogger.step("SettingsRepository", "setTerminalInstalled called with $installed")
        updateTerminal { it.toBuilder().setTerminalInstalled(installed).build() }
    }

    suspend fun setWorkspaceDirectory(dir: String) {
        AppLogger.step("SettingsRepository", "setWorkspaceDirectory called with $dir")
        dataStore.updateData { current ->
            current.toBuilder()
                .setWorkspaceDirectory(dir)
                .build()
        }
    }

    suspend fun updateDebug(transform: (DebugConfig) -> DebugConfig) {
        AppLogger.step("SettingsRepository", "updateDebug called")
        dataStore.updateData { current ->
            current.toBuilder()
                .setDebug(transform(current.debug))
                .build()
        }
    }

    suspend fun updateSdkManagerConfig(transform: (SdkManagerConfig) -> SdkManagerConfig) {
        AppLogger.step("SettingsRepository", "updateSdkManagerConfig called")
        dataStore.updateData { current ->
            current.toBuilder()
                .setSdkManager(transform(current.sdkManager))
                .build()
        }
    }

    suspend fun setSdkUpdateInterval(interval: com.codeforge.core.datastore.proto.UpdateIntervalProto) {
        AppLogger.step("SettingsRepository", "setSdkUpdateInterval called with $interval")
        updateSdkManagerConfig { current ->
            current.toBuilder()
                .setUpdateInterval(interval)
                .build()
        }
    }

    suspend fun setCmdlineToolsInstalled(installed: Boolean) {
        AppLogger.step("SettingsRepository", "setCmdlineToolsInstalled called with $installed")
        updateSdkManagerConfig { current ->
            current.toBuilder()
                .setCmdlineToolsInstalled(installed)
                .build()
        }
    }

    suspend fun setSdkDiagnosticMessage(message: String) {
        AppLogger.step("SettingsRepository", "setSdkDiagnosticMessage called: $message")
        updateSdkManagerConfig { current ->
            current.toBuilder()
                .setLastDiagnosticMessage(message)
                .build()
        }
    }

    suspend fun setJavaInstalled(installed: Boolean, version: String = "", path: String = "") {
        AppLogger.step("SettingsRepository", "setJavaInstalled called: installed=$installed, version=$version, path=$path")
        updateSdkManagerConfig { current ->
            current.toBuilder()
                .setJavaInstalled(installed)
                .setJavaVersion(version)
                .setJavaPath(path)
                .build()
        }
    }

    suspend fun saveSdkManagerCache(
        buildTools: List<com.codeforge.core.datastore.proto.ToolItemProto>,
        platformTools: List<com.codeforge.core.datastore.proto.ToolItemProto>,
        ndkTools: List<com.codeforge.core.datastore.proto.ToolItemProto>,
        cmakeTools: List<com.codeforge.core.datastore.proto.ToolItemProto>,
        javaTools: List<com.codeforge.core.datastore.proto.ToolItemProto>,
        diagnosticMessage: String = ""
    ) {
        AppLogger.step("SettingsRepository", "saveSdkManagerCache called")
        updateSdkManagerConfig { current ->
            current.toBuilder()
                .setFirstRunCompleted(true)
                .setCmdlineToolsInstalled(true)
                .setLastUpdateTimestamp(System.currentTimeMillis())
                .setLastDiagnosticMessage(diagnosticMessage)
                .clearCachedBuildTools()
                .addAllCachedBuildTools(buildTools)
                .clearCachedPlatformTools()
                .addAllCachedPlatformTools(platformTools)
                .clearCachedNdkTools()
                .addAllCachedNdkTools(ndkTools)
                .clearCachedCmakeTools()
                .addAllCachedCmakeTools(cmakeTools)
                .clearCachedJavaTools()
                .addAllCachedJavaTools(javaTools)
                .build()
        }
    }
}
