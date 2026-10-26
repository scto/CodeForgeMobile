package com.codeforge.core.common.log

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class FileCfmLogger(
    private val logDirectoryProvider: () -> File?,
    private val mirrorToAndroidLog: Boolean = true,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : CfmLogger {

    private data class LogEvent(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String,
        val throwable: Throwable?
    )

    private val channel = Channel<LogEvent>(
        capacity = 2000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var lastPruneTimestamp = 0L

    init {
        scope.launch {
            for (event in channel) {
                writeEventSync(event)
            }
        }
    }

    override fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message, null)
    override fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message, null)
    override fun w(tag: String, message: String, throwable: Throwable?) = log(LogLevel.WARN, tag, message, throwable)
    override fun e(tag: String, message: String, throwable: Throwable?) = log(LogLevel.ERROR, tag, message, throwable)

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (mirrorToAndroidLog) {
            try {
                when (level) {
                    LogLevel.DEBUG -> android.util.Log.d(tag, message, throwable)
                    LogLevel.INFO -> android.util.Log.i(tag, message, throwable)
                    LogLevel.WARN -> android.util.Log.w(tag, message, throwable)
                    LogLevel.ERROR -> android.util.Log.e(tag, message, throwable)
                }
            } catch (_: Throwable) {
                // Bei JVM Unit-Tests ohne Robolectric kann android.util.Log fehlen
            }
        }

        channel.trySend(
            LogEvent(
                timestamp = System.currentTimeMillis(),
                level = level,
                tag = tag,
                message = sanitizeMessage(message),
                throwable = throwable
            )
        )
    }

    private fun writeEventSync(event: LogEvent) {
        val dir = logDirectoryProvider() ?: return
        try {
            if (!dir.exists()) {
                dir.mkdirs()
            }
            pruneOldLogsIfNeeded(dir, event.timestamp)

            val dateStr = dateFormat.format(Date(event.timestamp))
            val logFile = resolveActiveLogFile(dir, dateStr)

            val timeStr = timeFormat.format(Date(event.timestamp))
            val line = buildString {
                append(timeStr)
                append(" [")
                append(event.level.name.padEnd(5))
                append("] [")
                append(event.tag)
                append("] ")
                append(event.message)
                if (event.throwable != null) {
                    append("\n")
                    val sw = StringWriter()
                    event.throwable.printStackTrace(PrintWriter(sw))
                    append(sw.toString().trimEnd())
                }
                append("\n")
            }

            FileWriter(logFile, true).use { writer ->
                writer.write(line)
            }
        } catch (_: Throwable) {
            // Logger darf niemals abstürzen
        }
    }

    private fun resolveActiveLogFile(dir: File, dateStr: String): File {
        var baseName = "cfm-$dateStr.log"
        var file = File(dir, baseName)
        var counter = 1
        while (file.exists() && file.length() >= CfmLogger.MAX_LOG_FILE_SIZE_BYTES) {
            baseName = "cfm-$dateStr.$counter.log"
            file = File(dir, baseName)
            counter++
        }
        return file
    }

    private fun pruneOldLogsIfNeeded(dir: File, now: Long) {
        // Prune maximal 1x pro Stunde
        if (now - lastPruneTimestamp < TimeUnit.HOURS.toMillis(1)) return
        lastPruneTimestamp = now

        val cutoff = now - TimeUnit.DAYS.toMillis(CfmLogger.RETENTION_DAYS.toLong())
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("cfm-") && file.name.endsWith(".log")) {
                if (file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }

    private fun sanitizeMessage(msg: String): String {
        // Filtere potenzielle sensible Tokens / Passwörter aus Log-Zeilen
        return msg.replace(Regex("(?i)(password|secret|token|api_?key|auth)[\\s:=]+[^\\s]+"), "$1=***")
    }
}
