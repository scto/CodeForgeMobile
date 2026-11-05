/**
 * Modul: :libs:mcp
 * @author Thomas Schmid
 */
package com.codeforge.libs.mcp

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

private val wireJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** Liest einen SSE-Strom Zeile für Zeile und ruft [onEvent] mit (Ereignisname, Daten) auf. */
private fun readSse(reader: BufferedReader, onEvent: (String?, String) -> Unit) {
    var event: String? = null
    val data = StringBuilder()
    while (true) {
        val line = reader.readLine() ?: break
        when {
            line.isEmpty() -> {
                if (data.isNotEmpty()) onEvent(event, data.toString())
                event = null
                data.setLength(0)
            }
            line.startsWith(":") -> Unit
            line.startsWith("event:") -> event = line.removePrefix("event:").trim()
            line.startsWith("data:") -> {
                if (data.isNotEmpty()) data.append('\n')
                data.append(line.removePrefix("data:").removePrefix(" "))
            }
        }
    }
    if (data.isNotEmpty()) onEvent(event, data.toString())
}

private fun deliver(text: String, onMessage: (JsonObject) -> Unit) {
    val el = runCatching { wireJson.parseToJsonElement(text) }.getOrNull() ?: return
    when (el) {
        is JsonObject -> onMessage(el)
        is JsonArray -> el.forEach { (it as? JsonObject)?.let(onMessage) }
        else -> Unit
    }
}

private fun HttpURLConnection.applyHeaders(headers: Map<String, String>) = headers.forEach { (k, v) -> setRequestProperty(k, v) }

/**
 * Streamable HTTP: jede Nachricht ist ein POST; die Antwort ist JSON oder ein SSE-Strom und läuft über die Anfrage zurück.
 * Die Sitzungs-ID aus `Mcp-Session-Id` und die Protokollversion gehen auf jede weitere Anfrage mit.
 */
class StreamableHttpTransport(
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    private val readTimeoutMs: Int = 300_000,
) : McpTransport {
    @Volatile private var sessionId: String? = null
    @Volatile private var protocol: String? = null
    @Volatile private var closed = false
    private lateinit var onMessage: (JsonObject) -> Unit
    private val active: MutableSet<HttpURLConnection> = Collections.synchronizedSet(HashSet())

    override suspend fun start(onMessage: (JsonObject) -> Unit, onClosed: (Throwable?) -> Unit) {
        this.onMessage = onMessage
    }

    override fun negotiated(protocolVersion: String) {
        protocol = protocolVersion
    }

    override suspend fun send(message: JsonObject) = withContext(Dispatchers.IO) {
        check(!closed) { "Transport geschlossen" }
        val conn = URL(url).openConnection() as HttpURLConnection
        active += conn
        try {
            conn.apply {
                connectTimeout = 15_000
                readTimeout = readTimeoutMs
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json, text/event-stream")
                sessionId?.let { setRequestProperty("Mcp-Session-Id", it) }
                protocol?.let { setRequestProperty("MCP-Protocol-Version", it) }
                applyHeaders(headers)
            }
            conn.outputStream.use { it.write(message.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.getHeaderField("Mcp-Session-Id")?.let { sessionId = it }
            when {
                code == 401 -> throw McpAuthRequiredException(conn.getHeaderField("WWW-Authenticate"))
                code == 404 && sessionId != null -> throw McpSessionExpiredException()
                code !in 200..299 -> {
                    val body = runCatching { conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty() }.getOrDefault("")
                    throw McpHttpException(code, "HTTP $code${if (body.isNotBlank()) ": ${body.take(300)}" else ""}")
                }
                code == 202 || code == 204 -> Unit
                else -> {
                    val type = conn.contentType.orEmpty().lowercase()
                    BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                        if (type.startsWith("text/event-stream")) {
                            readSse(reader) { _, data -> deliver(data, onMessage) }
                        } else {
                            deliver(reader.readText(), onMessage)
                        }
                    }
                }
            }
        } finally {
            active -= conn
            conn.disconnect()
        }
    }

    override fun close() {
        closed = true
        synchronized(active) { active.toList() }.forEach { runCatching { it.disconnect() } }
        // Sitzung beenden (Best Effort).
        val id = sessionId ?: return
        Thread {
            runCatching {
                (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 3_000
                    readTimeout = 3_000
                    setRequestProperty("Mcp-Session-Id", id)
                    applyHeaders(headers)
                    responseCode
                    disconnect()
                }
            }
        }.start()
    }
}

/**
 * Älteres HTTP+SSE: Ein GET öffnet den Ereignisstrom; das erste Ereignis `endpoint` nennt die URL, an die per POST
 * gesendet wird. Antworten kommen als `message`-Ereignisse über den Strom.
 */
class LegacySseTransport(
    private val url: String,
    private val headers: Map<String, String> = emptyMap(),
    private val endpointTimeoutMs: Long = 15_000,
) : McpTransport {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val endpoint = CompletableDeferred<String>()
    @Volatile private var stream: HttpURLConnection? = null
    @Volatile private var closed = false

    override suspend fun start(onMessage: (JsonObject) -> Unit, onClosed: (Throwable?) -> Unit) {
        scope.launch {
            var failure: Throwable? = null
            try {
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 0
                    setRequestProperty("Accept", "text/event-stream")
                    applyHeaders(headers)
                }
                stream = conn
                val code = conn.responseCode
                if (code == 401) throw McpAuthRequiredException(conn.getHeaderField("WWW-Authenticate"))
                if (code !in 200..299) throw McpHttpException(code, "HTTP $code")
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    readSse(reader) { event, data ->
                        if (event == "endpoint") {
                            endpoint.complete(URI(url).resolve(data.trim()).toString())
                        } else {
                            deliver(data, onMessage)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                failure = e
                endpoint.completeExceptionally(e)
            } finally {
                if (!closed) onClosed(failure)
            }
        }
        try {
            withTimeout(endpointTimeoutMs) { endpoint.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw IOException("Der SSE-Server nannte keinen Endpunkt (event: endpoint).")
        }
    }

    override suspend fun send(message: JsonObject) = withContext(Dispatchers.IO) {
        val target = endpoint.await()
        val conn = URL(target).openConnection() as HttpURLConnection
        try {
            conn.apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                applyHeaders(headers)
            }
            conn.outputStream.use { it.write(message.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code == 401) throw McpAuthRequiredException(conn.getHeaderField("WWW-Authenticate"))
            if (code !in 200..299) throw McpHttpException(code, "HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    override fun close() {
        closed = true
        runCatching { stream?.disconnect() }
        scope.cancel()
    }
}

/**
 * Streamable HTTP mit Rückfall: Lehnt der Server das erste POST (`initialize`) mit 400/404/405 ab, spricht er vermutlich nur
 * das ältere HTTP+SSE – dann wird darauf umgeschaltet und die Nachricht erneut gesendet.
 */
class FallbackHttpTransport(private val url: String, private val headers: Map<String, String> = emptyMap()) : McpTransport {
    private var current: McpTransport = StreamableHttpTransport(url, headers)
    private var fellBack = false
    private var first = true
    private lateinit var onMessage: (JsonObject) -> Unit
    private lateinit var onClosed: (Throwable?) -> Unit
    private var protocol: String? = null

    override suspend fun start(onMessage: (JsonObject) -> Unit, onClosed: (Throwable?) -> Unit) {
        this.onMessage = onMessage
        this.onClosed = onClosed
        current.start(onMessage, onClosed)
    }

    override fun negotiated(protocolVersion: String) {
        protocol = protocolVersion
        current.negotiated(protocolVersion)
    }

    override suspend fun send(message: JsonObject) {
        val isFirst = first
        first = false
        try {
            current.send(message)
        } catch (e: McpHttpException) {
            if (!isFirst || fellBack || e.status !in setOf(400, 404, 405)) throw e
            fellBack = true
            runCatching { current.close() }
            current = LegacySseTransport(url, headers).also { it.start(onMessage, onClosed) }
            protocol?.let { current.negotiated(it) }
            current.send(message)
        }
    }

    override fun close() = current.close()
}
