// Modul: :app
package com.codeforge.app

import android.app.Application
import android.os.Build
import android.os.Environment
import android.util.Log
import com.codeforge.core.common.crash.CfmCrashHandler
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import dagger.hilt.android.HiltAndroidApp
import java.io.File

@HiltAndroidApp
class CodeForgeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        installCrashHandler()
        TermuxApplication.initTermuxApplication(this)
        com.codeforge.feature.composepreview.PreviewActivityHolder.register(this)
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
