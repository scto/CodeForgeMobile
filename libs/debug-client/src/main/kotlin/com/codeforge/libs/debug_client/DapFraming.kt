package com.codeforge.libs.debug_client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStream
import java.io.OutputStream

/**
 * Handhabt die DAP-Rahmenkodierung (Header: Content-Length: <n>\r\n\r\n) und
 * die Konvertierung von/zu DapMessage.
 */
object DapFraming {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Liest die nächste vollständige DAP-Nachricht aus dem InputStream.
     * Blockiert, bis eine Nachricht vollständig vorliegt, oder gibt null bei EOF / Stream-Ende zurück.
     */
    fun readMessage(input: InputStream): DapMessage? {
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readHeaderLine(input) ?: return null
            if (line.isEmpty()) break
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val key = line.substring(0, colonIndex).trim()
                val value = line.substring(colonIndex + 1).trim()
                headers[key.lowercase()] = value
            }
        }

        val contentLength = headers["content-length"]?.toIntOrNull() ?: return null
        if (contentLength < 0) return null

        val bodyBytes = ByteArray(contentLength)
        var totalRead = 0
        while (totalRead < contentLength) {
            val count = input.read(bodyBytes, totalRead, contentLength - totalRead)
            if (count == -1) return null
            totalRead += count
        }

        val bodyText = String(bodyBytes, Charsets.UTF_8)
        val jsonElement = runCatching { json.parseToJsonElement(bodyText).jsonObject }.getOrNull() ?: return null
        return parseDapMessage(jsonElement)
    }

    /**
     * Liest eine einzelne Kopfzeile bis \r?\n.
     */
    private fun readHeaderLine(input: InputStream): String? {
        val buffer = StringBuilder()
        while (true) {
            val byte = input.read()
            if (byte == -1) {
                return if (buffer.isEmpty()) null else buffer.toString()
            }
            if (byte == '\r'.code) continue
            if (byte == '\n'.code) return buffer.toString()
            buffer.append(byte.toChar())
        }
    }

    /**
     * Wandelt ein JSON-Objekt in ein konkretes DapMessage-Objekt um.
     */
    fun parseDapMessage(obj: JsonObject): DapMessage? {
        val seq = obj["seq"]?.jsonPrimitive?.intOrNull ?: 0
        val type = obj["type"]?.jsonPrimitive?.content ?: return null

        return when (type) {
            "request" -> {
                val command = obj["command"]?.jsonPrimitive?.content ?: return null
                val args = obj["arguments"] as? JsonObject
                DapRequest(seq = seq, command = command, arguments = args)
            }
            "response" -> {
                val requestSeq = obj["request_seq"]?.jsonPrimitive?.intOrNull ?: 0
                val success = obj["success"]?.jsonPrimitive?.booleanOrNull ?: false
                val command = obj["command"]?.jsonPrimitive?.content ?: ""
                val message = obj["message"]?.jsonPrimitive?.content
                val body = obj["body"] as? JsonObject
                DapResponse(
                    seq = seq,
                    requestSeq = requestSeq,
                    success = success,
                    command = command,
                    message = message,
                    body = body
                )
            }
            "event" -> {
                val event = obj["event"]?.jsonPrimitive?.content ?: return null
                val body = obj["body"] as? JsonObject
                DapEvent(seq = seq, event = event, body = body)
            }
            else -> null
        }
    }

    /**
     * Schreibt eine DapRequest mit Content-Length-Header in den Ausgabestrom.
     */
    fun writeRequest(output: OutputStream, request: DapRequest) {
        val payload = buildString {
            append("{\"seq\":").append(request.seq)
            append(",\"type\":\"request\"")
            append(",\"command\":\"").append(escapeJson(request.command)).append("\"")
            if (request.arguments != null) {
                append(",\"arguments\":").append(request.arguments.toString())
            }
            append("}")
        }
        writePayload(output, payload)
    }

    /**
     * Schreibt eine DapResponse mit Content-Length-Header in den Ausgabestrom.
     */
    fun writeResponse(output: OutputStream, response: DapResponse) {
        val payload = buildString {
            append("{\"seq\":").append(response.seq)
            append(",\"type\":\"response\"")
            append(",\"request_seq\":").append(response.requestSeq)
            append(",\"success\":").append(response.success)
            append(",\"command\":\"").append(escapeJson(response.command)).append("\"")
            if (response.message != null) {
                append(",\"message\":\"").append(escapeJson(response.message)).append("\"")
            }
            if (response.body != null) {
                append(",\"body\":").append(response.body.toString())
            }
            append("}")
        }
        writePayload(output, payload)
    }

    /**
     * Schreibt ein DapEvent mit Content-Length-Header in den Ausgabestrom.
     */
    fun writeEvent(output: OutputStream, event: DapEvent) {
        val payload = buildString {
            append("{\"seq\":").append(event.seq)
            append(",\"type\":\"event\"")
            append(",\"event\":\"").append(escapeJson(event.event)).append("\"")
            if (event.body != null) {
                append(",\"body\":").append(event.body.toString())
            }
            append("}")
        }
        writePayload(output, payload)
    }

    private fun writePayload(output: OutputStream, payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        val header = "Content-Length: ${bytes.size}\r\n\r\n".toByteArray(Charsets.UTF_8)
        synchronized(output) {
            output.write(header)
            output.write(bytes)
            output.flush()
        }
    }

    private fun escapeJson(value: String): String {
        return value.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\b", "\\b")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
