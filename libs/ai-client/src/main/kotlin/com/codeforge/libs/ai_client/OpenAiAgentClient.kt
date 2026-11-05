// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.libs.agent.LlmClient
import com.codeforge.libs.agent.LlmRequest
import com.codeforge.libs.agent.LlmStreamEvent
import java.net.URL
import kotlinx.coroutines.flow.Flow

/**
 * [LlmClient] für OpenAI und kompatible Server (Chat Completions mit Werkzeugaufrufen, Streaming per SSE).
 *
 * @param options Modell, API-Schlüssel und Basis-URL des Nutzers.
 */
class OpenAiAgentClient(private val options: AiChatOptions) : LlmClient {

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> {
        val baseUrl = AiHttpSupport.normalizeBaseUrl(options.baseUrl, "https://api.openai.com/v1")
        val url = if (baseUrl.endsWith("/chat/completions")) URL(baseUrl) else URL("$baseUrl/chat/completions")
        val body = OpenAiAgentWire.buildBody(
            request.copy(maxTokens = options.maxTokens ?: request.maxTokens),
            options.model,
            useCompletionTokens = url.host.equals("api.openai.com", ignoreCase = true),
        ).toString()
        val headers = if (options.apiKey.isNotBlank()) {
            mapOf("Authorization" to "Bearer ${AiHttpSupport.cleanKey(options.apiKey)}")
        } else {
            emptyMap()
        }
        return AgentSseTransport.stream(url, headers, body) { OpenAiAgentWire.StreamAssembler() }
    }
}
