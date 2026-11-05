/**
 * Modul: :libs:mcp
 * @author Thomas Schmid
 */
package com.codeforge.libs.mcp

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedDeque
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Startet den Prozess eines stdio-Servers (in der App in der Termux-Umgebung, damit `npx`/`uvx` gefunden werden). */
fun interface McpProcessLauncher {
    /** Baut und startet den Prozess für [command] mit [args] und zusätzlicher Umgebung [env]. */
    fun launch(command: String, args: List<String>, env: Map<String, String>): Process
}

/** Lokaler Server über stdin/stdout: eine JSON-Nachricht je Zeile. Ausgaben auf stderr werden für Fehlermeldungen aufgehoben. */
class StdioTransport(
    private val command: String,
    private val args: List<String>,
    private val env: Map<String, String>,
    private val launcher: McpProcessLauncher,
) : McpTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stderrTail = ConcurrentLinkedDeque<String>()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    @Volatile private var process: Process? = null
    @Volatile private var closed = false

    /** Die letzten Zeilen von stderr (für Fehlerdiagnose). */
    fun stderr(): String = stderrTail.joinToString("\n")

    override suspend fun start(onMessage: (JsonObject) -> Unit, onClosed: (Throwable?) -> Unit) {
        val p = withContext(Dispatchers.IO) { launcher.launch(command, args, env) }
        process = p
        scope.launch {
            BufferedReader(InputStreamReader(p.errorStream, Charsets.UTF_8)).useLines { lines ->
                lines.forEach { stderrTail.addLast(it); while (stderrTail.size > STDERR_LINES) stderrTail.pollFirst() }
            }
        }
        scope.launch {
            var failure: Throwable? = null
            try {
                BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { line ->
                        val trimmed = line.trim()
                        if (trimmed.startsWith("{")) {
                            runCatching { json.parseToJsonElement(trimmed) as? JsonObject }.getOrNull()?.let(onMessage)
                        }
                    }
                }
                if (!closed) {
                    val tail = stderr().takeLast(400)
                    failure = IOException("Der Server-Prozess wurde beendet" + if (tail.isNotBlank()) ": $tail" else "")
                }
            } catch (e: IOException) {
                failure = e
            } finally {
                if (!closed) onClosed(failure)
            }
        }
    }

    override suspend fun send(message: JsonObject) = withContext(Dispatchers.IO) {
        val out = process?.outputStream ?: throw IOException("Der Server-Prozess läuft nicht")
        synchronized(this@StdioTransport) {
            out.write((message.toString() + "\n").toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    override fun close() {
        closed = true
        process?.let { p ->
            runCatching { p.outputStream.close() }
            p.destroy()
        }
        scope.cancel()
    }

    private companion object {
        const val STDERR_LINES = 40
    }
}
