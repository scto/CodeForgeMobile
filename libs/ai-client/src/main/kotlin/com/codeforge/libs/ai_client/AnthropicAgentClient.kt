// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.libs.agent.LlmClient
import com.codeforge.libs.agent.LlmRequest
import com.codeforge.libs.agent.LlmStreamEvent
import java.net.URL
import kotlinx.coroutines.flow.Flow

/**
 * [LlmClient] für die Anthropic-Messages-API mit Werkzeugaufrufen (Streaming per SSE).
 *
 * @param options Modell, API-Schlüssel und Basis-URL des Nutzers.
 */
class AnthropicAgentClient(private val options: AiChatOptions) : LlmClient {

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> {
        val baseUrl = AiHttpSupport.normalizeBaseUrl(options.baseUrl, "https://api.anthropic.com", listOf("/v1/messages", "/v1"))
        val body = AnthropicAgentWire.buildBody(request.copy(maxTokens = options.maxTokens ?: request.maxTokens), options.model).toString()
        return AgentSseTransport.stream(
            url = URL("$baseUrl/v1/messages"),
            headers = mapOf("x-api-key" to AiHttpSupport.cleanKey(options.apiKey), "anthropic-version" to "2023-06-01"),
            body = body,
            assembler = { AnthropicAgentWire.StreamAssembler() },
        )
    }
}
