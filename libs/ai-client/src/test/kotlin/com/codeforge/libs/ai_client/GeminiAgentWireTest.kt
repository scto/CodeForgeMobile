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

class GeminiAgentWireTest {
    private val schema = buildJsonObject { put("type", "object") }

    @Test
    fun `Body mit systemInstruction, Funktionsdeklarationen, Aufrufen mit Signatur und Antworten mit Funktionsnamen`() {
        val request = LlmRequest(
            system = "sys",
            messages = listOf(
                AgentMessage(AgentRole.USER, listOf(ContentPart.Text("x"))),
                AgentMessage(AgentRole.ASSISTANT, listOf(ContentPart.ToolUse("gm_1~SIG", "read_file", buildJsonObject { put("path", "a") }))),
                AgentMessage(AgentRole.USER, listOf(ContentPart.ToolResultPart("gm_1~SIG", "inhalt"), ContentPart.Text(" "))),
                AgentMessage(AgentRole.ASSISTANT, listOf(ContentPart.ToolUse("gm_2", "list_dir", buildJsonObject { }))),
                AgentMessage(AgentRole.USER, listOf(ContentPart.ToolResultPart("gm_2", "fehler", true))),
            ),
            tools = listOf(ToolSpec("read_file", "liest", schema)),
            maxTokens = 99,
        )
        val body = GeminiAgentWire.buildBody(request)
        assertEquals("sys", body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("99", body["generationConfig"]!!.jsonObject["maxOutputTokens"]!!.jsonPrimitive.content)
        assertEquals("read_file", body["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray[0].jsonObject["name"]!!.jsonPrimitive.content)

        val contents = body["contents"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "model", "user", "model", "user"), contents.map { it["role"]!!.jsonPrimitive.content })
        val callPart = contents[1]["parts"]!!.jsonArray[0].jsonObject
        assertEquals("read_file", callPart["functionCall"]!!.jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("SIG", callPart["thoughtSignature"]!!.jsonPrimitive.content)
        assertFalse(contents[3]["parts"]!!.jsonArray[0].jsonObject.containsKey("thoughtSignature"))
        val resp = contents[2]["parts"]!!.jsonArray.single().jsonObject["functionResponse"]!!.jsonObject
        assertEquals("read_file", resp["name"]!!.jsonPrimitive.content)
        assertEquals("inhalt", resp["response"]!!.jsonObject["output"]!!.jsonPrimitive.content)
        val err = contents[4]["parts"]!!.jsonArray.single().jsonObject["functionResponse"]!!.jsonObject
        assertEquals("list_dir", err["name"]!!.jsonPrimitive.content)
        assertEquals("fehler", err["response"]!!.jsonObject["error"]!!.jsonPrimitive.content)
    }

    private fun run(vararg data: String): List<LlmStreamEvent> {
        val a = GeminiAgentWire.StreamAssembler()
        return data.flatMap { a.accept(RawSseEvent(data = it)) } + a.finish()
    }

    @Test
    fun `Text, Gedanken werden uebersprungen, Funktionsaufruf mit Signatur, Usage und Ende`() {
        val out = run(
            """{"candidates":[{"content":{"role":"model","parts":[{"text":"denke","thought":true},{"text":"Ich lese."}]}}]}""",
            """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"read_file","args":{"path":"a.kt"}},"thoughtSignature":"SIG"},{"functionCall":{"name":"list_dir","args":{}}}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":10,"candidatesTokenCount":4}}""",
        )
        assertEquals(listOf("Ich lese."), out.filterIsInstance<LlmStreamEvent.TextDelta>().map { it.text })
        val calls = out.filterIsInstance<LlmStreamEvent.ToolCallCompleted>()
        assertEquals(listOf("gm_1~SIG", "gm_2"), calls.map { it.id })
        assertEquals("a.kt", calls[0].arguments["path"]!!.jsonPrimitive.content)
        assertEquals(LlmStreamEvent.Usage(10, 4), out.filterIsInstance<LlmStreamEvent.Usage>().single())
        assertEquals(LlmStreamEvent.Completed(StopReason.TOOL_USE), out.last())
    }

    @Test
    fun `Endgruende und Fehler`() {
        assertEquals(LlmStreamEvent.Completed(StopReason.END_TURN), run("""{"candidates":[{"content":{"parts":[{"text":"x"}]},"finishReason":"STOP"}]}""").last())
        assertEquals(LlmStreamEvent.Completed(StopReason.MAX_TOKENS), run("""{"candidates":[{"content":{"parts":[{"text":"x"}]},"finishReason":"MAX_TOKENS"}]}""").last())
        assertEquals(LlmStreamEvent.Completed(StopReason.OTHER), run("""{"candidates":[{"finishReason":"SAFETY"}]}""").last())
        assertEquals(listOf(LlmStreamEvent.Failed("API key not valid")), run("""{"error":{"code":400,"message":"API key not valid"}}"""))
        assertTrue(run("kein json").single() is LlmStreamEvent.Completed)
    }
}
