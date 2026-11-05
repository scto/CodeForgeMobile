package com.codeforge.libs.mcp

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class McpClientTest {
    private fun client(server: FakeMcpServer) = McpClient(FakeTransport(server))

    @Test
    fun `Handshake liefert Serverdaten, initialized wird gesendet`() = runBlocking {
        val server = FakeMcpServer(instructions = "Nutze mich sparsam")
        val c = client(server)
        c.connect()
        assertEquals("fake", c.serverName)
        assertEquals("Nutze mich sparsam", c.instructions)
        assertEquals("initialize", server.received.first()["method"]!!.jsonPrimitive.content)
        assertEquals("notifications/initialized", server.received.last()["method"]!!.jsonPrimitive.content)
        c.close()
    }

    @Test
    fun `Werkzeugliste folgt der Paginierung und liest Annotationen`() = runBlocking {
        val server = FakeMcpServer(tools = (1..5).map { FakeMcpServer.tool("t$it", readOnly = if (it == 2) true else null) }, pageSize = 2)
        val c = client(server)
        c.connect()
        val tools = c.listTools()
        assertEquals(5, tools.size)
        assertEquals(true, tools[1].readOnly)
        assertNull(tools[0].readOnly)
        c.close()
    }

    @Test
    fun `Aufruf flacht Text, Ressourcen und Fehlerkennzeichen ab`() = runBlocking {
        val server = FakeMcpServer(callHandler = { _, _ ->
            kotlinx.serialization.json.Json.parseToJsonElement(
                """{"isError":true,"content":[{"type":"text","text":"a"},{"type":"image","data":"x"},{"type":"resource","resource":{"uri":"u","text":"b"}}]}""",
            ) as kotlinx.serialization.json.JsonObject
        })
        val c = client(server)
        c.connect()
        val r = c.callTool("x", buildJsonObject { })
        assertEquals("a\nb", r.text)
        assertEquals(1, r.imageCount)
        assertTrue(r.isError)
        c.close()
    }

    @Test
    fun `JSON-RPC-Fehler wird zur McpException, Zeitlimit zur McpTimeoutException`() = runBlocking {
        val server = FakeMcpServer()
        val c = client(server)
        c.connect()
        try { c.request("gibts/nicht", null, 1000); fail() } catch (e: McpException) { assertEquals(-32601, e.code) }
        server.silentOn = "tools/list"
        try { c.listTools(timeoutMs = 100); fail() } catch (e: McpTimeoutException) { assertEquals("tools/list", e.method) }
        c.close()
    }

    @Test
    fun `ping des Servers wird beantwortet, unbekannte Server-Anfragen mit Fehler`() = runBlocking {
        val server = FakeMcpServer()
        val t = FakeTransport(server)
        val c = McpClient(t)
        c.connect()
        t.onMessage(buildJsonObject { put("jsonrpc", "2.0"); put("id", 77); put("method", "ping") })
        t.onMessage(buildJsonObject { put("jsonrpc", "2.0"); put("id", 78); put("method", "sampling/createMessage") })
        Thread.sleep(200)
        val replies = server.received.filter { it["method"] == null }
        assertEquals(2, replies.size)
        assertTrue(replies[0].containsKey("result"))
        assertTrue(replies[1].containsKey("error"))
        c.close()
    }

    @Test
    fun `Verbindungsabbruch bricht offene Anfragen ab und meldet onDisconnected`() = runBlocking {
        val server = FakeMcpServer().apply { silentOn = "tools/list" }
        val t = FakeTransport(server)
        val c = McpClient(t)
        var disconnected = false
        c.connect()
        c.onDisconnected = { disconnected = true }
        val pendingCall = async { runCatching { c.listTools(timeoutMs = 5000) } }
        Thread.sleep(100)
        t.onClosed(java.io.IOException("weg"))
        val res = pendingCall.await()
        assertTrue(res.isFailure)
        assertTrue(disconnected)
        assertTrue(c.closed)
        assertFalse(runCatching { c.request("x", null, 100) }.isSuccess)
    }

    @Test
    fun `tools list_changed ruft den Rueckruf`() = runBlocking {
        val t = FakeTransport(FakeMcpServer())
        val c = McpClient(t)
        c.connect()
        var changed = 0
        c.onToolsChanged = { changed++ }
        t.onMessage(buildJsonObject { put("jsonrpc", "2.0"); put("method", "notifications/tools/list_changed") })
        assertEquals(1, changed)
        c.close()
    }
}
