// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class AiStreamFixturesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testAnthropicFixtureParsing() {
        val stream = """
            event: message_start
            data: {"type": "message_start", "message": {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-3-7-sonnet", "usage": {"input_tokens": 25, "output_tokens": 1}}}

            event: content_block_start
            data: {"type": "content_block_start", "index": 0, "content_block": {"type": "text", "text": ""}}

            event: content_block_delta
            data: {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": "Hello"}}

            event: content_block_delta
            data: {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": " world!"}}

            event: message_delta
            data: {"type": "message_delta", "delta": {"stop_reason": "end_turn"}, "usage": {"output_tokens": 15}}

            event: message_stop
            data: {"type": "message_stop"}

        """.trimIndent()

        val parser = SseEventParser()
        val textPieces = mutableListOf<String>()
        var inputTokens = 0
        var outputTokens = 0

        for (line in stream.lines()) {
            val event = parser.parseLine(line) ?: continue
            val data = event.data.trim()
            if (data.isNotEmpty()) {
                val root = json.parseToJsonElement(data).jsonObject
                val type = root["type"]?.jsonPrimitive?.content ?: event.event
                when (type) {
                    "message_start" -> {
                        inputTokens = root["message"]?.jsonObject?.get("usage")?.jsonObject?.get("input_tokens")?.jsonPrimitive?.intOrNull ?: 0
                    }
                    "content_block_delta" -> {
                        val text = root["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.content
                        if (text != null) textPieces.add(text)
                    }
                    "message_delta" -> {
                        outputTokens = root["usage"]?.jsonObject?.get("output_tokens")?.jsonPrimitive?.intOrNull ?: 0
                    }
                }
            }
        }

        assertEquals(25, inputTokens)
        assertEquals(15, outputTokens)
        assertEquals("Hello world!", textPieces.joinToString(""))
    }

    @Test
    fun testOpenAiFixtureParsing() {
        val stream = """
            data: {"id":"chatcmpl-1","choices":[{"index":0,"delta":{"role":"assistant","content":""}}]}

            data: {"id":"chatcmpl-1","choices":[{"index":0,"delta":{"content":"Hi"}}]}

            data: {"id":"chatcmpl-1","choices":[{"index":0,"delta":{"content":" there"}}]}

            data: {"id":"chatcmpl-1","choices":[],"usage":{"prompt_tokens":12,"completion_tokens":4,"total_tokens":16}}

            data: [DONE]

        """.trimIndent()

        val parser = SseEventParser()
        val textPieces = mutableListOf<String>()
        var totalTokens = 0

        for (line in stream.lines()) {
            val event = parser.parseLine(line) ?: continue
            val data = event.data.trim()
            if (data == "[DONE]") break
            if (data.isNotEmpty()) {
                val root = json.parseToJsonElement(data).jsonObject
                root["usage"]?.jsonObject?.let {
                    totalTokens = it["total_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                }
                val choices = root["choices"]?.jsonArray
                if (choices != null && choices.isNotEmpty()) {
                    val content = choices[0].jsonObject["delta"]?.jsonObject?.get("content")?.jsonPrimitive?.content
                    if (content != null) textPieces.add(content)
                }
            }
        }

        assertEquals(16, totalTokens)
        assertEquals("Hi there", textPieces.joinToString(""))
    }

    @Test
    fun testGeminiFixtureParsing() {
        val stream = """
            data: {"candidates": [{"content": {"parts": [{"text": "Gemini"}], "role": "model"}}], "usageMetadata": {"promptTokenCount": 5, "candidatesTokenCount": 1, "totalTokenCount": 6}}

            data: {"candidates": [{"content": {"parts": [{"text": " response"}], "role": "model"}}], "usageMetadata": {"promptTokenCount": 5, "candidatesTokenCount": 2, "totalTokenCount": 7}}

        """.trimIndent()

        val parser = SseEventParser()
        val textPieces = mutableListOf<String>()
        var totalTokens = 0

        for (line in stream.lines()) {
            val event = parser.parseLine(line) ?: continue
            val data = event.data.trim()
            if (data.isNotEmpty()) {
                val root = json.parseToJsonElement(data).jsonObject
                root["usageMetadata"]?.jsonObject?.let {
                    totalTokens = it["totalTokenCount"]?.jsonPrimitive?.intOrNull ?: 0
                }
                val candidates = root["candidates"]?.jsonArray
                if (candidates != null && candidates.isNotEmpty()) {
                    val parts = candidates[0].jsonObject["content"]?.jsonObject?.get("parts")?.jsonArray
                    parts?.forEach { p ->
                        val text = p.jsonObject["text"]?.jsonPrimitive?.content
                        if (text != null) textPieces.add(text)
                    }
                }
            }
        }

        assertEquals(7, totalTokens)
        assertEquals("Gemini response", textPieces.joinToString(""))
    }
}
