/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 *
 * Brücke zwischen den Plugin-Contributions (`contributes.agentTools`, `contributes.mcpServers`) und dem Agenten.
 */
package com.codeforge.feature.agent

import com.codeforge.core.domain.extension.ActiveContributions
import com.codeforge.core.domain.extension.AgentToolContribution
import com.codeforge.core.domain.extension.AgentToolKind
import com.codeforge.core.domain.extension.PluginToolRunner
import com.codeforge.core.domain.extension.ToolRunEvent
import com.codeforge.libs.agent.AgentTool
import com.codeforge.libs.agent.ToolKind
import com.codeforge.libs.agent.ToolResult
import com.codeforge.libs.agent.objectSchema
import com.codeforge.libs.agent.optString
import com.codeforge.libs.agent.stringProp
import com.codeforge.libs.mcp.McpServerConfig
import com.codeforge.libs.mcp.McpTransportKind
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonObject

/** Werkzeug eines Plugins für den Agenten; startet das deklarierte Kommando über den [PluginToolRunner]. */
internal class PluginAgentTool(
    private val pluginId: String,
    private val contribution: AgentToolContribution,
    private val runner: PluginToolRunner,
    private val projectDir: String,
) : AgentTool {
    override val name: String = PluginAgentTools.toolName(pluginId, contribution.id)
    override val description: String = "${contribution.description} (Plugin $pluginId)"
    override val parameters: JsonObject = objectSchema(
        emptyList(),
        "input" to stringProp(contribution.inputDescription.ifBlank { "Eingabe für das Werkzeug (optional)" }),
    )
    override val kind: ToolKind = if (contribution.kind == AgentToolKind.READ) ToolKind.READ else ToolKind.EXECUTE

    override fun describeCall(arguments: JsonObject): String {
        val input = arguments.optString("input").orEmpty()
        return "Plugin-Werkzeug ${contribution.id} ($pluginId)" + if (input.isBlank()) "" else ": ${input.take(80)}"
    }

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val events = runner.run(pluginId, contribution.id, projectDir, arguments.optString("input").orEmpty()).toList()
        val text = events.filterIsInstance<ToolRunEvent.Line>().joinToString("\n") { it.text }
            .let { if (it.length > MAX_OUTPUT) it.take(MAX_OUTPUT) + "\n… (gekürzt)" else it }
        val end = events.filterIsInstance<ToolRunEvent.Finished>().lastOrNull()
        return when {
            end == null -> ToolResult.error("Werkzeug ohne Ergebnis beendet\n$text")
            end.timedOut -> ToolResult.error("Zeitüberschreitung\n$text")
            end.exitCode != 0 -> ToolResult.error("Exit-Code ${end.exitCode}\n$text")
            else -> ToolResult(text.ifBlank { "(keine Ausgabe)" })
        }
    }

    private companion object {
        const val MAX_OUTPUT = 20_000
    }
}

/** Erzeugt aus den aktiven Plugin-Contributions Agent-Werkzeuge und MCP-Konfigurationen. */
internal object PluginAgentTools {

    /** Werkzeugname `plugin_<plugin>_<tool>` aus a–z, 0–9, `_`, höchstens 64 Zeichen. */
    fun toolName(pluginId: String, toolId: String): String =
        ("plugin_" + pluginId.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_') + "_" + toolId).take(64)

    /** Werkzeuge aller aktiven Plugins; Namenskollisionen (nach Normalisierung) behält nur das erste. */
    fun tools(active: ActiveContributions, runner: PluginToolRunner, projectDir: String): List<AgentTool> =
        active.agentTools
            .map { PluginAgentTool(it.pluginId, it.item, runner, projectDir) }
            .distinctBy { it.name }

    /** MCP-Server aller aktiven Plugins; Name `<plugin>-<server>`, damit er nicht mit Nutzer-Servern kollidiert. */
    fun mcpServers(active: ActiveContributions): List<McpServerConfig> = active.mcpServers.map {
        val name = (it.pluginId + "-" + it.item.name).replace(Regex("[^A-Za-z0-9_-]"), "_")
        if (it.item.url != null) {
            McpServerConfig(name = name, transport = McpTransportKind.HTTP, url = it.item.url)
        } else {
            McpServerConfig(name = name, transport = McpTransportKind.STDIO, command = it.item.command, args = it.item.args)
        }
    }.distinctBy { it.name }
}
