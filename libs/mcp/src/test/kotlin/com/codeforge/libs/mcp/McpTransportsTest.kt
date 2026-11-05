package com.codeforge.libs.mcp

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class McpTransportsTest {
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply { executor = java.util.concurrent.Executors.newCachedThreadPool() }
    private val fake = FakeMcpServer(tools = listOf(FakeMcpServer.tool("a"), FakeMcpServer.tool("b")))
    private val base get() = "http://127.0.0.1:${server.address.port}"
    private val seenHeaders = mutableListOf<Map<String, String>>()

    @After
    fun tearDown() = server.stop(0)

    private fun HttpExchange.body(): JsonObject = Json.parseToJsonElement(requestBody.readBytes().decodeToString()).jsonObject

    private fun HttpExchange.respond(code: Int, type: String?, text: String, extra: Map<String, String> = emptyMap()) {
        type?.let { responseHeaders.add("Content-Type", it) }
        extra.forEach { (k, v) -> responseHeaders.add(k, v) }
        val bytes = text.toByteArray()
        sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) }
        close()
    }

    @Test
    fun `Streamable HTTP mit JSON-Antwort, Sitzungs-ID und Protokollheader`() = runBlocking {
        server.createContext("/mcp") { ex ->
            seenHeaders += ex.requestHeaders.entries.associate { it.key.lowercase() to it.value.first() }
            val replies = fake.reply(ex.body())
            if (replies.isEmpty()) ex.respond(202, null, "") else ex.respond(200, "application/json", replies[0].toString(), mapOf("Mcp-Session-Id" to "S1"))
        }
        server.start()
        val c = McpClient(StreamableHttpTransport("$base/mcp", mapOf("Authorization" to "Bearer T")))
        c.connect()
        assertEquals(listOf("a", "b"), c.listTools().map { it.name })
        assertEquals("S1", seenHeaders.last()["mcp-session-id"])
        assertEquals(LATEST_PROTOCOL_VERSION, seenHeaders.last()["mcp-protocol-version"])
        assertEquals("Bearer T", seenHeaders.last()["authorization"])
        c.close()
    }

    @Test
    fun `Streamable HTTP mit SSE-Antwort`() = runBlocking {
        server.createContext("/mcp") { ex ->
            val replies = fake.reply(ex.body())
            if (replies.isEmpty()) ex.respond(202, null, "")
            else ex.respond(200, "text/event-stream", ": ping\n\nevent: message\ndata: ${replies[0]}\n\n")
        }
        server.start()
        val c = McpClient(StreamableHttpTransport("$base/mcp"))
        c.connect()
        assertEquals(2, c.listTools().size)
        c.close()
    }

    @Test
    fun `401 wird zur Anmeldung, 404 mit Sitzung zur abgelaufenen Sitzung`() = runBlocking {
        server.createContext("/auth") { ex -> ex.body(); ex.respond(401, null, "", mapOf("WWW-Authenticate" to "Bearer realm=x")) }
        var calls = 0
        server.createContext("/exp") { ex ->
            val msg = ex.body()
            if (++calls <= 2) ex.respond(200, "application/json", fake.reply(msg).firstOrNull()?.toString().orEmpty(), mapOf("Mcp-Session-Id" to "S"))
            else ex.respond(404, null, "")
        }
        server.start()
        try { McpClient(StreamableHttpTransport("$base/auth")).connect(); fail() } catch (e: McpAuthRequiredException) {
            assertEquals("Bearer realm=x", e.wwwAuthenticate)
        }
        val c = McpClient(StreamableHttpTransport("$base/exp"))
        c.connect()
        try { c.listTools(); fail() } catch (e: McpSessionExpiredException) { }
        c.close()
    }

    @Test
    fun `Rueckfall auf das aeltere HTTP+SSE`() = runBlocking {
        val stream = java.util.concurrent.LinkedBlockingQueue<String>()
        server.createContext("/sse") { ex ->
            if (ex.requestMethod == "POST") { ex.body(); ex.respond(405, null, ""); return@createContext }
            ex.responseHeaders.add("Content-Type", "text/event-stream")
            ex.sendResponseHeaders(200, 0)
            ex.responseBody.use { out ->
                out.write("event: endpoint\ndata: /messages?s=1\n\n".toByteArray()); out.flush()
                while (true) {
                    val m = stream.poll(10, java.util.concurrent.TimeUnit.SECONDS) ?: break
                    if (m == "STOP") break
                    out.write("event: message\ndata: $m\n\n".toByteArray()); out.flush()
                }
            }
        }
        server.createContext("/messages") { ex ->
            fake.reply(ex.body()).forEach { stream.add(it.toString()) }
            ex.respond(202, null, "")
        }
        server.start()
        // Ein reiner HTTP+SSE-Server weist das erste POST (initialize) mit 405 ab → Rückfall.
        val c = McpClient(FallbackHttpTransport("$base/sse"))
        c.connect()
        assertEquals(2, c.listTools().size)
        stream.add("STOP")
        c.close()
    }

    @Test
    fun `stdio liest und schreibt Zeilen (cat spiegelt jede Nachricht zurueck)`() = runBlocking {
        val received = java.util.concurrent.LinkedBlockingQueue<JsonObject>()
        val t = StdioTransport("cat", emptyList(), emptyMap()) { cmd, args, _ -> ProcessBuilder(listOf(cmd) + args).start() }
        t.start({ received.add(it) }, { })
        t.send(buildJsonObject { put("jsonrpc", "2.0"); put("method", "echo"); put("id", 5) })
        val back = received.poll(5, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals("echo", back!!["method"]!!.jsonPrimitive.content)
        t.close()
    }

    @Test
    fun `stdio meldet Prozessende samt stderr`() = runBlocking {
        val closed = java.util.concurrent.CompletableFuture<Throwable?>()
        val t = StdioTransport("sh", listOf("-c", "echo kaputt >&2; exit 3"), emptyMap()) { cmd, args, _ -> ProcessBuilder(listOf(cmd) + args).start() }
        t.start({ }, { closed.complete(it) })
        val failure = closed.get(5, java.util.concurrent.TimeUnit.SECONDS)
        assertTrue(failure!!.message!!.contains("beendet"))
        t.close()
    }
}
