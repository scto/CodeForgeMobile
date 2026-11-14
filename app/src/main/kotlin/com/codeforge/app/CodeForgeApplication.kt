// Modul: :app
package com.codeforge.app

import android.app.Application
import android.os.Build
import android.os.Environment
import android.util.Log
import com.codeforge.core.common.crash.CfmCrashHandler
import com.codeforge.core.common.log.CfmLogManager
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Anwendungsklasse: initialisiert Hilt, Protokollierung, Absturzbehandlung und Hintergrunddienste. */
@HiltAndroidApp
class CodeForgeApplication : Application() {

    @Inject lateinit var logManager: CfmLogManager
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var releaseMemory: dagger.Lazy<com.codeforge.core.domain.runtime.ReleaseMemoryUseCase>

    /** Zeitpunkt der letzten automatischen Speicherfreigabe (gegen Dauerfeuer bei anhaltendem Druck). */
    @Volatile private var lastTrimMillis = 0L

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private companion object {
        /** Mindestabstand zwischen zwei automatischen Speicherfreigaben. */
        const val TRIM_MIN_INTERVAL_MILLIS = 60_000L
    }

    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        observeLogSetting()
        TermuxApplication.initTermuxApplication(this)
        com.codeforge.app.keepalive.IdeKeepAliveController.start(this)
        com.codeforge.feature.composepreview.PreviewActivityHolder.register(this)
    }

    /**
     * Speicherdruck vom System: Hilfsprozesse und Caches abgeben, statt dass Android die App beendet.
     * `TRIM_MEMORY_UI_HIDDEN` (App nur im Hintergrund) löst nichts aus – Builds laufen dort weiter.
     */
    @Suppress("DEPRECATION") // RUNNING_*-Stufen liefert Android 14+ nicht mehr, ältere Geräte schon.
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val pressure = when {
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE ->
                com.codeforge.core.domain.runtime.MemoryPressure.CRITICAL
            level == android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ->
                com.codeforge.core.domain.runtime.MemoryPressure.LOW
            else -> return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastTrimMillis < TRIM_MIN_INTERVAL_MILLIS) return
        lastTrimMillis = now
        appScope.launch {
            val released = runCatching { releaseMemory.get()(pressure) }.getOrDefault(emptyMap())
            if (released.isNotEmpty()) Log.i("CFMemory", "onTrimMemory($level) → $pressure: $released")
        }
    }

    /** Debug-Einstellungen: Protokollierung nach `.cfm/log` an/aus (Standard: an). */
    private fun observeLogSetting() {
        appScope.launch {
            settingsRepository.appSettings
                .map { !it.debug.logDisabled }
                .distinctUntilChanged()
                .collect { logManager.setEnabled(it) }
        }
        appScope.launch {
            settingsRepository.appSettings
                .map { it.debug.logMaxFiles to it.debug.logCompress }
                .distinctUntilChanged()
                .collect { (maxFiles, compress) -> logManager.configure(maxFiles, compress) }
        }
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stackTrace = throwable.stackTraceToString()
                Log.e("CodeForgeCrash", "FATAL CRASH in thread ${thread.name}: $stackTrace")

                val appInfo = "${BuildConfig.APPLICATION_ID} ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                val osInfo = "Android API ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})"
                val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})"

                val primaryDir = File(Environment.getExternalStorageDirectory(), "${ProjectsDirectoryProvider.PROJECTS_DIR_NAME}/.cfm/crash")
                val fallbackDir = getExternalFilesDir("crash") ?: File(filesDir, "crash")

                CfmCrashHandler.writeCrashLog(
                    primaryCrashDir = primaryDir,
                    fallbackCrashDir = fallbackDir,
                    thread = thread,
                    throwable = throwable,
                    appInfo = appInfo,
                    osInfo = osInfo,
                    deviceInfo = deviceInfo
                )
            } catch (_: Throwable) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
