/**
 * Modul: :libs:mcp
 * @author Thomas Schmid
 *
 * CodeForge Mobile als MCP-Server: Die Werkzeuge des eingebauten Agenten werden über MCP angeboten, damit externe
 * Agenten (z. B. in Termux) sie nutzen können. Jeder Aufruf läuft über dieselbe [AgentPermissionPolicy] wie im
 * eigenen Agenten – schreibende Werkzeuge werden also vorher freigegeben, lesende fragen nie.
 * Vorbild: `CodeAssistMcpServer`/`HttpMcpServer` aus CodeAssist (GPL-3.0).
 */
package com.codeforge.libs.mcp

import com.codeforge.libs.agent.AgentPermissionPolicy
import com.codeforge.libs.agent.AgentToolRegistry
import com.codeforge.libs.agent.PolicyVerdict
import com.codeforge.libs.agent.ToolArgumentException
import java.io.BufferedInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/** Eine lesbare Ressource des Servers (z. B. Projektüberblick). */
data class McpResource(val uri: String, val name: String, val description: String, val read: suspend () -> String)

/**
 * JSON-RPC-Logik des Servers (ohne Transport, testbar).
 *
 * @param tools liefert die aktuell verfügbaren Werkzeuge (Projektwechsel ändern sie).
 * @param policy Berechtigungen; identisch mit dem eingebauten Agenten.
 * @param resources lesbare Ressourcen (Projektüberblick, Gedächtnis).
 * @param instructions Hinweis an den Client (`initialize`).
 * @param serverVersion Version in `serverInfo`.
 */
class McpToolHandler(
    private val tools: () -> AgentToolRegistry?,
    private val policy: AgentPermissionPolicy,
    private val resources: () -> List<McpResource> = { emptyList() },
    private val instructions: String = DEFAULT_INSTRUCTIONS,
    private val serverVersion: String = "1.0.0",
) {
    /** Bearbeitet eine Nachricht; `null` für Benachrichtigungen (keine Antwort). */
    @Suppress("TooGenericExceptionCaught") // Jeder Fehler wird als JSON-RPC-Fehler an den Client gemeldet.
    suspend fun handle(message: JsonObject): JsonObject? {
        val id = message["id"]?.takeIf { it !is JsonNull }
        val method = (message["method"] as? JsonPrimitive)?.contentOrNull
        val params = message["params"] as? JsonObject ?: JsonObject(emptyMap())
        if (method == null) return error(id, INVALID_REQUEST, "Ungültige Anfrage")
        if (id == null) return null
        return try {
            when (method) {
                "initialize" -> result(id, initialize(params))
                "ping" -> result(id, JsonObject(emptyMap()))
                "tools/list" -> result(id, listTools())
                "tools/call" -> result(id, callTool(params))
                "resources/list" -> result(id, listResources())
                "resources/read" -> result(id, readResource(params))
                "prompts/list" -> result(id, buildJsonObject { put("prompts", JsonArray(emptyList())) })
                else -> error(id, METHOD_NOT_FOUND, "Unbekannte Methode: $method")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            error(id, INVALID_PARAMS, e.message ?: "Ungültige Parameter")
        } catch (e: Exception) {
            error(id, INTERNAL_ERROR, e.message ?: e.toString())
        }
    }

    private fun initialize(params: JsonObject): JsonObject {
        val requested = (params["protocolVersion"] as? JsonPrimitive)?.contentOrNull
        return buildJsonObject {
            put("protocolVersion", requested?.takeIf { it in SUPPORTED_VERSIONS } ?: LATEST_PROTOCOL_VERSION)
            put("capabilities", buildJsonObject {
                put("tools", buildJsonObject { put("listChanged", false) })
                put("resources", buildJsonObject { put("listChanged", false) })
            })
            put("serverInfo", buildJsonObject { put("name", "codeforge"); put("version", serverVersion) })
            put("instructions", instructions)
        }
    }

    private fun listTools(): JsonObject = buildJsonObject {
        put("tools", buildJsonArray {
            tools()?.specs().orEmpty().forEach { spec ->
                add(buildJsonObject { put("name", spec.name); put("description", spec.description); put("inputSchema", spec.parameters) })
            }
        })
    }

    private suspend fun callTool(params: JsonObject): JsonObject {
        val name = (params["name"] as? JsonPrimitive)?.contentOrNull ?: throw IllegalArgumentException("'name' fehlt")
        val args = params["arguments"] as? JsonObject ?: JsonObject(emptyMap())
        val registry = tools()
        val tool = registry?.get(name)
        val verdict = tool?.let { policy.check(it, args) }
        return when {
            registry == null -> toolError("Kein Projekt geöffnet – öffne ein Projekt in CodeForge Mobile.")
            tool == null -> toolError("Unbekanntes Werkzeug: $name")
            verdict is PolicyVerdict.Denied -> toolError(verdict.reason)
            else -> runTool(tool, args)
        }
    }

    @Suppress("TooGenericExceptionCaught") // Werkzeugfehler gehen als isError an den Client, wie im eigenen Agenten.
    private suspend fun runTool(tool: com.codeforge.libs.agent.AgentTool, args: JsonObject): JsonObject {
        val r = try {
            tool.execute(args)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ToolArgumentException) {
            com.codeforge.libs.agent.ToolResult.error(e.message ?: "Ungültige Argumente")
        } catch (e: Exception) {
            com.codeforge.libs.agent.ToolResult.error("${tool.name} fehlgeschlagen: ${e.message ?: e::class.simpleName}")
        }
        return buildJsonObject {
            put(KEY_CONTENT, buildJsonArray {
                add(buildJsonObject { put(KEY_TYPE, KEY_TEXT); put(KEY_TEXT, r.content) })
                r.images.forEach { img -> add(buildJsonObject { put(KEY_TYPE, "image"); put("data", img.base64); put("mimeType", img.mediaType) }) }
            })
            put("isError", r.isError)
        }
    }

    private fun listResources(): JsonObject = buildJsonObject {
        put("resources", buildJsonArray {
            resources().forEach { r ->
                add(buildJsonObject { put("uri", r.uri); put("name", r.name); put("description", r.description); put("mimeType", TEXT_PLAIN) })
            }
        })
    }

    private suspend fun readResource(params: JsonObject): JsonObject {
        val uri = (params["uri"] as? JsonPrimitive)?.contentOrNull ?: throw IllegalArgumentException("'uri' fehlt")
        val res = resources().firstOrNull { it.uri == uri } ?: throw IllegalArgumentException("Unbekannte Ressource: $uri")
        val text = res.read()
        return buildJsonObject {
            put("contents", buildJsonArray { add(buildJsonObject { put("uri", uri); put("mimeType", TEXT_PLAIN); put(KEY_TEXT, text) }) })
        }
    }

    private fun toolError(text: String) = buildJsonObject {
        put(KEY_CONTENT, buildJsonArray { add(buildJsonObject { put(KEY_TYPE, KEY_TEXT); put(KEY_TEXT, text) }) })
        put("isError", true)
    }

    private fun result(id: JsonElement, value: JsonObject) = buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", value) }

    private fun error(id: JsonElement?, code: Int, message: String) = buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id ?: JsonNull)
        put("error", buildJsonObject { put("code", code); put("message", message) })
    }

    /** Konstanten. */
    companion object {
        private const val INVALID_REQUEST = -32_600
        private const val METHOD_NOT_FOUND = -32_601
        private const val INVALID_PARAMS = -32_602
        private const val INTERNAL_ERROR = -32_603
        private const val KEY_CONTENT = "content"
        private const val KEY_TYPE = "type"
        private const val KEY_TEXT = "text"
        private const val TEXT_PLAIN = "text/plain"

        /** Unterstützte Protokollversionen. */
        val SUPPORTED_VERSIONS = setOf("2024-11-05", "2025-03-26", "2025-06-18")

        /** Standard-Hinweis an externe Agenten. */
        const val DEFAULT_INSTRUCTIONS =
            "Tools of the CodeForge Mobile IDE (Android). Paths are relative to the open project. Read tools run at once; " +
                "changes and builds may need the user's approval on the phone. Prefer get_diagnostics, find_references and " +
                "run_gradle over guessing."
    }
}

/**
 * MCP über HTTP (Streamable HTTP, nur JSON-Antworten) auf `127.0.0.1`. Jeder Aufruf braucht den [token]
 * (`Authorization: Bearer …`); Anfragen mit fremdem `Origin` werden abgewiesen (Schutz vor DNS-Rebinding).
 */
class LoopbackMcpHttpServer(
    private val handler: McpToolHandler,
    private val token: String,
    private val port: Int = 0,
) {
    private var socket: ServerSocket? = null
    private var job: Job? = null
    private val json = Json { ignoreUnknownKeys = true }

    /** Tatsächlicher Port (nach [start]). */
    val boundPort: Int get() = socket?.localPort ?: -1

    /** Adresse für Clients. */
    val url: String get() = "http://127.0.0.1:$boundPort/mcp"

    /** Startet den Server in [scope]. */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        val server = ServerSocket(port, BACKLOG, InetAddress.getByName("127.0.0.1"))
        socket = server
        job = scope.launch(Dispatchers.IO) {
            while (isActive && !server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: continue
                launch { client.use { runCatching { serve(it) } } }
            }
        }
    }

    /** Beendet den Server. */
    fun stop() {
        job?.cancel()
        job = null
        runCatching { socket?.close() }
        socket = null
    }

    private class Request(val method: String, val path: String, val headers: Map<String, String>, val input: BufferedInputStream)

    private class Response(val status: String, val body: String = EMPTY_JSON, val extra: Map<String, String> = emptyMap())

    private suspend fun serve(client: Socket) {
        client.soTimeout = READ_TIMEOUT_MS
        val request = readRequest(BufferedInputStream(client.getInputStream())) ?: return
        val response = rejection(request) ?: dispatch(request)
        write(client, response)
    }

    private fun readRequest(input: BufferedInputStream): Request? {
        val requestLine = readLine(input) ?: return null
        val headers = HashMap<String, String>()
        var line = readLine(input)
        while (!line.isNullOrEmpty()) {
            val i = line.indexOf(':')
            if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
            line = readLine(input)
        }
        val parts = requestLine.split(' ')
        return Request(parts.getOrNull(0).orEmpty(), parts.getOrNull(1).orEmpty(), headers, input)
    }

    /** Antwort für unzulässige Anfragen oder `null`, wenn die Anfrage weiter darf. */
    private fun rejection(r: Request): Response? {
        val origin = r.headers["origin"]
        return when {
            r.path.substringBefore('?') != "/mcp" -> Response("404 Not Found")
            origin != null && !LOCAL_ORIGIN.matches(origin) -> Response("403 Forbidden")
            !constantTimeEquals(r.headers["authorization"].orEmpty(), "Bearer $token") ->
                Response("401 Unauthorized", extra = mapOf("WWW-Authenticate" to "Bearer realm=\"codeforge\""))
            r.method == "DELETE" -> Response("200 OK")
            r.method != "POST" -> Response("405 Method Not Allowed", extra = mapOf("Allow" to "POST, DELETE"))
            else -> null
        }
    }

    private suspend fun dispatch(r: Request): Response {
        val length = r.headers["content-length"]?.toIntOrNull()
        val body = when {
            length == null -> return Response("411 Length Required")
            length > MAX_BODY -> return Response("413 Payload Too Large")
            else -> readBody(r.input, length) ?: return Response("400 Bad Request")
        }
        val parsed = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return Response("400 Bad Request", PARSE_ERROR)
        val responses = when (parsed) {
            is JsonArray -> parsed.mapNotNull { (it as? JsonObject)?.let { m -> handler.handle(m) } }
            is JsonObject -> listOfNotNull(handler.handle(parsed))
            else -> emptyList()
        }
        val isInit = ((parsed as? JsonObject)?.get("method") as? JsonPrimitive)?.contentOrNull == "initialize"
        val session = if (isInit) mapOf("Mcp-Session-Id" to LoopbackSession.newId()) else emptyMap()
        return when {
            responses.isEmpty() -> Response("202 Accepted", "", session)
            parsed is JsonArray -> Response("200 OK", JsonArray(responses).toString(), session)
            else -> Response("200 OK", responses.first().toString(), session)
        }
    }

    private fun readBody(input: BufferedInputStream, length: Int): String? {
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) return null
            read += n
        }
        return String(body, Charsets.UTF_8)
    }

    private fun write(client: Socket, r: Response) {
        val bytes = r.body.toByteArray()
        val head = StringBuilder("HTTP/1.1 ${r.status}\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n")
        r.extra.forEach { (k, v) -> head.append(k).append(": ").append(v).append("\r\n") }
        val out = client.getOutputStream()
        out.write(head.append("\r\n").toString().toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) return sb.toString().trimEnd('\r')
            if (sb.length > MAX_LINE) throw IOException("Zeile zu lang")
            sb.append(c.toChar())
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())

    private companion object {
        const val EMPTY_JSON = "{}"
        const val PARSE_ERROR = """{"jsonrpc":"2.0","id":null,"error":{"code":-32700,"message":"Parse error"}}"""
        val LOCAL_ORIGIN = Regex("""^https?://(localhost|127\.0\.0\.1)(:\d+)?$""")
        const val BACKLOG = 8
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_BODY = 4_000_000
        const val MAX_LINE = 8192
    }
}

/** Sitzungskennungen für `Mcp-Session-Id`. */
internal object LoopbackSession {
    fun newId(): String = com.codeforge.libs.agent.LoopbackRedirect.randomToken(16)
}
