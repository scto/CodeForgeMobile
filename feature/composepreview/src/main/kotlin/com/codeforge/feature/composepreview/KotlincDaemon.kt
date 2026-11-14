/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview

import com.codeforge.libs.terminal_engine.TermuxEnvironment
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/** Ergebnis eines Daemon-Laufs. */
internal data class DaemonRun(val exitCode: Int, val output: String)

/**
 * Hält einen JVM-Prozess mit geladenem Kotlin-Compiler warm (spart JVM-Start und Klassenladen, ~Faktor 3–5 ab dem
 * zweiten Lauf). Wird nach [idleTimeoutMillis] ohne Anfrage oder nach [maxCompilesPerProcess] Läufen beendet
 * (Speicher). Jeder Fehler führt zu `null`; der Aufrufer fällt dann auf den einmaligen `kotlinc`-Prozess zurück.
 */
internal class KotlincDaemon(
    private val idleTimeoutMillis: Long = 120_000L,
    private val maxCompilesPerProcess: Int = 40,
    private val maxHeap: String = "768m",
) {
    private val mutex = Mutex()
    private var process: Process? = null
    private var writer: Writer? = null
    private var reader: BufferedReader? = null
    private var compiles = 0
    private var lastUse = 0L
    private var idleWatcher: Thread? = null

    /** Startet den Daemon im Voraus (ohne Anfrage); `false`, wenn nicht möglich. */
    suspend fun prewarm(kotlincLibDir: File): Boolean = mutex.withLock {
        runCatching { ensureStarted(kotlincLibDir) != null }.getOrDefault(false)
    }

    /** Wandelt Klassen mit D8 (`d8.jar` der Build-Tools) im warmen Prozess in DEX um. */
    suspend fun dex(kotlincLibDir: File, d8Jar: File, args: List<String>, timeoutMillis: Long): DaemonRun? =
        run(kotlincLibDir, listOf("D8", d8Jar.absolutePath) + args, timeoutMillis)

    /** Führt `kotlinc`-Argumente (ohne Binärpfad) aus, oder liefert `null`, wenn der Daemon nicht nutzbar ist. */
    suspend fun run(kotlincLibDir: File, args: List<String>, timeoutMillis: Long): DaemonRun? = mutex.withLock {
        try {
            val p = ensureStarted(kotlincLibDir) ?: return@withLock null
            val w = writer ?: return@withLock null
            val r = reader ?: return@withLock null
            val result = withTimeoutOrNull(timeoutMillis) {
                runInterruptible(Dispatchers.IO) {
                    w.write(args.joinToString("\u0001") + "\n")
                    w.flush()
                    val out = StringBuilder()
                    while (true) {
                        val line = r.readLine() ?: return@runInterruptible null
                        if (line.startsWith(EXIT_MARK)) {
                            return@runInterruptible DaemonRun(line.removePrefix(EXIT_MARK).trim().toIntOrNull() ?: 2, out.toString())
                        }
                        out.append(line).append('\n')
                    }
                    @Suppress("UNREACHABLE_CODE") null
                }
            }
            if (result == null) { stop(); return@withLock null }
            compiles++
            lastUse = System.currentTimeMillis()
            // OOM oder Interner Fehler: Prozess neu starten statt weiterverwenden.
            if (result.exitCode >= 2 || compiles >= maxCompilesPerProcess || !p.isAlive) stop()
            result
        } catch (e: kotlinx.coroutines.CancellationException) {
            stop() // laufende Kompilierung abgebrochen: Zustand unbekannt
            throw e
        } catch (_: Exception) {
            stop()
            null
        }
    }

    /**
     * Beendet den Daemon nur, wenn gerade nichts kompiliert wird. Liefert `true`, wenn ein laufender Daemon beendet wurde.
     */
    fun stopIfIdle(): Boolean {
        if (!mutex.tryLock()) return false
        return try {
            val wasRunning = process != null
            stop()
            wasRunning
        } finally {
            mutex.unlock()
        }
    }

    /** Beendet den Daemon (idempotent). */
    fun stop() {
        runCatching { writer?.close() }
        runCatching { process?.destroyForcibly() }
        process = null; writer = null; reader = null; compiles = 0
    }

    private fun ensureStarted(libDir: File): Process? {
        process?.takeIf { it.isAlive }?.let { return it }
        stop()
        val java = resolveJava() ?: return null
        val sourceDir = File(System.getProperty("java.io.tmpdir") ?: return null, "cf-kotlinc-daemon").apply { mkdirs() }
        val source = File(sourceDir, "KotlincDaemon.java")
        if (!source.isFile || source.readText() != KOTLINC_DAEMON_SOURCE) source.writeText(KOTLINC_DAEMON_SOURCE)

        val env = TermuxEnvironment.environment()
        val command = listOf(
            java, "-Xmx$maxHeap", "-XX:+UseSerialGC", "-XX:-UsePerfData", "-Xshare:auto", "-Djansi.passthrough=true",
            source.absolutePath, libDir.absolutePath,
        )
        val p = ProcessBuilder(command).redirectError(File("/dev/null")).also {
            it.environment().clear(); it.environment().putAll(env)
        }.start()
        val r = BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8))
        val ready = runCatching { r.readLine() }.getOrNull()
        if (ready?.trim() != READY_MARK) { p.destroyForcibly(); return null }
        process = p; reader = r; writer = OutputStreamWriter(p.outputStream, Charsets.UTF_8)
        lastUse = System.currentTimeMillis()
        startIdleWatcher()
        return p
    }

    private fun startIdleWatcher() {
        idleWatcher?.interrupt()
        idleWatcher = Thread({
            try {
                while (true) {
                    Thread.sleep(15_000L)
                    if (process == null) return@Thread
                    if (System.currentTimeMillis() - lastUse > idleTimeoutMillis && mutex.tryLock()) {
                        try { stop() } finally { mutex.unlock() }
                        return@Thread
                    }
                }
            } catch (_: InterruptedException) { }
        }, "cf-kotlinc-daemon-idle").apply { isDaemon = true; start() }
    }

    private fun resolveJava(): String? {
        val home = TermuxEnvironment.environment()["JAVA_HOME"]
        return home?.let { File(it, "bin/java") }?.takeIf { it.canExecute() }?.absolutePath
    }

    companion object {
        /** Gemeinsame Instanz für Compiler und Dexer (ein warmer Prozess). */
        val shared = KotlincDaemon()
        private const val READY_MARK = "\u0002READY"
        private const val EXIT_MARK = "\u0002EXIT:"
    }
}
