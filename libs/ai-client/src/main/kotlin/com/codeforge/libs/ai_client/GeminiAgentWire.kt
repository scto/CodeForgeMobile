// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.libs.agent.AgentRole
import com.codeforge.libs.agent.ContentPart
import com.codeforge.libs.agent.LlmRequest
import com.codeforge.libs.agent.LlmStreamEvent
import com.codeforge.libs.agent.StopReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Wire-Format der Gemini-API (`streamGenerateContent`) mit Funktionsaufrufen (ohne Netzwerk, testbar).
 *
 * Gemini vergibt keine Aufruf-IDs und verlangt bei Funktionsantworten den Funktionsnamen. Deshalb vergibt die
 * Übersetzung IDs der Form `gm_<n>`; eine `thoughtSignature` des Modells (Gemini 3 verlangt sie beim Zurücksenden
 * von Funktionsaufrufen) wird als `gm_<n>~<signatur>` in der ID mitgeführt und beim Senden wieder abgespalten.
 */
internal object GeminiAgentWire {
    private val json = Json { ignoreUnknownKeys = true }
    private const val SIGNATURE_SEPARATOR = '~'

    /** Body für `POST …/models/<modell>:streamGenerateContent?alt=sse`. */
    fun buildBody(request: LlmRequest): JsonObject {
        val nameById = request.messages.flatMap { it.parts }.filterIsInstance<ContentPart.ToolUse>().associate { it.id to it.name }
        return buildJsonObject {
            if (request.system.isNotBlank()) {
                put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", request.system) }) }) })
            }
            put("contents", JsonArray(request.messages.mapNotNull { content(it.role, it.parts, nameById) }))
            if (request.tools.isNotEmpty()) {
                put(
                    "tools",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put(
                                    "functionDeclarations",
                                    buildJsonArray {
                                        request.tools.forEach { t ->
                                            add(
                                                buildJsonObject {
                                                    put("name", t.name)
                                                    put("description", t.description)
                                                    put("parameters", t.parameters)
                                                },
                                            )
                                        }
                                    },
                                )
                            },
                        )
                    },
                )
            }
            put("generationConfig", buildJsonObject { put("maxOutputTokens", request.maxTokens) })
        }
    }

    private fun content(role: AgentRole, parts: List<ContentPart>, nameById: Map<String, String>): JsonObject? {
        val out = parts.mapNotNull { p ->
            when (p) {
                is ContentPart.Text -> if (p.text.isBlank()) null else buildJsonObject { put("text", p.text) }
                is ContentPart.ToolUse -> buildJsonObject {
                    put("functionCall", buildJsonObject { put("name", p.name); put("args", p.arguments) })
                    p.id.substringAfter(SIGNATURE_SEPARATOR, "").takeIf { it.isNotEmpty() }?.let { put("thoughtSignature", it) }
                }
                is ContentPart.ToolResultPart -> buildJsonObject {
                    put(
                        "functionResponse",
                        buildJsonObject {
                            put("name", nameById[p.toolUseId] ?: "unknown")
                            put("response", buildJsonObject { put(if (p.isError) "error" else "output", p.content) })
                        },
                    )
                }
            }
        }
        if (out.isEmpty()) return null
        return buildJsonObject {
            put("role", if (role == AgentRole.ASSISTANT) "model" else "user")
            put("parts", JsonArray(out))
        }
    }

    /** Setzt die Antwort-Chunks zusammen; Funktionsaufrufe kommen vollständig in einem Chunk. */
    class StreamAssembler : SseAssembler {
        private var nextCall = 1
        private var sawCall = false
        private var finishReason: String? = null
        private var completed = false

        override fun accept(event: RawSseEvent): List<LlmStreamEvent> {
            val data = event.data.trim()
            if (data.isEmpty()) return emptyList()
            val root = try {
                json.parseToJsonElement(data).jsonObject
            } catch (_: Exception) {
                return emptyList()
            }
            root["error"]?.takeIf { it is JsonObject }?.jsonObject?.let { err ->
                completed = true
                return listOf(LlmStreamEvent.Failed(err["message"]?.jsonPrimitive?.contentOrNull ?: err.toString()))
            }
            val out = ArrayList<LlmStreamEvent>()
            val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            candidate?.get("content")?.jsonObject?.get("parts")?.jsonArray?.forEach { part ->
                val o = part.jsonObject
                // Gedankenzusammenfassungen (thought=true) gehören nicht in die Antwort.
                if (o["thought"]?.jsonPrimitive?.booleanOrNull == true) return@forEach
                o["text"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { out += LlmStreamEvent.TextDelta(it) }
                o["functionCall"]?.jsonObject?.let { fc ->
                    sawCall = true
                    val signature = o["thoughtSignature"]?.jsonPrimitive?.contentOrNull
                    val id = "gm_${nextCall++}" + (signature?.let { "$SIGNATURE_SEPARATOR$it" } ?: "")
                    out += LlmStreamEvent.ToolCallCompleted(
                        id,
                        fc["name"]?.jsonPrimitive?.content.orEmpty(),
                        fc["args"] as? JsonObject ?: JsonObject(emptyMap()),
                    )
                }
            }
            candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull?.let { finishReason = it }
            root["usageMetadata"]?.takeIf { it is JsonObject }?.jsonObject?.let { u ->
                val inT = u["promptTokenCount"]?.jsonPrimitive?.intOrNull
                val outT = u["candidatesTokenCount"]?.jsonPrimitive?.intOrNull
                if (inT != null || outT != null) out += LlmStreamEvent.Usage(inT ?: 0, outT ?: 0)
            }
            return out
        }

        override fun finish(): List<LlmStreamEvent> {
            if (completed) return emptyList()
            completed = true
            return listOf(
                LlmStreamEvent.Completed(
                    when {
                        finishReason == "MAX_TOKENS" -> StopReason.MAX_TOKENS
                        sawCall -> StopReason.TOOL_USE
                        finishReason == null || finishReason == "STOP" -> StopReason.END_TURN
                        else -> StopReason.OTHER
                    },
                ),
            )
        }
    }
}
