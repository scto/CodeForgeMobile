/**
 * Modul: :libs:mcp
 * @author Thomas Schmid
 */
package com.codeforge.libs.mcp

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * MCP-Client (Model Context Protocol) über einen beliebigen [McpTransport]: Handshake, Werkzeugliste und Werkzeugaufrufe.
 *
 * Bewusst nur die Teilmenge, die ein Agent braucht: Der Client meldet keine Fähigkeiten (kein Sampling, keine Roots, keine
 * Elicitation); ein Server, der sie trotzdem anfragt, bekommt „Methode unbekannt“, ein `ping` seine leere Antwort.
 */
class McpClient(
    private val transport: McpTransport,
    private val clientName: String = "CodeForgeMobile",
    private val clientVersion: String = "1.0",
) : AutoCloseable {

    private val ids = AtomicLong()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var closedBy: Throwable? = null

    /** `true`, sobald der Client geschlossen ist oder der Transport weggebrochen ist. */
    @Volatile
    var closed: Boolean = false
        private set

    /** Name und Version des Servers laut `initialize`. */
    var serverName: String? = null
        private set

    /** Version des Servers laut `initialize`. */
    var serverVersion: String? = null
        private set

    /** Anweisungen des Servers zur Benutzung (aus seiner `initialize`-Antwort). */
    var instructions: String? = null
        private set

    /** Ausgehandelte Protokollversion. */
    var protocolVersion: String? = null
        private set

    /** Wird gerufen, wenn der Server meldet, dass sich seine Werkzeugliste geändert hat. */
    @Volatile
    var onToolsChanged: (() -> Unit)? = null

    /** Wird einmal gerufen, wenn der Transport unter dem Client wegbricht. */
    @Volatile
    var onDisconnected: ((Throwable?) -> Unit)? = null

    /** Öffnet den Transport und führt den `initialize`-Handshake aus. */
    suspend fun connect(timeoutMs: Long = CONNECT_TIMEOUT_MS) {
        transport.start(::onMessage, ::onTransportClosed)
        val result = request(
            "initialize",
            buildJsonObject {
                put("protocolVersion", LATEST_PROTOCOL_VERSION)
                putJsonObject("capabilities") {}
                putJsonObject("clientInfo") { put("name", clientName); put("version", clientVersion) }
            },
            timeoutMs,
        )
        protocolVersion = result.str("protocolVersion") ?: LATEST_PROTOCOL_VERSION
        (result["serverInfo"] as? JsonObject)?.let { serverName = it.str("name"); serverVersion = it.str("version") }
        instructions = result.str("instructions")?.takeIf { it.isNotBlank() }
        transport.negotiated(protocolVersion!!)
        notify("notifications/initialized")
    }

    /** Alle Werkzeuge des Servers, seitenweise gelesen (begrenzt, damit ein kreisender Cursor nicht hängt). */
    suspend fun listTools(timeoutMs: Long = CONNECT_TIMEOUT_MS): List<McpToolInfo> {
        val out = ArrayList<McpToolInfo>()
        var cursor: String? = null
        repeat(MAX_PAGES) {
            val result = request("tools/list", cursor?.let { c -> buildJsonObject { put("cursor", c) } }, timeoutMs)
            (result["tools"] as? JsonArray)?.forEach { el ->
                val t = el as? JsonObject ?: return@forEach
                val name = t.str("name") ?: return@forEach
                val annotations = t["annotations"] as? JsonObject
                out += McpToolInfo(
                    name = name,
                    title = t.str("title") ?: annotations?.str("title"),
                    description = t.str("description"),
                    inputSchema = t["inputSchema"] as? JsonObject ?: JsonObject(emptyMap()),
                    readOnly = annotations?.bool("readOnlyHint"),
                    destructive = annotations?.bool("destructiveHint"),
                )
            }
            cursor = result.str("nextCursor")?.takeIf { it.isNotEmpty() }
            if (cursor == null) return out
        }
        return out
    }

    /** Ruft das Werkzeug [name] mit [arguments] auf. */
    suspend fun callTool(name: String, arguments: JsonObject, timeoutMs: Long = CALL_TIMEOUT_MS): McpCallResult =
        flatten(request("tools/call", buildJsonObject { put("name", name); put("arguments", arguments) }, timeoutMs))

    /** Sendet eine Anfrage und wartet auf die Antwort; ein JSON-RPC-Fehler wird zur [McpException]. */
    suspend fun request(method: String, params: JsonObject?, timeoutMs: Long): JsonObject {
        closedBy?.let { throw IOException("Die MCP-Verbindung ist geschlossen: ${it.message}", it) }
        if (closed) throw IOException("Die MCP-Verbindung ist geschlossen.")
        val id = ids.incrementAndGet().toString()
        val answer = CompletableDeferred<JsonObject>()
        pending[id] = answer
        val message = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id.toLong())
            put("method", method)
            params?.let { put("params", it) }
        }
        // Parallel senden: Bei Streamable HTTP liest send() den Antwortstrom, die Antwort kommt aber schon vorher an.
        val sender = scope.launch {
            try {
                transport.send(message)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                answer.completeExceptionally(e)
            }
        }
        try {
            val response = try {
                withTimeout(timeoutMs) { answer.await() }
            } catch (e: TimeoutCancellationException) {
                throw McpTimeoutException(method, timeoutMs)
            }
            (response["error"] as? JsonObject)?.let { err ->
                throw McpException(err.str("message") ?: "MCP-Fehler", (err["code"] as? JsonPrimitive)?.intOrNull)
            }
            return response["result"] as? JsonObject ?: JsonObject(emptyMap())
        } finally {
            pending.remove(id)
            sender.cancel()
        }
    }

    /** Sendet eine Benachrichtigung (ohne Antwort). */
    suspend fun notify(method: String, params: JsonObject? = null) {
        transport.send(
            buildJsonObject {
                put("jsonrpc", "2.0")
                put("method", method)
                params?.let { put("params", it) }
            },
        )
    }

    private fun onMessage(message: JsonObject) {
        val method = message.str("method")
        val id = message["id"]?.takeIf { it !is JsonNull }
        when {
            method == null && id != null -> {
                val key = (id as? JsonPrimitive)?.contentOrNull ?: return
                pending[key]?.complete(message)
            }
            method != null && id != null -> scope.launch { answerServerRequest(id, method) }
            method == "notifications/tools/list_changed" -> onToolsChanged?.invoke()
        }
    }

    private suspend fun answerServerRequest(id: kotlinx.serialization.json.JsonElement, method: String) {
        val reply = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            if (method == "ping") {
                putJsonObject("result") {}
            } else {
                putJsonObject("error") { put("code", METHOD_NOT_FOUND); put("message", "Methode nicht unterstützt: $method") }
            }
        }
        runCatching { transport.send(reply) }
    }

    private fun onTransportClosed(cause: Throwable?) {
        if (closed) return
        closed = true
        closedBy = cause ?: IOException("Verbindung beendet")
        val failure = IOException("Die MCP-Verbindung wurde beendet: ${cause?.message ?: "Server hat sie geschlossen"}", cause)
        pending.values.forEach { it.completeExceptionally(failure) }
        pending.clear()
        onDisconnected?.invoke(cause)
    }

    override fun close() {
        closed = true
        runCatching { transport.close() }
        scope.cancel()
        pending.values.forEach { it.cancel() }
        pending.clear()
    }

    /** Konstanten und Hilfen zur Antwortverarbeitung. */
    companion object {
        /** Zeitlimit für Handshake und Werkzeugliste. */
        const val CONNECT_TIMEOUT_MS = 30_000L

        /** Zeitlimit für einen Werkzeugaufruf. */
        const val CALL_TIMEOUT_MS = 120_000L
        private const val MAX_PAGES = 20
        private const val METHOD_NOT_FOUND = -32601

        /** Flacht das Ergebnis von `tools/call` ab: Textblöcke werden verbunden, Bilder nur gezählt, Ressourcen als Text/URI. */
        fun flatten(result: JsonObject): McpCallResult {
            val texts = ArrayList<String>()
            var images = 0
            (result["content"] as? JsonArray)?.forEach { el ->
                val block = el as? JsonObject ?: return@forEach
                when (block.str("type")) {
                    "text" -> block.str("text")?.let { texts += it }
                    "image" -> images++
                    "resource" -> (block["resource"] as? JsonObject)?.let { r -> texts += r.str("text") ?: r.str("uri").orEmpty() }
                    "resource_link" -> texts += block.str("uri").orEmpty()
                    else -> Unit
                }
            }
            if (texts.isEmpty()) (result["structuredContent"] as? JsonObject)?.let { texts += it.toString() }
            return McpCallResult(texts.joinToString("\n"), images, result.bool("isError") == true)
        }

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
    }
}

private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
