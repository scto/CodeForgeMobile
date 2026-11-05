// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.libs.agent.LlmClient
import com.codeforge.libs.agent.LlmRequest
import com.codeforge.libs.agent.LlmStreamEvent
import java.net.URL
import kotlinx.coroutines.flow.Flow

/**
 * [LlmClient] für die Gemini-API mit Funktionsaufrufen (Streaming per SSE).
 *
 * @param options Modell, API-Schlüssel und Basis-URL des Nutzers.
 */
class GeminiAgentClient(private val options: AiChatOptions) : LlmClient {

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> {
        val baseUrl = AiHttpSupport.normalizeBaseUrl(options.baseUrl, "https://generativelanguage.googleapis.com", listOf("/v1beta", "/v1"))
        val url = URL("$baseUrl/v1beta/models/${options.model.trim()}:streamGenerateContent?alt=sse")
        val body = GeminiAgentWire.buildBody(request.copy(maxTokens = options.maxTokens ?: request.maxTokens)).toString()
        return AgentSseTransport.stream(
            url = url,
            headers = mapOf("x-goog-api-key" to AiHttpSupport.cleanKey(options.apiKey)),
            body = body,
            assembler = { GeminiAgentWire.StreamAssembler() },
        )
    }
}
