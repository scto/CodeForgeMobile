/**
 * Modul: :libs:mcp
 * @author Thomas Schmid
 */
package com.codeforge.libs.mcp

import com.codeforge.libs.agent.AgentTool
import com.codeforge.libs.agent.ToolKind
import com.codeforge.libs.agent.ToolResult
import kotlinx.serialization.json.JsonObject

/**
 * Ein Werkzeug eines MCP-Servers als [AgentTool] unter dem Namen `mcp__<server>__<werkzeug>`.
 *
 * Der Aufruf gilt als „Ausführen“ (Freigabe nötig), außer der Server markiert das Werkzeug als `readOnlyHint`.
 */
class McpRemoteTool(
    override val name: String,
    private val server: String,
    private val info: McpToolInfo,
    private val call: suspend (toolName: String, arguments: JsonObject) -> McpCallResult,
) : AgentTool {
    override val kind: ToolKind = if (info.readOnly == true) ToolKind.READ else ToolKind.EXECUTE
    override val description: String =
        "[MCP: $server] " + (info.description ?: info.title ?: info.name).take(MAX_DESCRIPTION)
    override val parameters: JsonObject = McpSchemas.normalize(info.inputSchema)

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val result = call(info.name, arguments)
        var text = result.text.ifBlank { if (result.isError) "Der Server meldete einen Fehler ohne Text." else "(leere Antwort)" }
        if (text.length > MAX_RESULT) text = text.take(MAX_RESULT) + "\n[… ${text.length - MAX_RESULT} Zeichen gekürzt]"
        if (result.imageCount > 0) text += "\n[${result.imageCount} Bild(er) nicht darstellbar]"
        return ToolResult(text, result.isError)
    }

    override fun describeCall(arguments: JsonObject): String {
        val args = arguments.toString().let { if (it.length > MAX_ARGS) it.take(MAX_ARGS) + "…" else it }
        return "MCP $server · ${info.name} $args"
    }

    /** Namensbildung. */
    companion object {
        private const val MAX_DESCRIPTION = 1000
        private const val MAX_RESULT = 20_000
        private const val MAX_ARGS = 160
        private const val MAX_NAME = 64

        /** Baut `mcp__server__tool`, bereinigt auf `[A-Za-z0-9_-]` und höchstens 64 Zeichen (Grenze der Anbieter). */
        fun toolName(server: String, tool: String): String {
            fun clean(s: String) = s.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val full = "mcp__${clean(server)}__${clean(tool)}"
            return if (full.length <= MAX_NAME) full else full.take(MAX_NAME - 9) + "_" + Integer.toHexString(full.hashCode()).padStart(8, '0')
        }
    }
}
