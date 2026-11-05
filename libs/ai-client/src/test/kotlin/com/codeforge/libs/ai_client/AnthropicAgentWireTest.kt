package com.codeforge.libs.ai_client

import com.codeforge.libs.agent.AgentMessage
import com.codeforge.libs.agent.AgentRole
import com.codeforge.libs.agent.ContentPart
import com.codeforge.libs.agent.LlmRequest
import com.codeforge.libs.agent.LlmStreamEvent
import com.codeforge.libs.agent.StopReason
import com.codeforge.libs.agent.ToolSpec
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicAgentWireTest {
    private val schema = buildJsonObject { put("type", "object") }

    @Test
    fun `Body enthaelt Werkzeuge, Werkzeugaufrufe und Ergebnisse im Anthropic-Format`() {
        val request = LlmRequest(
            system = "sys",
            messages = listOf(
                AgentMessage(AgentRole.USER, listOf(ContentPart.Text("lies a"))),
                AgentMessage(
                    AgentRole.ASSISTANT,
                    listOf(ContentPart.Text("  "), ContentPart.ToolUse("t1", "read_file", buildJsonObject { put("path", "a") })),
                ),
                AgentMessage(AgentRole.USER, listOf(ContentPart.ToolResultPart("t1", "inhalt", isError = true))),
            ),
            tools = listOf(ToolSpec("read_file", "liest", schema)),
            maxTokens = 777,
        )
        val body = AnthropicAgentWire.buildBody(request, "claude-sonnet-5-5")

        assertEquals("claude-sonnet-5-5", body["model"]!!.jsonPrimitive.content)
        assertEquals("777", body["max_tokens"]!!.jsonPrimitive.content)
        assertEquals("true", body["stream"]!!.jsonPrimitive.content)
        assertEquals("sys", body["system"]!!.jsonPrimitive.content)
        val tool = body["tools"]!!.jsonArray.single().jsonObject
        assertEquals("read_file", tool["name"]!!.jsonPrimitive.content)
        assertTrue(tool.containsKey("input_schema"))

        val msgs = body["messages"]!!.jsonArray
        assertEquals(3, msgs.size)
        // leerer Textblock entfällt, tool_use bleibt
        val assistant = msgs[1].jsonObject["content"]!!.jsonArray
        assertEquals(1, assistant.size)
        assertEquals("tool_use", assistant[0].jsonObject["type"]!!.jsonPrimitive.content)
        val result = msgs[2].jsonObject["content"]!!.jsonArray.single().jsonObject
        assertEquals("tool_result", result["type"]!!.jsonPrimitive.content)
        assertEquals("t1", result["tool_use_id"]!!.jsonPrimitive.content)
        assertEquals("true", result["is_error"]!!.jsonPrimitive.content)
    }

    @Test
    fun `Body ohne Werkzeuge und System laesst die Felder weg`() {
        val body = AnthropicAgentWire.buildBody(LlmRequest("", listOf(AgentMessage(AgentRole.USER, listOf(ContentPart.Text("x")))), emptyList()), "m")
        assertFalse(body.containsKey("tools"))
        assertFalse(body.containsKey("system"))
        assertTrue(body["messages"] is JsonArray)
    }

    private fun events(vararg frames: Pair<String, String>): List<LlmStreamEvent> {
        val a = AnthropicAgentWire.StreamAssembler()
        return frames.flatMap { (type, data) -> a.accept(RawSseEvent(event = type, data = data)) }
    }

    @Test
    fun `Text und Werkzeugaufruf mit zerstueckelten Argumenten werden zusammengesetzt`() {
        val out = events(
            "message_start" to """{"type":"message_start","message":{"usage":{"input_tokens":25,"output_tokens":1}}}""",
            "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Ich "}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"lese."}}""",
            "content_block_stop" to """{"type":"content_block_stop","index":0}""",
            "content_block_start" to """{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"toolu_1","name":"read_file","input":{}}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"pa"}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"th\": \"a.kt\"}"}}""",
            "content_block_stop" to """{"type":"content_block_stop","index":1}""",
            "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"tool_use"},"usage":{"output_tokens":42}}""",
            "message_stop" to """{"type":"message_stop"}""",
        )
        assertEquals(listOf("Ich ", "lese."), out.filterIsInstance<LlmStreamEvent.TextDelta>().map { it.text })
        val call = out.filterIsInstance<LlmStreamEvent.ToolCallCompleted>().single()
        assertEquals("toolu_1", call.id)
        assertEquals("read_file", call.name)
        assertEquals("a.kt", call.arguments["path"]!!.jsonPrimitive.content)
        assertEquals(LlmStreamEvent.Usage(25, 42), out.filterIsInstance<LlmStreamEvent.Usage>().last())
        assertEquals(LlmStreamEvent.Completed(StopReason.TOOL_USE), out.last())
    }

    @Test
    fun `Werkzeug ohne Argumente ergibt leeres Objekt, End-Gruende werden abgebildet`() {
        val out = events(
            "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"t","name":"list_dir","input":{}}}""",
            "content_block_stop" to """{"type":"content_block_stop","index":0}""",
            "message_delta" to """{"type":"message_delta","delta":{"stop_reason":"max_tokens"},"usage":{"output_tokens":1}}""",
            "message_stop" to """{"type":"message_stop"}""",
        )
        assertTrue(out.filterIsInstance<LlmStreamEvent.ToolCallCompleted>().single().arguments.isEmpty())
        assertEquals(LlmStreamEvent.Completed(StopReason.MAX_TOKENS), out.last())
    }

    @Test
    fun `Fehlerereignis, kaputte Argumente und Rauschen`() {
        assertEquals(
            listOf(LlmStreamEvent.Failed("Overloaded")),
            events("error" to """{"type":"error","error":{"type":"overloaded_error","message":"Overloaded"}}"""),
        )
        val broken = events(
            "content_block_start" to """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"t","name":"x","input":{}}}""",
            "content_block_delta" to """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{kaputt"}}""",
            "content_block_stop" to """{"type":"content_block_stop","index":0}""",
        )
        assertTrue(broken.single() is LlmStreamEvent.Failed)
        assertTrue(events("ping" to """{"type":"ping"}""", "x" to "kein json").isEmpty())
    }
}
