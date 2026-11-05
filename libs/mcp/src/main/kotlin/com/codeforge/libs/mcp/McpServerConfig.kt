/**
 * Modul: :libs:mcp
 * @author Thomas Schmid
 *
 * Konfiguration von MCP-Servern im `mcpServers`-Format, das auch Claude Code und Cursor in `.mcp.json` lesen.
 * Das Konzept der Anbindung ist an das Vorgehen in CodeAssist (GPL-3.0) angelehnt; der Code ist eigenständig.
 */
package com.codeforge.libs.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Wie die App einen Server erreicht. */
enum class McpTransportKind {
    /** Streamable HTTP; fällt auf das ältere HTTP+SSE zurück, wenn der Server nur das kann. */
    HTTP,

    /** Nur das ältere HTTP+SSE. */
    SSE,

    /** Lokaler Befehl (in der Termux-Umgebung). */
    STDIO,
}

/**
 * Ein vom Nutzer eingetragener Server.
 *
 * ```json
 * { "mcpServers": {
 *     "docs":  { "type": "http", "url": "https://example.com/mcp", "headers": { "Authorization": "Bearer …" } },
 *     "files": { "command": "npx", "args": ["-y", "@modelcontextprotocol/server-filesystem", "."] }
 * } }
 * ```
 *
 * Ohne `type` gilt: `url` → HTTP, `command` → stdio. `disabled: true` behält den Eintrag, ohne zu verbinden.
 */
data class McpServerConfig(
    val name: String,
    val transport: McpTransportKind,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val disabled: Boolean = false,
) {
    /** Was den Server für die Vertrauensfreigabe identifiziert: andere URL oder anderer Befehl = anderer Server. */
    val identity: String
        get() = when (transport) {
            McpTransportKind.STDIO -> "stdio:" + (listOfNotNull(command) + args).joinToString(" ")
            else -> "url:" + url.orEmpty()
        }

    /** Dateiformat lesen und schreiben. */
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true; prettyPrint = true }

        /** Liest eine `.mcp.json`; sinnlose Einträge (weder `url` noch `command`) und kaputtes JSON ergeben nichts. */
        fun parseFile(text: String): List<McpServerConfig> {
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyList()
            val servers = root["mcpServers"] as? JsonObject ?: root["servers"] as? JsonObject ?: return emptyList()
            return servers.mapNotNull { (name, value) -> (value as? JsonObject)?.let { parseEntry(name, it) } }
        }

        /** Ein einzelner Eintrag; `null`, wenn er nicht benutzbar ist. */
        fun parseEntry(name: String, obj: JsonObject): McpServerConfig? {
            val url = obj.str("url")?.trim()?.takeIf { it.isNotEmpty() }
            val command = obj.str("command")?.trim()?.takeIf { it.isNotEmpty() }
            val transport = when (obj.str("type")?.lowercase()) {
                "sse" -> McpTransportKind.SSE
                "stdio" -> McpTransportKind.STDIO
                "http", "streamable-http", "streamablehttp" -> McpTransportKind.HTTP
                else -> when {
                    url != null -> McpTransportKind.HTTP
                    command != null -> McpTransportKind.STDIO
                    else -> return null
                }
            }
            if (transport == McpTransportKind.STDIO && command == null) return null
            if (transport != McpTransportKind.STDIO && url == null) return null
            return McpServerConfig(
                name = name,
                transport = transport,
                url = url,
                headers = obj.stringMap("headers"),
                command = command,
                args = (obj["args"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty(),
                env = obj.stringMap("env"),
                disabled = (obj["disabled"] as? JsonPrimitive)?.booleanOrNull == true,
            )
        }

        /** Schreibt [servers] im selben Format. */
        fun toFile(servers: List<McpServerConfig>): String = json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject { putJsonObject("mcpServers") { servers.forEach { s -> put(s.name, toEntry(s)) } } },
        )

        private fun toEntry(s: McpServerConfig): JsonObject = buildJsonObject {
            when (s.transport) {
                McpTransportKind.STDIO -> {
                    put("type", "stdio")
                    put("command", s.command.orEmpty())
                    if (s.args.isNotEmpty()) putJsonArray("args") { s.args.forEach { add(JsonPrimitive(it)) } }
                    if (s.env.isNotEmpty()) putJsonObject("env") { s.env.forEach { (k, v) -> put(k, v) } }
                }
                else -> {
                    put("type", if (s.transport == McpTransportKind.SSE) "sse" else "http")
                    put("url", s.url.orEmpty())
                    if (s.headers.isNotEmpty()) putJsonObject("headers") { s.headers.forEach { (k, v) -> put(k, v) } }
                }
            }
            if (s.disabled) put("disabled", true)
        }

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

        private fun JsonObject.stringMap(key: String): Map<String, String> =
            (this[key] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }?.toMap().orEmpty()
    }
}
