// Modul: :app
package com.codeforge.app

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import java.util.Date

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
                val targets = listOfNotNull(
                    File("/sdcard/Download/codeforge-crash.log"),
                    File("/sdcard/Documents/codeforge-crash.log"),
                    getExternalFilesDir(null)?.let { File(it, "crash.log") },
                    File(filesDir, "crash.log")
                )
                for (target in targets) {
                    try {
                        target.parentFile?.mkdirs()
                        target.writeText("=== Crash at ${Date()} in thread ${thread.name} ===\n$stackTrace\n")
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
