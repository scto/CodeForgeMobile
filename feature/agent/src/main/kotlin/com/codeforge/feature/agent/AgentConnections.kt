/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 */
package com.codeforge.feature.agent

import com.codeforge.libs.mcp.McpServerConfig
import com.codeforge.libs.mcp.McpServerEntry
import com.codeforge.libs.mcp.McpSource
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray

/**
 * Konfiguration der MCP-Verbindungen:
 * - **App-Server** (vom Nutzer im Panel angelegt) in `<dir>/mcp.json`,
 * - **Projekt-Server** aus `<projekt>/.mcp.json` (Format von Claude Code/Cursor) – sie bleiben unvertraut, bis der Nutzer
 *   sie freigibt, denn ein geklontes Repository kann beliebige Befehle oder URLs eintragen.
 *
 * Die Freigabe gilt je Name *und* Ziel (URL bzw. Befehl); zeigt ein Eintrag woandershin, ist neu freizugeben.
 */
class AgentConnections(private val dir: File) {
    private val appFile get() = File(dir, "mcp.json")
    private val trustFile get() = File(dir, "mcp-trust.json")

    /** Vom Nutzer angelegte Server. */
    fun appServers(): List<McpServerConfig> = appFile.takeIf { it.isFile }?.let { McpServerConfig.parseFile(it.readText()) }.orEmpty()

    /** Ersetzt die App-Server. */
    fun saveAppServers(servers: List<McpServerConfig>) {
        dir.mkdirs()
        val tmp = File(dir, "mcp.json.tmp")
        tmp.writeText(McpServerConfig.toFile(servers))
        if (!tmp.renameTo(appFile)) { appFile.writeText(tmp.readText()); tmp.delete() }
    }

    /** Server aus der `.mcp.json` des Projekts [root]. */
    fun projectServers(root: File): List<McpServerConfig> =
        File(root, ".mcp.json").takeIf { it.isFile }?.let { McpServerConfig.parseFile(it.readText()) }.orEmpty()

    /** MCP-Server aus aktiven Plugins (`contributes.mcpServers`); wie Projekt-Server unvertraut, bis der Nutzer sie freigibt. */
    @Volatile
    var pluginServers: List<McpServerConfig> = emptyList()

    private fun key(c: McpServerConfig) = c.name + "|" + c.identity

    private fun trusted(): Set<String> = trustFile.takeIf { it.isFile }
        ?.let { runCatching { Json.parseToJsonElement(it.readText()).jsonArray.mapNotNull { e -> (e as? JsonPrimitive)?.contentOrNull } }.getOrNull() }
        .orEmpty().toSet()

    /** Gibt den Projekt-Server [config] frei. */
    fun trust(config: McpServerConfig) {
        dir.mkdirs()
        trustFile.writeText(JsonArray((trusted() + key(config)).sorted().map { JsonPrimitive(it) }).toString())
    }

    /** Alle Einträge für das Projekt [root]; App-Server sind immer vertraut, Projekt-Server nur nach Freigabe. */
    fun entries(root: File?): List<McpServerEntry> {
        val ok = trusted()
        val app = appServers().map { McpServerEntry(it, McpSource.APP, trusted = true) }
        val project = root?.let(::projectServers).orEmpty().map { McpServerEntry(it, McpSource.PROJECT, trusted = key(it) in ok) }
        val plugins = pluginServers.map { McpServerEntry(it, McpSource.PROJECT, trusted = key(it) in ok) }
        return app + project + plugins
    }

    private val contextTrustFile get() = File(dir, "context-trust.json")

    private fun contextKey(root: File, fingerprint: String) = runCatching { root.canonicalPath }.getOrDefault(root.path) + "|" + fingerprint

    private fun trustedContexts(): Set<String> = contextTrustFile.takeIf { it.isFile }
        ?.let { runCatching { Json.parseToJsonElement(it.readText()).jsonArray.mapNotNull { e -> (e as? JsonPrimitive)?.contentOrNull } }.getOrNull() }
        .orEmpty().toSet()

    /** `true`, wenn Regeln/Skills mit [fingerprint] im Projekt [root] freigegeben wurden. */
    fun isContextTrusted(root: File, fingerprint: String): Boolean = contextKey(root, fingerprint) in trustedContexts()

    /** Gibt Regeln/Skills mit [fingerprint] im Projekt [root] frei. */
    fun trustContext(root: File, fingerprint: String) {
        dir.mkdirs()
        contextTrustFile.writeText(JsonArray((trustedContexts() + contextKey(root, fingerprint)).sorted().map { JsonPrimitive(it) }).toString())
    }
}
