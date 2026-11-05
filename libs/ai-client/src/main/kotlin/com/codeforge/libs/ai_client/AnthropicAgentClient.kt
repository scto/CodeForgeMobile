// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.agent.LlmClient
import com.codeforge.libs.agent.LlmRequest
import com.codeforge.libs.agent.LlmStreamEvent
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * [LlmClient] für die Anthropic-Messages-API mit Werkzeugaufrufen (Streaming per SSE).
 *
 * @param options Modell, API-Schlüssel und Basis-URL des Nutzers (`AiChatOptions.provider` muss ANTHROPIC sein).
 */
class AnthropicAgentClient(private val options: AiChatOptions) : LlmClient {

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        val baseUrl = AiHttpSupport.normalizeBaseUrl(options.baseUrl, "https://api.anthropic.com", listOf("/v1/messages", "/v1"))
        val body = AnthropicAgentWire.buildBody(request.copy(maxTokens = options.maxTokens ?: request.maxTokens), options.model).toString()
        val url = URL("$baseUrl/v1/messages")

        var attempt = 0
        var backoff = 1000L
        while (true) {
            attempt++
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 120_000
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("x-api-key", AiHttpSupport.cleanKey(options.apiKey))
                    setRequestProperty("anthropic-version", "2023-06-01")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "text/event-stream")
                }
                connection.outputStream.use { OutputStreamWriter(it, Charsets.UTF_8).use { w -> w.write(body) } }

                val code = connection.responseCode
                if ((code == 429 || code in 500..599) && attempt < MAX_ATTEMPTS) {
                    connection.disconnect()
                    delay(backoff)
                    backoff *= 2
                    continue
                }
                if (code !in 200..299) {
                    val err = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty() }.getOrDefault("")
                    emit(
                        LlmStreamEvent.Failed(
                            when (code) {
                                401, 403 -> Res.string(R.string.ai_error_invalid_key)
                                429 -> Res.string(R.string.ai_error_quota_exceeded)
                                else -> Res.string(R.string.ai_error_generic, "HTTP $code: $err")
                            },
                        ),
                    )
                    return@flow
                }

                val parser = SseEventParser()
                val assembler = AnthropicAgentWire.StreamAssembler()
                var completed = false
                BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val raw = parser.parseLine(line) ?: continue
                        for (event in assembler.accept(raw)) {
                            emit(event)
                            if (event is LlmStreamEvent.Completed || event is LlmStreamEvent.Failed) completed = true
                        }
                        if (completed) break
                    }
                }
                if (!completed) emit(LlmStreamEvent.Failed(Res.string(R.string.ai_error_generic, "Antwort unvollständig (Stream abgebrochen)")))
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                if (attempt < MAX_ATTEMPTS) {
                    delay(backoff)
                    backoff *= 2
                    continue
                }
                emit(LlmStreamEvent.Failed(AiHttpSupport.describe(e)))
                return@flow
            } catch (e: Exception) {
                emit(LlmStreamEvent.Failed(Res.string(R.string.ai_error_generic, e.message ?: e.toString())))
                return@flow
            } finally {
                connection?.disconnect()
            }
        }
    }.flowOn(Dispatchers.IO)

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
