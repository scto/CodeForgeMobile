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
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

@Singleton
class OpenAiClient @Inject constructor() {

    private val json = Json { ignoreUnknownKeys = true }

    fun stream(messages: List<AiChatMessage>, options: AiChatOptions): Flow<AiEvent> = flow {
        val baseUrl = (options.baseUrl ?: "https://api.openai.com/v1").trimEnd('/')
        val url = if (baseUrl.endsWith("/chat/completions")) {
            URL(baseUrl)
        } else {
            URL("$baseUrl/chat/completions")
        }

        val body = buildJsonObject {
            put("model", options.model)
            put(
                "messages",
                buildJsonArray {
                    for (msg in messages) {
                        add(
                            buildJsonObject {
                                put(
                                    "role",
                                    when (msg.role) {
                                        AiRole.SYSTEM -> "system"
                                        AiRole.USER -> "user"
                                        AiRole.ASSISTANT -> "assistant"
                                    }
                                )
                                put("content", msg.content)
                            }
                        )
                    }
                }
            )
            put("stream", true)
            put(
                "stream_options",
                buildJsonObject {
                    put("include_usage", true)
                }
            )
            options.temperature?.let { put("temperature", it.toDouble()) }
            options.maxTokens?.let { put("max_tokens", it) }
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
                    if (options.apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${options.apiKey}")
                    }
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

                val parser = SseEventParser()
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val event = parser.parseLine(line!!) ?: continue
                        val data = event.data.trim()
                        if (data == "[DONE]") {
                            break
                        }

                        if (data.isNotBlank()) {
                            try {
                                val root = json.parseToJsonElement(data).jsonObject

                                // Usage stats
                                root["usage"]?.jsonObject?.let { u ->
                                    val prompt = u["prompt_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                                    val completion = u["completion_tokens"]?.jsonPrimitive?.intOrNull ?: 0
                                    val total = u["total_tokens"]?.jsonPrimitive?.intOrNull ?: (prompt + completion)
                                    emit(AiEvent.Usage(prompt, completion, total))
                                }

                                val choices = root["choices"]?.jsonArray
                                if (choices != null && choices.isNotEmpty()) {
                                    val delta = choices[0].jsonObject["delta"]?.jsonObject
                                    val content = delta?.get("content")?.jsonPrimitive?.content
                                    if (!content.isNullOrEmpty()) {
                                        emit(AiEvent.TextDelta(content))
                                    }
                                }
                            } catch (_: Exception) {
                                // Skip partial / malformed chunk
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

    suspend fun fetchModels(options: AiChatOptions): Result<List<String>> = withContext(Dispatchers.IO) {
        val baseUrl = (options.baseUrl ?: "https://api.openai.com/v1").trimEnd('/')
        val url = if (baseUrl.endsWith("/models")) {
            URL(baseUrl)
        } else {
            URL("$baseUrl/models")
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                requestMethod = "GET"
                if (options.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${options.apiKey}")
                }
                setRequestProperty("Accept", "application/json")
            }

            if (connection.responseCode !in 200..299) {
                return@withContext Result.failure(IOException("HTTP ${connection.responseCode}"))
            }

            val text = connection.inputStream.bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(text).jsonObject
            val data = root["data"]?.jsonArray
            val models = data?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }?.sorted()
                ?: emptyList()
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
