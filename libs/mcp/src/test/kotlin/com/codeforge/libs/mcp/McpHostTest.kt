package com.codeforge.libs.mcp

import com.codeforge.libs.agent.AgentPermissionPolicy
import com.codeforge.libs.agent.AgentTool
import com.codeforge.libs.agent.AgentToolRegistry
import com.codeforge.libs.agent.PermissionMode
import com.codeforge.libs.agent.ToolKind
import com.codeforge.libs.agent.ToolResult
import com.codeforge.libs.agent.objectSchema
import com.codeforge.libs.agent.stringProp
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpHostTest {
    private val echo = object : AgentTool {
        override val name = "echo"
        override val kind = ToolKind.READ
        override val description = "Echo"
        override val parameters = objectSchema(listOf("text"), "text" to stringProp("Text"))
        override suspend fun execute(arguments: JsonObject) = ToolResult((arguments["text"] as JsonPrimitive).content)
    }
    private val write = object : AgentTool {
        override val name = "write"
        override val kind = ToolKind.WRITE
        override val description = "Write"
        override val parameters = objectSchema(emptyList())
        override suspend fun execute(arguments: JsonObject) = ToolResult("geschrieben")
    }

    private fun handler(mode: PermissionMode) = McpToolHandler(
        tools = { AgentToolRegistry(listOf(echo, write)) },
        policy = AgentPermissionPolicy(mode),
        resources = { listOf(McpResource("codeforge://x", "X", "d") { "inhalt" }) },
    )

    private fun request(id: Int, method: String, params: JsonObject = JsonObject(emptyMap())) = buildJsonObject {
        put("jsonrpc", "2.0"); put("id", id); put("method", method); put("params", params)
    }

    @Test
    fun handlerListsAndCallsTools() = runTest {
        val h = handler(PermissionMode.PLAN_ONLY)
        val init = h.handle(request(1, "initialize", buildJsonObject { put("protocolVersion", "2025-03-26") }))!!
        assertEquals("2025-03-26", init["result"]!!.jsonObject["protocolVersion"]!!.let { (it as JsonPrimitive).content })
        val tools = h.handle(request(2, "tools/list"))!!["result"]!!.jsonObject["tools"]!!.jsonArray
        assertEquals(listOf("echo", "write"), tools.map { (it.jsonObject["name"] as JsonPrimitive).content }.sorted())
        val call = h.handle(request(3, "tools/call", buildJsonObject { put("name", "echo"); put("arguments", buildJsonObject { put("text", "hallo") }) }))!!
        assertEquals("hallo", (call["result"]!!.jsonObject["content"]!!.jsonArray[0].jsonObject["text"] as JsonPrimitive).content)
        // Plan-Modus: schreibende Werkzeuge werden abgelehnt.
        val denied = h.handle(request(4, "tools/call", buildJsonObject { put("name", "write") }))!!["result"]!!.jsonObject
        assertEquals("true", (denied["isError"] as JsonPrimitive).content)
        val res = h.handle(request(5, "resources/read", buildJsonObject { put("uri", "codeforge://x") }))!!
        assertEquals("inhalt", (res["result"]!!.jsonObject["contents"]!!.jsonArray[0].jsonObject["text"] as JsonPrimitive).content)
        assertEquals(-32601, (h.handle(request(6, "nope"))!!["error"]!!.jsonObject["code"] as JsonPrimitive).content.toInt())
        assertEquals(null, h.handle(buildJsonObject { put("jsonrpc", "2.0"); put("method", "notifications/initialized") }))
    }

    @Test
    fun loopbackServerNeedsToken() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = LoopbackMcpHttpServer(handler(PermissionMode.AUTO_ACCEPT), token = "geheim")
        server.start(scope)
        try {
            fun post(auth: String?, body: String): Pair<Int, String> {
                val c = URL(server.url).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                auth?.let { c.setRequestProperty("Authorization", it) }
                c.outputStream.use { it.write(body.toByteArray()) }
                val code = c.responseCode
                val text = (if (code >= 400) c.errorStream else c.inputStream)?.bufferedReader()?.readText().orEmpty()
                return code to text
            }
            assertEquals(401, post(null, "{}").first)
            val (code, text) = post("Bearer geheim", request(1, "tools/call", buildJsonObject { put("name", "write") }).toString())
            assertEquals(200, code)
            val result = Json.parseToJsonElement(text).jsonObject["result"]!!.jsonObject
            assertTrue((result["content"]!!.jsonArray[0].jsonObject["text"] as JsonPrimitive).contentOrNull == "geschrieben")
            assertEquals(202, post("Bearer geheim", """{"jsonrpc":"2.0","method":"notifications/initialized"}""").first)
        } finally {
            server.stop()
            scope.cancel()
        }
    }

    @Test
    fun oauthChallengeParsing() {
        val p = McpOAuth.parseChallenge("Bearer realm=\"mcp\", resource_metadata=\"https://x.dev/.well-known/oauth-protected-resource\", scope=\"a b\"")
        assertEquals("https://x.dev/.well-known/oauth-protected-resource", p["resource_metadata"])
        assertEquals("a b", p["scope"])
        assertEquals("https://mcp.example.com/mcp", McpOAuth.canonicalResource("HTTPS://MCP.Example.com/mcp/#frag"))
        val t = McpTokens("a", "r", 1_000, "https://t", "c")
        assertEquals(t, McpTokens.fromJson(t.toJson()))
        assertTrue(t.expired(nowMs = 2_000))
    }
}
