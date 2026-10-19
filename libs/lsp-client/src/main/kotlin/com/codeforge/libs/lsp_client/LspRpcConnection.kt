// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Kapselt die rohe JSON-RPC-2.0-Kommunikation mit einem Language-Server-Prozess
 * gemäß LSP-Spec: jede Nachricht besitzt einen "Content-Length: N"-Header, eine
 * Leerzeile, dann N Bytes UTF-8-kodiertes JSON.
 */
class LspRpcConnection(
    private val output: OutputStream,
    private val input: InputStream
) {

    constructor(process: Process) : this(
        output = BufferedOutputStream(process.outputStream),
        input = BufferedInputStream(process.inputStream)
    )

    private val requestIdCounter = AtomicInteger(0)
    private val pendingRequests = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()

    private var notificationHandler: ((method: String, params: JsonObject?) -> Unit)? = null
    private var requestHandler: (suspend (method: String, id: JsonElement, params: JsonObject?) -> JsonElement?)? = null
    private var connectionScope: CoroutineScope? = null
    private var readerJob: Job? = null

    fun start(
        scope: CoroutineScope,
        onNotification: (method: String, params: JsonObject?) -> Unit,
        onRequest: (suspend (method: String, id: JsonElement, params: JsonObject?) -> JsonElement?)? = null
    ) {
        connectionScope = scope
        notificationHandler = onNotification
        requestHandler = onRequest
        readerJob = scope.launch(Dispatchers.IO) { readLoop() }
    }

    private suspend fun readLoop() {
        while (readerJob?.isActive == true) {
            val message = readMessage(input) ?: break
            dispatch(message)
        }
    }

    private fun dispatch(message: JsonObject) {
        val idElement = message["id"]
        val method = (message["method"] as? JsonPrimitive)?.content

        if (method != null) {
            if (idElement != null) {
                // Server->Client Request (z.B. workspace/applyEdit)
                val handler = requestHandler
                connectionScope?.launch(Dispatchers.IO) {
                    try {
                        val result = handler?.invoke(method, idElement, message["params"] as? JsonObject)
                        val response = buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", idElement)
                            put("result", result ?: buildJsonObject { put("applied", false) })
                        }
                        writeMessage(output, response)
                    } catch (e: Exception) {
                        val errorResponse = buildJsonObject {
                            put("jsonrpc", "2.0")
                            put("id", idElement)
                            put("error", buildJsonObject {
                                put("code", -32603)
                                put("message", e.message ?: "Internal error")
                            })
                        }
                        writeMessage(output, errorResponse)
                    }
                }
            } else {
                // Notification (z.B. textDocument/publishDiagnostics)
                notificationHandler?.invoke(method, message["params"] as? JsonObject)
            }
            return
        }

        val id = idElement?.jsonPrimitive?.intOrNull ?: return
        pendingRequests.remove(id)?.complete(message)
    }

    suspend fun sendRequest(method: String, params: JsonObject): JsonObject {
        val id = requestIdCounter.incrementAndGet()
        val deferred = CompletableDeferred<JsonObject>()
        pendingRequests[id] = deferred

        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        writeMessage(output, payload)

        return deferred.await()
    }

    fun sendNotification(method: String, params: JsonObject) {
        val payload = buildJsonObject {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
        writeMessage(output, payload)
    }

    fun close() {
        readerJob?.cancel()
        pendingRequests.values.forEach { it.cancel() }
        pendingRequests.clear()
        runCatching { output.close() }
        runCatching { input.close() }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun readMessage(input: InputStream): JsonObject? {
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readHeaderLine(input) ?: return null
                if (line.isEmpty()) break
                val separatorIndex = line.indexOf(':')
                if (separatorIndex > 0) {
                    headers[line.substring(0, separatorIndex).trim()] = line.substring(separatorIndex + 1).trim()
                }
            }

            val contentLength = headers["Content-Length"]?.toIntOrNull() ?: return null
            val bodyBytes = ByteArray(contentLength)
            var readTotal = 0
            while (readTotal < contentLength) {
                val readCount = input.read(bodyBytes, readTotal, contentLength - readTotal)
                if (readCount == -1) return null
                readTotal += readCount
            }

            val bodyText = String(bodyBytes, Charsets.UTF_8)
            return runCatching { json.parseToJsonElement(bodyText).jsonObject }.getOrNull()
        }

        private fun readHeaderLine(input: InputStream): String? {
            val builder = StringBuilder()
            while (true) {
                val byte = input.read()
                if (byte == -1) return if (builder.isEmpty()) null else builder.toString()
                if (byte == '\r'.code) continue
                if (byte == '\n'.code) return builder.toString()
                builder.append(byte.toChar())
            }
        }

        fun writeMessage(output: OutputStream, payload: JsonObject) {
            val bodyText = json.encodeToString(JsonObject.serializer(), payload)
            val bodyBytes = bodyText.toByteArray(Charsets.UTF_8)
            val header = "Content-Length: ${bodyBytes.size}\r\n\r\n".toByteArray(Charsets.UTF_8)
            synchronized(output) {
                output.write(header)
                output.write(bodyBytes)
                output.flush()
            }
        }
    }
}
