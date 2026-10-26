package com.codeforge.core.common.crash

import com.codeforge.core.common.log.CfmLogger
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CfmCrashHandler {

    private val fileDateFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    private val headerDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Schreibt den Crash-Report synchron in das Zielverzeichnis.
     * Wirft niemals eine Exception.
     */
    fun writeCrashLog(
        primaryCrashDir: File?,
        fallbackCrashDir: File?,
        thread: Thread,
        throwable: Throwable,
        appInfo: String,
        osInfo: String,
        deviceInfo: String,
        now: Long = System.currentTimeMillis()
    ): File? {
        val fileName = "crash-${fileDateFormat.format(Date(now))}.txt"
        val headerDate = headerDateFormat.format(Date(now))

        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString().trimEnd()

        val content = buildString {
            append("=== Crash at ").append(headerDate).append(" in thread ").append(thread.name).append(" ===\n")
            append("App: ").append(appInfo).append("\n")
            append("OS: ").append(osInfo).append("\n")
            append("Device: ").append(deviceInfo).append("\n\n")
            append(stackTrace).append("\n")
        }

        val targetDirs = listOfNotNull(primaryCrashDir, fallbackCrashDir)
        for (dir in targetDirs) {
            try {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val file = File(dir, fileName)
                file.writeText(content)
                pruneOldCrashLogs(dir, CfmLogger.MAX_CRASH_LOGS)
                return file
            } catch (_: Throwable) {
                // Bei Scheitern nächstes Verzeichnis probieren
            }
        }
        return null
    }

    /**
     * Liefert alle Crash-Logs sortiert nach Änderungsdatum absteigend (neueste zuerst).
     */
    fun listCrashLogs(crashDir: File): List<File> {
        if (!crashDir.exists() || !crashDir.isDirectory) return emptyList()
        return crashDir.listFiles { file ->
            file.isFile && file.name.startsWith("crash-") && file.name.endsWith(".txt")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Behält maximal [maxCount] neueste Crash-Logs und löscht ältere.
     */
    fun pruneOldCrashLogs(crashDir: File, maxCount: Int = CfmLogger.MAX_CRASH_LOGS) {
        try {
            val logs = listCrashLogs(crashDir)
            if (logs.size > maxCount) {
                logs.drop(maxCount).forEach { it.delete() }
            }
        } catch (_: Throwable) {
            // Ignorieren
        }
    }
}
