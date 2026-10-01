package com.codeforge.core.common.logging

import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * LogCatcher / AppLogger: Zentrale Protokollierungsinstanz für alle Module von CodeForge Mobile.
 * Schreibt Protokolle in Logcat sowie in die Datei "codeforge.log" im Zielverzeichnis (CodeForgeMobileProjects).
 */
object AppLogger {
    enum class LogLevel { VERBOSE, DEBUG, INFO, WARN, ERROR }

    var isEnabled: Boolean = true
    var excessiveTracingEnabled: Boolean = false
    var workspaceDirectory: String = "/storage/emulated/0/CodeForgeMobileProjects"
    var currentLevel: LogLevel = LogLevel.INFO

    private val logExecutor = Executors.newSingleThreadExecutor()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private fun getLogFile(): File? {
        return try {
            val baseDir = File(workspaceDirectory)
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
            File(baseDir, "codeforge.log")
        } catch (e: Exception) {
            null
        }
    }

    private fun writeToFile(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        if (!isEnabled) return

        logExecutor.execute {
            try {
                val file = getLogFile() ?: return@execute
                val timestamp = dateFormat.format(Date())
                val logLine = StringBuilder()
                    .append("[$timestamp] [${level.name}] [$tag] ")
                    .append(message)
                    .append("\n")

                if (throwable != null) {
                    val sw = StringWriter()
                    throwable.printStackTrace(PrintWriter(sw))
                    logLine.append(sw.toString()).append("\n")
                }

                FileWriter(file, true).use { writer ->
                    writer.write(logLine.toString())
                }
            } catch (e: Exception) {
                Log.e("AppLogger", "Failed to write to codeforge.log", e)
            }
        }
    }

    fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("CrashHandler", "Uncaught exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun step(tag: String, message: String) {
        if (isEnabled) {
            Log.i(tag, "[STEP] $message")
            writeToFile(LogLevel.INFO, tag, "[STEP] $message")
        }
    }

    fun d(tag: String, message: String) {
        if (isEnabled && currentLevel.ordinal <= LogLevel.DEBUG.ordinal) {
            Log.d(tag, message)
            writeToFile(LogLevel.DEBUG, tag, message)
        }
    }

    fun i(tag: String, message: String) {
        if (isEnabled && currentLevel.ordinal <= LogLevel.INFO.ordinal) {
            Log.i(tag, message)
            writeToFile(LogLevel.INFO, tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (isEnabled && currentLevel.ordinal <= LogLevel.WARN.ordinal) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
            writeToFile(LogLevel.WARN, tag, message, throwable)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (isEnabled) {
            if (throwable != null) {
                Log.e(tag, message, throwable)
            } else {
                Log.e(tag, message)
            }
            writeToFile(LogLevel.ERROR, tag, message, throwable)
        }
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        e(tag, message, throwable)
    }
}

typealias LogCatcher = AppLogger