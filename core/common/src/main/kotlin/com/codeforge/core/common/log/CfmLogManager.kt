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
 * - Dateien: `cfm-yyyyMMdd-HHmmss.log` (eine je Sitzung, neue Datei ab [CfmLogger.MAX_LOG_FILE_SIZE_BYTES]),
 *   Aufbewahrung [CfmLogger.RETENTION_DAYS] Tage und höchstens die eingestellte Anzahl ([configure]).
 * - Optional werden abgeschlossene Dateien als `.log.zip` abgelegt.
 *
 * Schreibt nur, solange [setEnabled] `true` ist. Wirft nie.
 */
class CfmLogManager(
    private val primaryLogDir: File?,
    private val fallbackLogDir: File?,
    private val pidProvider: () -> Int = { android.os.Process.myPid() },
    private val clock: () -> Long = System::currentTimeMillis,
    private val captureLogcat: Boolean = true,
    /** Führt Aufräumarbeiten (Zippen, Löschen) aus; Standard: eigener Daemon-Thread, in Tests direkt. */
    private val background: (() -> Unit) -> Unit = { work -> Thread(work, "cfm-log-housekeeping").apply { isDaemon = true }.start() }
) : CfmLogger {

    private val enabled = AtomicBoolean(false)
    private val lock = Any()
    private val fileDate = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    private val lineDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var logcatProcess: Process? = null

    @Volatile
    private var maxFiles = CfmLogger.DEFAULT_MAX_LOG_FILES

    @Volatile
    private var compressClosed = false

    /** Datei der laufenden Sitzung (unter [lock]). */
    private var activeFile: File? = null

    /** Höchstzahl der Log-Dateien. */
    val maxLogFiles: Int get() = maxFiles

    /** Ob abgeschlossene Dateien gezippt werden. */
    val compressesClosedFiles: Boolean get() = compressClosed

    /**
     * Setzt Höchstzahl ([maxFiles] ≤ 0 = Standard) und Zippen abgeschlossener Dateien und räumt sofort auf.
     */
    fun configure(maxFiles: Int, compressClosedFiles: Boolean) {
        this.maxFiles = (if (maxFiles <= 0) CfmLogger.DEFAULT_MAX_LOG_FILES else maxFiles).coerceIn(1, CfmLogger.MAX_LOG_FILES_LIMIT)
        compressClosed = compressClosedFiles
        background { housekeeping() }
    }

    /** Ob die Protokollierung aktiv ist. */
    val isEnabled: Boolean get() = enabled.get()

    /** Schaltet die Protokollierung ein oder aus. */
    fun setEnabled(value: Boolean) {
        val was = enabled.getAndSet(value)
        if (value && !was) {
            synchronized(lock) { activeFile = null } // neue Sitzung = neue Datei mit Datum und Uhrzeit
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

    /** Alle Protokolldateien (`cfm-*.log`). */
    fun listLogFiles(): List<File> {
        val dirs = listOfNotNull(primaryLogDir, fallbackLogDir).distinct()
        return dirs.flatMap { d ->
            d.listFiles { f -> f.isFile && f.name.startsWith("cfm-") && f.name.contains(".log") }?.toList().orEmpty()
        }.sortedByDescending { it.lastModified() }
    }

    /** Gesamtgröße aller Protokolldateien in Bytes. */
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

    /** Hängt eine Zeile an die Sitzungsdatei an; ab der Maximalgröße beginnt eine neue Datei. */
    internal fun append(line: String) {
        try {
            var rotated = false
            synchronized(lock) {
                val dir = activeLogDir() ?: return
                var file = activeFile?.takeIf { it.parentFile == dir && it.exists() }
                if (file == null || file.length() >= CfmLogger.MAX_LOG_FILE_SIZE_BYTES) {
                    file = newLogFile(dir)
                    activeFile = file
                    rotated = true
                }
                file.appendText(line + "\n")
            }
            if (rotated) background { housekeeping() }
        } catch (_: IOException) {
        } catch (_: SecurityException) {
        }
    }

    /** `cfm-<Datum>-<Uhrzeit>.log`; bei Namensgleichheit (gleiche Sekunde) mit laufender Nummer. */
    private fun newLogFile(dir: File): File {
        val stamp = fileDate.format(Date(clock()))
        var file = File(dir, "cfm-$stamp.log")
        var n = 2
        while (file.exists() || File(dir, file.name + ".zip").exists()) {
            file = File(dir, "cfm-$stamp-$n.log")
            n++
        }
        return file
    }

    /** Zippt abgeschlossene Dateien (falls eingeschaltet) und löscht nach Alter und Höchstzahl. */
    internal fun housekeeping() {
        if (compressClosed) compressClosedFiles()
        prune()
    }

    /** Legt jede abgeschlossene `.log`-Datei als `.log.zip` ab (Änderungszeit bleibt erhalten). */
    internal fun compressClosedFiles() {
        val active = synchronized(lock) { activeFile }
        listLogFiles().filter { it.name.endsWith(".log") && it != active }.forEach { log ->
            runCatching {
                val zip = File(log.parentFile, log.name + ".zip")
                java.util.zip.ZipOutputStream(zip.outputStream().buffered()).use { out ->
                    out.putNextEntry(java.util.zip.ZipEntry(log.name).apply { time = log.lastModified() })
                    log.inputStream().use { it.copyTo(out) }
                    out.closeEntry()
                }
                zip.setLastModified(log.lastModified())
                log.delete()
            }
        }
    }

    /** Löscht Protokolle, die älter als die Aufbewahrungsfrist sind oder über der Höchstzahl liegen (älteste zuerst). */
    internal fun prune() {
        val limit = clock() - CfmLogger.RETENTION_DAYS * 24L * 60 * 60 * 1000
        runCatching {
            val active = synchronized(lock) { activeFile }
            listLogFiles().filter { it.lastModified() < limit && it != active }.forEach { it.delete() }
            // listLogFiles() ist neueste-zuerst sortiert; die laufende Datei bleibt immer.
            listLogFiles().filter { it != active }.drop((maxFiles - if (active != null) 1 else 0).coerceAtLeast(0)).forEach { it.delete() }
        }
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
