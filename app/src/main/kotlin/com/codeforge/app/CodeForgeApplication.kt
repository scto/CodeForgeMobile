// Modul: :app
package com.codeforge.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.codeforge.core.common.logging.AppLogger
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.datastore.proto.LogLevelProto
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@JvmField var application: Application? = null

@HiltAndroidApp
class CodeForgeApplication :
    Application(),
    Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var sdkRepository: com.codeforge.core.domain.repository.SdkRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()

        application = this

        // Initial setup
        AppLogger.isEnabled = true
        AppLogger.currentLevel = AppLogger.LogLevel.INFO
        AppLogger.step("App", "Application onCreate called")
        AppLogger.setupCrashHandler()

        // NATIVE BIBLIOTHEKEN LADEN (Tree-Sitter)
        // Lädt die in den jniLibs liegenden .so Dateien (z.B. libandroid-tree-sitter.so)
        runCatching {
            System.loadLibrary("android-tree-sitter")
            System.loadLibrary("tree-sitter-c")
            System.loadLibrary("tree-sitter-cpp")
            System.loadLibrary("tree-sitter-java")
            System.loadLibrary("tree-sitter-json")
            System.loadLibrary("tree-sitter-kotlin")
            System.loadLibrary("tree-sitter-python")
            System.loadLibrary("tree-sitter-xml")
            AppLogger.i("App", "Tree-Sitter native libraries loaded successfully.")
        }.onFailure { e ->
            AppLogger.e("App", "Failed to load Tree-Sitter native libraries. Check jniLibs folder.", e)
        }

        applicationScope.launch(Dispatchers.IO) {
            runCatching {
                sdkRepository.syncInstalledToolsToDataStore()
            }
        }

        applicationScope.launch {
            settingsRepository.appSettings.collectLatest { settings ->
                AppLogger.isEnabled = settings.debug.loggingEnabled
                AppLogger.excessiveTracingEnabled = settings.debug.excessiveTracingEnabled
                AppLogger.workspaceDirectory =
                    settings.workspaceDirectory.takeIf { it.isNotBlank() } ?: "/storage/emulated/0/CodeForgeMobileProjects"
                AppLogger.currentLevel =
                    when (settings.debug.logLevel) {
                        LogLevelProto.LOG_LEVEL_VERBOSE -> AppLogger.LogLevel.VERBOSE
                        LogLevelProto.LOG_LEVEL_DEBUG -> AppLogger.LogLevel.DEBUG
                        LogLevelProto.LOG_LEVEL_WARN -> AppLogger.LogLevel.WARN
                        LogLevelProto.LOG_LEVEL_ERROR -> AppLogger.LogLevel.ERROR
                        else -> AppLogger.LogLevel.INFO
                    }
                AppLogger.d("App", "AppLogger updated from settings")
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(workerFactory)
                .build()
}