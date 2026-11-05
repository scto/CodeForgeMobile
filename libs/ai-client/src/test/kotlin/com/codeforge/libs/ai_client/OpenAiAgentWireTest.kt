package com.codeforge.libs.ai_client

import com.codeforge.libs.agent.AgentMessage
import com.codeforge.libs.agent.AgentRole
import com.codeforge.libs.agent.ContentPart
import com.codeforge.libs.agent.LlmRequest
import com.codeforge.libs.agent.LlmStreamEvent
import com.codeforge.libs.agent.StopReason
import com.codeforge.libs.agent.ToolSpec
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiAgentWireTest {
    private val schema = buildJsonObject { put("type", "object") }
    private val request = LlmRequest(
        system = "sys",
        messages = listOf(
            AgentMessage(AgentRole.USER, listOf(ContentPart.Text("lies a"))),
            AgentMessage(AgentRole.ASSISTANT, listOf(ContentPart.Text("ok"), ContentPart.ToolUse("c1", "read_file", buildJsonObject { put("path", "a") }))),
            AgentMessage(AgentRole.USER, listOf(ContentPart.ToolResultPart("c1", "inhalt"), ContentPart.ToolResultPart("c2", "kaputt", true))),
        ),
        tools = listOf(ToolSpec("read_file", "liest", schema)),
        maxTokens = 321,
    )

    @Test
    fun `Body mit Tools, tool_calls und tool-Nachrichten`() {
        val body = OpenAiAgentWire.buildBody(request, "gpt-5-mini", useCompletionTokens = true)
        assertEquals("321", body["max_completion_tokens"]!!.jsonPrimitive.content)
        assertFalse(body.containsKey("max_tokens"))
        assertEquals("function", body["tools"]!!.jsonArray.single().jsonObject["type"]!!.jsonPrimitive.content)
        val msgs = body["messages"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("system", "user", "assistant", "tool", "tool"), msgs.map { it["role"]!!.jsonPrimitive.content })
        val call = msgs[2]["tool_calls"]!!.jsonArray.single().jsonObject
        assertEquals("c1", call["id"]!!.jsonPrimitive.content)
        // arguments ist ein JSON-String
        assertEquals("""{"path":"a"}""", call["function"]!!.jsonObject["arguments"]!!.jsonPrimitive.content)
        assertEquals("c1", msgs[3]["tool_call_id"]!!.jsonPrimitive.content)
        assertEquals("Error: kaputt", msgs[4]["content"]!!.jsonPrimitive.content)
        assertTrue(OpenAiAgentWire.buildBody(request, "m", useCompletionTokens = false).containsKey("max_tokens"))
    }

    private fun run(vararg data: String): List<LlmStreamEvent> {
        val a = OpenAiAgentWire.StreamAssembler()
        return data.flatMap { a.accept(RawSseEvent(data = it)) }
    }

    @Test
    fun `Text und zerstueckelte parallele Werkzeugaufrufe`() {
        val out = run(
            """{"choices":[{"delta":{"role":"assistant","content":"Ich "}}]}""",
            """{"choices":[{"delta":{"content":"lese."}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call_a","type":"function","function":{"name":"read_file","arguments":""}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"pa"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":1,"id":"call_b","function":{"name":"list_dir","arguments":"{}"}}]}}]}""",
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"th\":\"a.kt\"}"}}]}}]}""",
            """{"choices":[{"delta":{},"finish_reason":"tool_calls"}]}""",
            """{"choices":[],"usage":{"prompt_tokens":11,"completion_tokens":7}}""",
            "[DONE]",
        )
        assertEquals("Ich lese.", out.filterIsInstance<LlmStreamEvent.TextDelta>().joinToString("") { it.text })
        val calls = out.filterIsInstance<LlmStreamEvent.ToolCallCompleted>()
        assertEquals(listOf("call_a", "call_b"), calls.map { it.id })
        assertEquals("a.kt", calls[0].arguments["path"]!!.jsonPrimitive.content)
        assertTrue(calls[1].arguments.isEmpty())
        assertEquals(LlmStreamEvent.Usage(11, 7), out.filterIsInstance<LlmStreamEvent.Usage>().single())
        assertEquals(LlmStreamEvent.Completed(StopReason.TOOL_USE), out.last())
    }

    @Test
    fun `Ende ohne DONE ueber finish, length und Fehler`() {
        val a = OpenAiAgentWire.StreamAssembler()
        a.accept(RawSseEvent(data = """{"choices":[{"delta":{"content":"x"},"finish_reason":"length"}]}"""))
        assertEquals(listOf(LlmStreamEvent.Completed(StopReason.MAX_TOKENS)), a.finish())
        assertTrue(a.finish().isEmpty())

        assertEquals(
            listOf(LlmStreamEvent.Failed("Rate limit")),
            run("""{"error":{"message":"Rate limit","type":"x"}}"""),
        )
        val broken = run(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"c","function":{"name":"x","arguments":"{kaputt"}}]}}]}""",
            "[DONE]",
        )
        assertTrue(broken.single() is LlmStreamEvent.Failed)
        // Chunk ohne ID bekommt eine erzeugte ID
        val noId = run("""{"choices":[{"delta":{"tool_calls":[{"index":2,"function":{"name":"n","arguments":"{}"}}]},"finish_reason":"stop"}]}""", "[DONE]")
        assertEquals("call_2", noId.filterIsInstance<LlmStreamEvent.ToolCallCompleted>().single().id)
    }
}
