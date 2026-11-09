/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 */
package com.codeforge.feature.agent

import com.codeforge.core.domain.extension.ActiveContributions
import com.codeforge.core.domain.extension.PluginManifestParser
import com.codeforge.core.domain.extension.PluginToolRunner
import com.codeforge.core.domain.extension.RequirementContribution
import com.codeforge.core.domain.extension.ToolRunEvent
import com.codeforge.libs.agent.ToolKind
import com.codeforge.libs.mcp.McpTransportKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginAgentToolsTest {

    private val manifest = PluginManifestParser.parse(
        """
        {"id":"my.plugin","name":"P","version":"1","apiVersion":2,"permissions":["agent","shell"],
         "contributes":{
           "agentTools":[
             {"id":"lint","description":"Lint","command":["lint","${'$'}{input}"],"kind":"read"},
             {"id":"deploy","description":"Deploy","command":["deploy"]}],
           "mcpServers":[
             {"name":"docs","url":"https://example.org/mcp"},
             {"name":"local","command":"node","args":["srv.js"]}]}}
        """.trimIndent()
    ).getOrThrow()

    private val active = ActiveContributions.of(listOf(manifest to "/plugins/my.plugin"))

    private class FakeRunner(val events: List<ToolRunEvent>) : PluginToolRunner {
        var lastCall: List<String> = emptyList()
        override suspend fun missingRequirements(pluginId: String) = emptyList<RequirementContribution>()
        override fun install(requirements: List<RequirementContribution>): Flow<ToolRunEvent> = flowOf()
        override fun run(pluginId: String, toolId: String, projectDir: String, input: String): Flow<ToolRunEvent> {
            lastCall = listOf(pluginId, toolId, projectDir, input)
            return flowOf(*events.toTypedArray())
        }
    }

    @Test
    fun `Werkzeuge bekommen normalisierte Namen und passende Art`() {
        val tools = PluginAgentTools.tools(active, FakeRunner(emptyList()), "/p")
        assertEquals(listOf("plugin_my_plugin_lint", "plugin_my_plugin_deploy"), tools.map { it.name })
        assertEquals(ToolKind.READ, tools[0].kind)
        assertEquals(ToolKind.EXECUTE, tools[1].kind)
    }

    @Test
    fun `Ausfuehrung liefert Ausgabe und reicht Eingabe weiter`() = runBlocking {
        val runner = FakeRunner(listOf(ToolRunEvent.Line("a"), ToolRunEvent.Line("b"), ToolRunEvent.Finished(0)))
        val tool = PluginAgentTools.tools(active, runner, "/p").first()
        val result = tool.execute(JsonObject(mapOf("input" to JsonPrimitive("src"))))
        assertFalse(result.isError)
        assertEquals("a\nb", result.content)
        assertEquals(listOf("my.plugin", "lint", "/p", "src"), runner.lastCall)
    }

    @Test
    fun `Exit-Code ungleich null und Timeout sind Fehler`() = runBlocking {
        val failing = PluginAgentTools.tools(active, FakeRunner(listOf(ToolRunEvent.Line("x"), ToolRunEvent.Finished(2))), "/p").first()
        assertTrue(failing.execute(JsonObject(emptyMap())).isError)
        val timedOut = PluginAgentTools.tools(active, FakeRunner(listOf(ToolRunEvent.Finished(-1, timedOut = true))), "/p").first()
        assertTrue(timedOut.execute(JsonObject(emptyMap())).content.startsWith("Zeitüberschreitung"))
    }

    @Test
    fun `MCP-Server werden je Plugin benannt und dem Transport zugeordnet`() {
        val servers = PluginAgentTools.mcpServers(active)
        assertEquals(listOf("my_plugin-docs", "my_plugin-local"), servers.map { it.name })
        assertEquals(McpTransportKind.HTTP, servers[0].transport)
        assertEquals(McpTransportKind.STDIO, servers[1].transport)
        assertEquals("node", servers[1].command)
    }

    @Test
    fun `Manifest verlangt agent und shell Berechtigung`() {
        val bad = PluginManifestParser.parse(
            """{"id":"x","name":"X","version":"1","apiVersion":2,"contributes":{"agentTools":[{"id":"t","description":"d","command":["c"]}]}}"""
        )
        assertTrue(bad.isFailure)
    }
}
