package com.codeforge.libs.mcp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Ein Server im Speicher, der JSON-RPC wie ein MCP-Server beantwortet. */
class FakeMcpServer(
    val tools: List<JsonObject> = listOf(tool("echo")),
    private val pageSize: Int = 100,
    private val instructions: String? = null,
    private val callHandler: (String, JsonObject) -> JsonObject = { name, args ->
        buildJsonObject { put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", "$name:${args}") }) }) }
    },
) {
    val received = mutableListOf<JsonObject>()
    var silentOn: String? = null
    var failCallsWith: Throwable? = null

    fun reply(message: JsonObject): List<JsonObject> {
        received += message
        val method = message["method"]?.jsonPrimitive?.contentOrNull
        val id = message["id"] ?: return emptyList()
        if (method == silentOn) return emptyList()
        fun ok(result: JsonObject) = listOf(buildJsonObject { put("jsonrpc", "2.0"); put("id", id); put("result", result) })
        return when (method) {
            "initialize" -> ok(
                buildJsonObject {
                    put("protocolVersion", LATEST_PROTOCOL_VERSION)
                    putJsonObject("serverInfo") { put("name", "fake"); put("version", "9") }
                    instructions?.let { put("instructions", it) }
                },
            )
            "tools/list" -> {
                val start = message["params"]?.jsonObject?.get("cursor")?.jsonPrimitive?.contentOrNull?.toInt() ?: 0
                val page = tools.drop(start).take(pageSize)
                val next = start + page.size
                ok(buildJsonObject { put("tools", JsonArray(page)); if (next < tools.size) put("nextCursor", next.toString()) })
            }
            "tools/call" -> {
                failCallsWith?.let { throw it }
                val p = message["params"]!!.jsonObject
                ok(callHandler(p["name"]!!.jsonPrimitive.content, p["arguments"]!!.jsonObject))
            }
            else -> listOf(
                buildJsonObject {
                    put("jsonrpc", "2.0"); put("id", id)
                    putJsonObject("error") { put("code", -32601); put("message", "unbekannt: $method") }
                },
            )
        }
    }

    companion object {
        fun tool(name: String, readOnly: Boolean? = null, description: String? = "Werkzeug $name"): JsonObject = buildJsonObject {
            put("name", name)
            description?.let { put("description", it) }
            putJsonObject("inputSchema") {
                put("type", "object")
                putJsonObject("properties") { putJsonObject("text") { put("type", "string") } }
            }
            readOnly?.let { putJsonObject("annotations") { put("readOnlyHint", JsonPrimitive(it)) } }
        }
    }
}

/** Transport, der Nachrichten direkt an einen [FakeMcpServer] gibt. */
class FakeTransport(val server: FakeMcpServer) : McpTransport {
    lateinit var onMessage: (JsonObject) -> Unit
    lateinit var onClosed: (Throwable?) -> Unit
    var closed = false

    override suspend fun start(onMessage: (JsonObject) -> Unit, onClosed: (Throwable?) -> Unit) {
        this.onMessage = onMessage
        this.onClosed = onClosed
    }

    override suspend fun send(message: JsonObject) {
        server.reply(message).forEach(onMessage)
    }

    override fun close() { closed = true }
}
