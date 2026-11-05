package com.codeforge.feature.agent

import com.codeforge.libs.mcp.McpServerConfig
import com.codeforge.libs.mcp.McpSource
import com.codeforge.libs.mcp.McpTransportKind
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentConnectionsTest {
    private val base = kotlin.io.path.createTempDirectory("conn").toFile()
    private val project = File(base, "proj").apply { mkdirs() }
    private val conn = AgentConnections(File(base, "agent"))

    @After
    fun tearDown() { base.deleteRecursively() }

    private fun http(name: String, url: String) = McpServerConfig(name, McpTransportKind.HTTP, url = url)

    @Test
    fun `App-Server werden gespeichert und gelesen`() {
        assertTrue(conn.appServers().isEmpty())
        conn.saveAppServers(listOf(http("a", "https://a/mcp")))
        assertEquals(listOf("a"), conn.appServers().map { it.name })
    }

    @Test
    fun `Projekt-Server sind erst nach Freigabe vertraut, eine geaenderte URL braucht neue Freigabe`() {
        File(project, ".mcp.json").writeText("""{"mcpServers":{"p":{"url":"https://p/mcp"}}}""")
        conn.saveAppServers(listOf(http("a", "https://a/mcp")))
        val first = conn.entries(project)
        assertTrue(first.single { it.source == McpSource.APP }.trusted)
        assertFalse(first.single { it.source == McpSource.PROJECT }.trusted)

        conn.trust(conn.projectServers(project).single())
        assertTrue(conn.entries(project).single { it.source == McpSource.PROJECT }.trusted)

        File(project, ".mcp.json").writeText("""{"mcpServers":{"p":{"url":"https://evil/mcp"}}}""")
        assertFalse(conn.entries(project).single { it.source == McpSource.PROJECT }.trusted)
    }

    @Test
    fun `kaputte Dateien ergeben leere Listen`() {
        File(project, ".mcp.json").writeText("{kaputt")
        File(base, "agent").mkdirs()
        File(base, "agent/mcp.json").writeText("???")
        File(base, "agent/mcp-trust.json").writeText("???")
        assertTrue(conn.entries(project).isEmpty())
        assertTrue(conn.entries(null).isEmpty())
    }

    @Test
    fun `shellQuote schuetzt einfache Anfuehrungszeichen`() {
        assertEquals("'a'\\''b'", TermuxMcpLauncher.shellQuote("a'b"))
    }
}
