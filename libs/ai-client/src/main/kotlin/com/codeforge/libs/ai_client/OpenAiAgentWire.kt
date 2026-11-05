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
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Wire-Format der OpenAI-kompatiblen Chat-Completions-API mit Werkzeugaufrufen (ohne Netzwerk, testbar). */
internal object OpenAiAgentWire {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Body für `POST …/chat/completions` mit Streaming.
     *
     * @param useCompletionTokens `max_completion_tokens` statt `max_tokens` (verlangen die neueren OpenAI-Modelle;
     *  lokale/kompatible Server kennen meist nur `max_tokens`).
     */
    fun buildBody(request: LlmRequest, model: String, useCompletionTokens: Boolean): JsonObject = buildJsonObject {
        put("model", model)
        put("stream", true)
        put("stream_options", buildJsonObject { put("include_usage", true) })
        put(if (useCompletionTokens) "max_completion_tokens" else "max_tokens", request.maxTokens)
        put("messages", JsonArray(messages(request)))
        if (request.tools.isNotEmpty()) {
            put(
                "tools",
                buildJsonArray {
                    request.tools.forEach { t ->
                        add(
                            buildJsonObject {
                                put("type", "function")
                                put(
                                    "function",
                                    buildJsonObject {
                                        put("name", t.name)
                                        put("description", t.description)
                                        put("parameters", t.parameters)
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
    }

    private fun messages(request: LlmRequest): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        if (request.system.isNotBlank()) out += buildJsonObject { put("role", "system"); put("content", request.system) }
        for (m in request.messages) {
            val text = m.parts.filterIsInstance<ContentPart.Text>().joinToString("") { it.text }
            when (m.role) {
                AgentRole.USER -> {
                    // Werkzeugergebnisse sind in OpenAI eigene Nachrichten (role=tool), vor dem eventuellen Nutzertext.
                    m.parts.filterIsInstance<ContentPart.ToolResultPart>().forEach { r ->
                        out += buildJsonObject {
                            put("role", "tool")
                            put("tool_call_id", r.toolUseId)
                            put("content", if (r.isError) "Error: ${r.content}" else r.content)
                        }
                    }
                    if (text.isNotBlank()) out += buildJsonObject { put("role", "user"); put("content", text) }
                }
                AgentRole.ASSISTANT -> {
                    val calls = m.parts.filterIsInstance<ContentPart.ToolUse>()
                    if (text.isBlank() && calls.isEmpty()) continue
                    out += buildJsonObject {
                        put("role", "assistant")
                        if (text.isNotBlank()) put("content", text)
                        if (calls.isNotEmpty()) {
                            put(
                                "tool_calls",
                                buildJsonArray {
                                    calls.forEach { c ->
                                        add(
                                            buildJsonObject {
                                                put("id", c.id)
                                                put("type", "function")
                                                put(
                                                    "function",
                                                    buildJsonObject { put("name", c.name); put("arguments", c.arguments.toString()) },
                                                )
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    /** Setzt die `data:`-Chunks zusammen; Werkzeugaufrufe kommen in Teilen je `index` und werden am Ende gemeinsam geliefert. */
    class StreamAssembler : SseAssembler {
        private class Call(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())

        private val calls = java.util.TreeMap<Int, Call>()
        private var finish: String? = null
        private var flushed = false
        private var completed = false

        override fun accept(event: RawSseEvent): List<LlmStreamEvent> {
            val data = event.data.trim()
            if (data.isEmpty()) return emptyList()
            if (data == "[DONE]") return finish()
            val root = try {
                json.parseToJsonElement(data).jsonObject
            } catch (_: Exception) {
                return emptyList()
            }
            root["error"]?.let { err ->
                val msg = (err as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: err.toString()
                return listOf(LlmStreamEvent.Failed(msg))
            }
            val out = ArrayList<LlmStreamEvent>()
            val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            val delta = choice?.get("delta")?.jsonObject
            delta?.get("content")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { out += LlmStreamEvent.TextDelta(it) }
            delta?.get("tool_calls")?.jsonArray?.forEach { tc ->
                val o = tc.jsonObject
                val index = o["index"]?.jsonPrimitive?.intOrNull ?: 0
                val call = calls.getOrPut(index) { Call() }
                o["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { call.id = it }
                val fn = o["function"]?.jsonObject
                fn?.get("name")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotEmpty() }?.let { call.name = it }
                fn?.get("arguments")?.jsonPrimitive?.contentOrNull?.let { call.args.append(it) }
            }
            choice?.get("finish_reason")?.jsonPrimitive?.contentOrNull?.let { finish = it }
            root["usage"]?.takeIf { it is JsonObject }?.jsonObject?.let { u ->
                val inT = u["prompt_tokens"]?.jsonPrimitive?.intOrNull
                val outT = u["completion_tokens"]?.jsonPrimitive?.intOrNull
                if (inT != null || outT != null) out += LlmStreamEvent.Usage(inT ?: 0, outT ?: 0)
            }
            return out
        }

        /** Liefert gesammelte Werkzeugaufrufe und das Ende; mehrfach aufrufbar. */
        override fun finish(): List<LlmStreamEvent> {
            if (completed) return emptyList()
            val out = ArrayList<LlmStreamEvent>()
            if (!flushed) {
                flushed = true
                for ((index, c) in calls) {
                    if (c.name.isEmpty()) continue
                    val args: JsonObject = if (c.args.isBlank()) JsonObject(emptyMap()) else try {
                        json.parseToJsonElement(c.args.toString()) as? JsonObject ?: JsonObject(emptyMap())
                    } catch (_: Exception) {
                        completed = true
                        return out + LlmStreamEvent.Failed("Ungültige Werkzeugargumente für '${c.name}'")
                    }
                    out += LlmStreamEvent.ToolCallCompleted(c.id.ifEmpty { "call_$index" }, c.name, args)
                }
            }
            completed = true
            out += LlmStreamEvent.Completed(
                when {
                    calls.isNotEmpty() && finish != "length" -> StopReason.TOOL_USE
                    finish == "length" -> StopReason.MAX_TOKENS
                    finish == "stop" || finish == null -> StopReason.END_TURN
                    else -> StopReason.OTHER
                },
            )
            return out
        }
    }
}
