package com.codeforge.libs.mcp

import com.codeforge.libs.agent.ToolKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class McpManagerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val servers = HashMap<String, FakeMcpServer>()
    private val created = ArrayList<String>()
    private val factory = McpTransportFactory { cfg ->
        created += cfg.name
        if (cfg.url == "boom") object : McpTransport {
            override suspend fun start(onMessage: (kotlinx.serialization.json.JsonObject) -> Unit, onClosed: (Throwable?) -> Unit) = throw java.io.IOException("nicht erreichbar")
            override suspend fun send(message: kotlinx.serialization.json.JsonObject) {}
            override fun close() {}
        } else FakeTransport(servers.getOrPut(cfg.name) { FakeMcpServer(tools = listOf(FakeMcpServer.tool("read", readOnly = true), FakeMcpServer.tool("write")), instructions = "Hinweis ${cfg.name}") })
    }

    private fun http(name: String, url: String = "https://x/$name", disabled: Boolean = false) =
        McpServerConfig(name, McpTransportKind.HTTP, url = url, disabled = disabled)

    private fun entry(c: McpServerConfig, source: McpSource = McpSource.APP, trusted: Boolean = true) = McpServerEntry(c, source, trusted)

    private fun McpManager.await(cond: (List<McpServerState>) -> Boolean) = runBlocking {
        val end = System.nanoTime() + 10_000_000_000L
        while (!cond(servers.value) && System.nanoTime() < end) Thread.sleep(10)
        assertTrue("Zustand nicht erreicht: ${servers.value.map { it.entry.config.name to it.status }}", cond(servers.value))
    }

    @Test
    fun `verbindet Server, benennt Werkzeuge um und liefert Hinweise`() = runBlocking {
        val m = McpManager(scope, factory)
        m.setEntries(listOf(entry(http("docs")), entry(http("off", disabled = true))))
        m.await { l -> l.first { it.entry.config.name == "docs" }.status == McpStatus.READY }
        assertEquals(McpStatus.DISABLED, m.servers.value.first { it.entry.config.name == "off" }.status)
        val tools = m.tools.value
        assertEquals(listOf("mcp__docs__read", "mcp__docs__write"), tools.map { it.name })
        assertEquals(ToolKind.READ, tools[0].kind)
        assertEquals(ToolKind.EXECUTE, tools[1].kind)
        assertEquals("[docs] Hinweis docs", m.serverNotes())
        val result = tools[1].execute(buildJsonObject { put("text", "hi") })
        assertTrue(result.content.startsWith("write:"))
        m.close()
    }

    @Test
    fun `Projekt-Server ohne Freigabe verbinden nicht, mit Freigabe schon, Projekt gewinnt bei gleichem Namen`() = runBlocking {
        val m = McpManager(scope, factory)
        m.setEntries(listOf(entry(http("a", "https://app/a")), entry(http("a", "https://proj/a"), McpSource.PROJECT, trusted = false)))
        assertEquals(1, m.servers.value.size)
        assertEquals(McpStatus.NEEDS_TRUST, m.servers.value.single().status)
        assertTrue(created.isEmpty())
        m.setEntries(listOf(entry(http("a", "https://proj/a"), McpSource.PROJECT, trusted = true)))
        m.await { it.single().status == McpStatus.READY }
        assertEquals(1, created.size)
        m.close()
    }

    @Test
    fun `Fehler werden je Server gemeldet, unveraenderte Server bleiben verbunden, Entfernte verschwinden`() = runBlocking {
        val m = McpManager(scope, factory)
        m.setEntries(listOf(entry(http("ok")), entry(http("bad", "boom"))))
        m.await { l -> l.all { it.status == McpStatus.READY || it.status == McpStatus.FAILED } }
        assertEquals(McpStatus.FAILED, m.servers.value.first { it.entry.config.name == "bad" }.status)
        assertTrue(m.servers.value.first { it.entry.config.name == "bad" }.message!!.contains("nicht erreichbar"))
        val before = created.count { it == "ok" }
        m.setEntries(listOf(entry(http("ok"))))
        assertEquals(listOf("ok"), m.servers.value.map { it.entry.config.name })
        assertEquals(before, created.count { it == "ok" })
        assertEquals(2, m.tools.value.size)
        m.close()
    }

    @Test
    fun `Namenskonflikte bekommen ein Suffix`() = runBlocking {
        servers["dup"] = FakeMcpServer(tools = listOf(FakeMcpServer.tool("a.b"), FakeMcpServer.tool("a_b")))
        val m = McpManager(scope, factory)
        m.setEntries(listOf(entry(http("dup"))))
        m.await { it.single().status == McpStatus.READY }
        assertEquals(2, m.tools.value.map { it.name }.toSet().size)
        m.close()
    }
}
