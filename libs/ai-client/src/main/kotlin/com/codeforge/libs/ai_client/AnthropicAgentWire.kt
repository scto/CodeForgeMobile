// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.libs.agent.AgentMessage
import com.codeforge.libs.agent.AgentRole
import com.codeforge.libs.agent.ContentPart
import com.codeforge.libs.agent.LlmRequest
import com.codeforge.libs.agent.LlmStreamEvent
import com.codeforge.libs.agent.StopReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Übersetzt zwischen dem neutralen Agentenmodell und dem Wire-Format der Anthropic-Messages-API (ohne Netzwerk, testbar). */
internal object AnthropicAgentWire {

    private val json = Json { ignoreUnknownKeys = true }

    /** Baut den JSON-Body für `POST /v1/messages` mit Streaming und Werkzeugen. */
    fun buildBody(request: LlmRequest, model: String): JsonObject = buildJsonObject {
        put("model", model)
        put("max_tokens", request.maxTokens)
        put("stream", true)
        if (request.system.isNotBlank()) put("system", request.system)
        put("messages", JsonArray(request.messages.mapNotNull(::message)))
        if (request.tools.isNotEmpty()) {
            put(
                "tools",
                buildJsonArray {
                    request.tools.forEach { t ->
                        add(
                            buildJsonObject {
                                put("name", t.name)
                                put("description", t.description)
                                put("input_schema", t.parameters)
                            },
                        )
                    }
                },
            )
        }
    }

    private fun message(m: AgentMessage): JsonObject? {
        val blocks = m.parts.mapNotNull { part ->
            when (part) {
                // Anthropic lehnt leere Textblöcke ab.
                is ContentPart.Text -> if (part.text.isBlank()) null else buildJsonObject { put("type", "text"); put("text", part.text) }
                is ContentPart.ToolUse -> buildJsonObject {
                    put("type", "tool_use"); put("id", part.id); put("name", part.name); put("input", part.arguments)
                }
                is ContentPart.ToolResultPart -> buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", part.toolUseId)
                    put("content", part.content)
                    if (part.isError) put("is_error", true)
                }
            }
        }
        if (blocks.isEmpty()) return null
        return buildJsonObject {
            put("role", if (m.role == AgentRole.ASSISTANT) "assistant" else "user")
            put("content", JsonArray(blocks))
        }
    }

    /** Setzt den SSE-Strom einer Antwort zu [LlmStreamEvent]s zusammen (Werkzeugargumente kommen als Teilstücke). */
    class StreamAssembler : SseAssembler {
        private class Block(val id: String, val name: String, val args: StringBuilder = StringBuilder())

        private val blocks = HashMap<Int, Block>()
        private var inputTokens = 0
        private var outputTokens = 0
        private var stop = StopReason.END_TURN

        /** Verarbeitet ein SSE-Ereignis und liefert die daraus entstehenden Ereignisse (oft keines). */
        override fun accept(event: RawSseEvent): List<LlmStreamEvent> {
            if (event.data.isBlank()) return emptyList()
            val root = try {
                json.parseToJsonElement(event.data).jsonObject
            } catch (_: Exception) {
                return emptyList()
            }
            return when (root["type"]?.jsonPrimitive?.contentOrNull ?: event.event) {
                "message_start" -> {
                    root["message"]?.jsonObject?.get("usage")?.jsonObject?.get("input_tokens")?.jsonPrimitive?.intOrNull
                        ?.let { inputTokens = it; listOf(LlmStreamEvent.Usage(inputTokens, outputTokens)) } ?: emptyList()
                }
                "content_block_start" -> {
                    val block = root["content_block"]?.jsonObject
                    if (block?.get("type")?.jsonPrimitive?.contentOrNull == "tool_use") {
                        blocks[index(root)] = Block(
                            block["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            block["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        )
                    }
                    emptyList()
                }
                "content_block_delta" -> {
                    val delta = root["delta"]?.jsonObject
                    when (delta?.get("type")?.jsonPrimitive?.contentOrNull) {
                        "text_delta" -> delta["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }
                            ?.let { listOf(LlmStreamEvent.TextDelta(it)) } ?: emptyList()
                        "input_json_delta" -> {
                            delta["partial_json"]?.jsonPrimitive?.contentOrNull?.let { blocks[index(root)]?.args?.append(it) }
                            emptyList()
                        }
                        else -> emptyList()
                    }
                }
                "content_block_stop" -> {
                    val b = blocks.remove(index(root)) ?: return emptyList()
                    val args: JsonElement = if (b.args.isBlank()) JsonObject(emptyMap()) else try {
                        json.parseToJsonElement(b.args.toString())
                    } catch (_: Exception) {
                        return listOf(LlmStreamEvent.Failed("Ungültige Werkzeugargumente für '${b.name}'"))
                    }
                    listOf(LlmStreamEvent.ToolCallCompleted(b.id, b.name, args as? JsonObject ?: JsonObject(emptyMap())))
                }
                "message_delta" -> {
                    root["delta"]?.jsonObject?.get("stop_reason")?.jsonPrimitive?.contentOrNull?.let { stop = stopReason(it) }
                    root["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.intOrNull
                        ?.let { outputTokens = it; listOf(LlmStreamEvent.Usage(inputTokens, outputTokens)) } ?: emptyList()
                }
                "message_stop" -> listOf(LlmStreamEvent.Completed(stop))
                "error" -> listOf(
                    LlmStreamEvent.Failed(
                        root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: event.data,
                    ),
                )
                else -> emptyList()
            }
        }

        private fun index(root: JsonObject): Int = (root["index"] as? JsonPrimitive)?.intOrNull ?: 0

        private fun stopReason(raw: String) = when (raw) {
            "end_turn" -> StopReason.END_TURN
            "tool_use" -> StopReason.TOOL_USE
            "max_tokens" -> StopReason.MAX_TOKENS
            else -> StopReason.OTHER
        }
    }
}
