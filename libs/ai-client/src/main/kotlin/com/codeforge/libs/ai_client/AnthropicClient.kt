// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.core.domain.ai.AiChatMessage
import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.domain.ai.AiEvent
import com.codeforge.core.domain.ai.AiRole
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class AnthropicClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    fun stream(messages: List<AiChatMessage>, options: AiChatOptions): Flow<AiEvent> = flow {
        val baseUrl = (options.baseUrl ?: "https://api.anthropic.com").trimEnd('/')
        val url = URL("$baseUrl/v1/messages")

        val systemMessages = messages.filter { it.role == AiRole.SYSTEM }.joinToString("\n\n") { it.content }
        val dialogMessages = messages.filter { it.role != AiRole.SYSTEM }

        val body = buildJsonObject {
            put("model", options.model)
            if (systemMessages.isNotBlank()) {
                put("system", systemMessages)
            }
            put(
                "messages",
                buildJsonArray {
                    for (msg in dialogMessages) {
                        add(
                            buildJsonObject {
                                put("role", if (msg.role == AiRole.ASSISTANT) "assistant" else "user")
                                put("content", msg.content)
                            }
                        )
                    }
                }
            )
            put("max_tokens", options.maxTokens ?: 4096)
            options.temperature?.let { put("temperature", it.toDouble()) }
            put("stream", true)
        }.toString()

        var attempts = 0
        val maxAttempts = 3
        var delayMs = 1000L
        var connection: HttpURLConnection? = null

        while (attempts < maxAttempts) {
            attempts++
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("x-api-key", options.apiKey)
                    setRequestProperty("anthropic-version", "2023-06-01")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "text/event-stream")
                }

                connection.outputStream.use { os ->
                    OutputStreamWriter(os, Charsets.UTF_8).use { it.write(body) }
                }

                val responseCode = connection.responseCode
                if (responseCode == 429 || responseCode in 500..599) {
                    if (attempts < maxAttempts) {
                        connection.disconnect()
                        delay(delayMs)
                        delayMs *= 2
                        continue
                    }
                }

                if (responseCode !in 200..299) {
                    val errBody = try {
                        connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    } catch (_: Exception) {
                        ""
                    }
                    val msg = when (responseCode) {
                        401, 403 -> Res.string(R.string.ai_error_invalid_key)
                        429 -> Res.string(R.string.ai_error_quota_exceeded)
                        else -> Res.string(R.string.ai_error_generic, "HTTP $responseCode: $errBody")
                    }
                    emit(AiEvent.Error(msg))
                    emit(AiEvent.Done)
                    return@flow
                }

                var promptTokens = 0
                var completionTokens = 0
                val parser = SseEventParser()

                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val event = parser.parseLine(line!!) ?: continue
                        if (event.event == "error") {
                            emit(AiEvent.Error(event.data))
                            break
                        }

                        if (event.data.isNotBlank()) {
                            try {
                                val root = json.parseToJsonElement(event.data).jsonObject
                                val type = root["type"]?.jsonPrimitive?.content ?: event.event

                                when (type) {
                                    "message_start" -> {
                                        val usage = root["message"]?.jsonObject?.get("usage")?.jsonObject
                                        val inputTokens = usage?.get("input_tokens")?.jsonPrimitive?.intOrNull
                                        if (inputTokens != null) {
                                            promptTokens = inputTokens
                                            emit(AiEvent.Usage(promptTokens, completionTokens, promptTokens + completionTokens))
                                        }
                                    }
                                    "content_block_delta" -> {
                                        val delta = root["delta"]?.jsonObject
                                        val text = delta?.get("text")?.jsonPrimitive?.content
                                        if (!text.isNullOrEmpty()) {
                                            emit(AiEvent.TextDelta(text))
                                        }
                                    }
                                    "message_delta" -> {
                                        val usage = root["usage"]?.jsonObject
                                        val outputTokens = usage?.get("output_tokens")?.jsonPrimitive?.intOrNull
                                        if (outputTokens != null) {
                                            completionTokens = outputTokens
                                            emit(AiEvent.Usage(promptTokens, completionTokens, promptTokens + completionTokens))
                                        }
                                    }
                                    "message_stop" -> {
                                        // End of message
                                    }
                                }
                            } catch (_: Exception) {
                                // Ignore non-json or unparseable lines
                            }
                        }
                    }
                }

                emit(AiEvent.Done)
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (attempts < maxAttempts) {
                    delay(delayMs)
                    delayMs *= 2
                    continue
                }
                emit(AiEvent.Error(Res.string(R.string.ai_error_offline), e))
                emit(AiEvent.Done)
                return@flow
            } catch (e: Exception) {
                emit(AiEvent.Error(Res.string(R.string.ai_error_generic, e.message ?: e.toString()), e))
                emit(AiEvent.Done)
                return@flow
            } finally {
                connection?.disconnect()
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun testConnection(options: AiChatOptions): Result<String> {
        val testOptions = options.copy(maxTokens = 5)
        val msg = AiChatMessage(id = "test", role = AiRole.USER, content = "Ping")
        var responseText = ""
        var errorMsg: String? = null

        try {
            stream(listOf(msg), testOptions).collect { event ->
                when (event) {
                    is AiEvent.TextDelta -> responseText += event.text
                    is AiEvent.Error -> errorMsg = event.message
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            return Result.failure(e)
        }

        return if (errorMsg != null) {
            Result.failure(IOException(errorMsg))
        } else {
            Result.success(responseText.ifEmpty { "OK" })
        }
    }
}
