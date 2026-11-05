/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Persistenz des Agenten-Verlaufs (ein Chat je Projekt).
 */
package com.codeforge.libs.agent

import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** JSON-Umwandlung des Verlaufs (versioniert, damit sich das Format später ändern lässt). */
object AgentHistoryCodec {
    private const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true }

    /** Verlauf als JSON-Text. */
    fun encode(messages: List<AgentMessage>): String = buildJsonObject {
        put("version", VERSION)
        put(
            "messages",
            buildJsonArray {
                messages.forEach { m ->
                    add(
                        buildJsonObject {
                            put("role", m.role.name)
                            put(
                                "parts",
                                buildJsonArray {
                                    m.parts.forEach { p ->
                                        add(
                                            buildJsonObject {
                                                when (p) {
                                                    is ContentPart.Text -> { put("type", "text"); put("text", p.text) }
                                                    is ContentPart.ToolUse -> {
                                                        put("type", "tool_use"); put("id", p.id); put("name", p.name); put("arguments", p.arguments)
                                                    }
                                                    is ContentPart.ToolResultPart -> {
                                                        put("type", "tool_result"); put("tool_use_id", p.toolUseId)
                                                        put("content", p.content); put("is_error", p.isError)
                                                    }
                                                }
                                            },
                                        )
                                    }
                                },
                            )
                        },
                    )
                }
            },
        )
    }.toString()

    /** Liest [text]; unbekannte Version oder kaputtes JSON ergibt `null` (Chat beginnt dann leer). */
    fun decode(text: String): List<AgentMessage>? = runCatching {
        val root = json.parseToJsonElement(text).jsonObject
        if (root["version"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() != VERSION) return null
        root["messages"]!!.jsonArray.map { m ->
            val o = m.jsonObject
            AgentMessage(
                AgentRole.valueOf(o["role"]!!.jsonPrimitive.content),
                o["parts"]!!.jsonArray.map { p -> part(p.jsonObject) },
            )
        }
    }.getOrNull()

    private fun part(o: JsonObject): ContentPart = when (o["type"]!!.jsonPrimitive.content) {
        "text" -> ContentPart.Text(o["text"]!!.jsonPrimitive.content)
        "tool_use" -> ContentPart.ToolUse(o["id"]!!.jsonPrimitive.content, o["name"]!!.jsonPrimitive.content, o["arguments"]!!.jsonObject)
        "tool_result" -> ContentPart.ToolResultPart(
            o["tool_use_id"]!!.jsonPrimitive.content,
            o["content"]!!.jsonPrimitive.content,
            o["is_error"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
        else -> error("unbekannter Teil")
    }
}

/**
 * Dateibasierter Speicher: eine Datei je Projekt unter [dir], benannt nach dem Hash des Projektpfads.
 * Vor dem Speichern werden alte Werkzeugergebnisse gekürzt, damit die Datei klein bleibt.
 */
class AgentHistoryStore(
    private val dir: File,
    private val compactor: HistoryCompactor = HistoryCompactor(triggerChars = 200_000, targetChars = 100_000),
) {
    private fun file(projectKey: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(projectKey.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.json")
    }

    /** Gespeicherter Verlauf des Projekts oder leere Liste. */
    fun load(projectKey: String): List<AgentMessage> =
        file(projectKey).takeIf { it.isFile }?.let { runCatching { AgentHistoryCodec.decode(it.readText()) }.getOrNull() }.orEmpty()

    /** Speichert [messages] atomar; Fehler (z. B. voller Speicher) werden verschluckt – der Chat läuft weiter. */
    fun save(projectKey: String, messages: List<AgentMessage>) {
        runCatching {
            dir.mkdirs()
            val target = file(projectKey)
            val tmp = File(dir, target.name + ".tmp")
            tmp.writeText(AgentHistoryCodec.encode(compactor.compact(messages)))
            if (!tmp.renameTo(target)) {
                target.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    /** Löscht den Verlauf des Projekts. */
    fun clear(projectKey: String) {
        file(projectKey).delete()
    }
}
