package com.codeforge.core.common.log

import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Schreibt Log-Dateien nach `<Projekte>/.cfm/log/` (Fallback: app-eigenes Verzeichnis).
 *
 * - Eigene Aufrufe über [CfmLogger] (`d/i/w/e/log`).
 * - Optional ([captureLogcat]) der komplette Logcat des eigenen Prozesses (`logcat --pid`), damit auch
 *   `Log.w/e` aus Bibliotheken und der App im Log landen. Für den eigenen Prozess ist dafür keine
 *   READ_LOGS-Berechtigung nötig.
 * - Dateien: `cfm-yyyyMMdd.log`, Rotation ab [CfmLogger.MAX_LOG_FILE_SIZE_BYTES], Aufbewahrung
 *   [CfmLogger.RETENTION_DAYS] Tage.
 *
 * Schreibt nur, solange [setEnabled] `true` ist. Wirft nie.
 */
class CfmLogManager(
    private val primaryLogDir: File?,
    private val fallbackLogDir: File?,
    private val pidProvider: () -> Int = { android.os.Process.myPid() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val captureLogcat: Boolean = true
) : CfmLogger {

    private val enabled = AtomicBoolean(false)
    private val lock = Any()
    private val fileDate = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val lineDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logcatProcess: Process? = null

    val isEnabled: Boolean get() = enabled.get()

    fun setEnabled(value: Boolean) {
        val was = enabled.getAndSet(value)
        if (value && !was) {
            prune()
            append("=== Protokollierung gestartet ===")
            if (captureLogcat) startLogcatCapture()
        } else if (!value && was) {
            append("=== Protokollierung beendet ===")
            stopLogcatCapture()
        }
    }

    /** Verzeichnis, in das aktuell geschrieben wird (Primär, sonst Fallback), oder `null`. */
    fun activeLogDir(): File? = listOfNotNull(primaryLogDir, fallbackLogDir).firstOrNull { dir ->
        runCatching { dir.isDirectory || dir.mkdirs() }.getOrDefault(false) && dir.canWrite()
    }

    fun listLogFiles(): List<File> {
        val dirs = listOfNotNull(primaryLogDir, fallbackLogDir).distinct()
        return dirs.flatMap { d ->
            d.listFiles { f -> f.isFile && f.name.startsWith("cfm-") && f.name.contains(".log") }?.toList().orEmpty()
        }.sortedByDescending { it.lastModified() }
    }

    fun totalSizeBytes(): Long = listLogFiles().sumOf { it.length() }

    /** Löscht alle Log-Dateien (Crash-Logs bleiben unberührt). */
    fun clearLogs(): Int {
        var n = 0
        synchronized(lock) { listLogFiles().forEach { if (it.delete()) n++ } }
        return n
    }

    override fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    override fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    override fun w(tag: String, message: String, throwable: Throwable?) = log(LogLevel.WARN, tag, message, throwable)
    override fun e(tag: String, message: String, throwable: Throwable?) = log(LogLevel.ERROR, tag, message, throwable)

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (!enabled.get()) return
        val sb = StringBuilder()
            .append(lineDate.format(Date(clock()))).append(' ')
            .append(level.name.first()).append('/').append(tag).append(": ").append(message)
        if (throwable != null) sb.append('\n').append(throwable.stackTraceToString().trimEnd())
        append(sb.toString())
    }

    internal fun append(line: String) {
        try {
            synchronized(lock) {
                val dir = activeLogDir() ?: return
                val file = File(dir, "cfm-${fileDate.format(Date(clock()))}.log")
                if (file.exists() && file.length() >= CfmLogger.MAX_LOG_FILE_SIZE_BYTES) {
                    val rotated = File(dir, file.name + ".1")
                    if (rotated.exists()) rotated.delete()
                    file.renameTo(rotated)
                }
                file.appendText(line + "\n")
            }
        } catch (_: IOException) {
        } catch (_: SecurityException) {
        }
    }

    internal fun prune() {
        val limit = clock() - CfmLogger.RETENTION_DAYS * 24L * 60 * 60 * 1000
        runCatching { listLogFiles().filter { it.lastModified() < limit }.forEach { it.delete() } }
    }

    private fun startLogcatCapture() {
        if (logcatProcess != null) return
        val thread = Thread({
            try {
                val process = ProcessBuilder("logcat", "-v", "threadtime", "--pid=${pidProvider()}")
                    .redirectErrorStream(true)
                    .start()
                logcatProcess = process
                process.inputStream.bufferedReader().useLines { lines ->
                    for (line in lines) {
                        if (!enabled.get()) break
                        append(line)
                    }
                }
            } catch (_: Throwable) {
            } finally {
                logcatProcess?.destroy()
                logcatProcess = null
            }
        }, "cfm-logcat")
        thread.isDaemon = true
        thread.start()
    }

    private fun stopLogcatCapture() {
        logcatProcess?.destroy()
        logcatProcess = null
    }
}
