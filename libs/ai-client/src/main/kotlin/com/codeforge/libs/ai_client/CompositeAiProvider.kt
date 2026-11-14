// Modul: :libs:ai-client
package com.codeforge.libs.ai_client

import com.codeforge.core.domain.ai.AiChatMessage
import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.domain.ai.AiEvent
import com.codeforge.core.domain.ai.AiProvider
import com.codeforge.core.domain.ai.AiProviderType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** [AiProvider], der je nach gewähltem Anbieter an [AnthropicClient], [OpenAiClient] oder [GeminiClient] delegiert. */
@Singleton
class CompositeAiProvider @Inject constructor(
    private val anthropicClient: AnthropicClient,
    private val openAiClient: OpenAiClient,
    private val geminiClient: GeminiClient
) : AiProvider {

    override fun streamChat(messages: List<AiChatMessage>, options: AiChatOptions): Flow<AiEvent> =
        when (options.provider) {
            AiProviderType.ANTHROPIC -> anthropicClient.stream(messages, options)
            AiProviderType.OPENAI, AiProviderType.GROK, AiProviderType.OPENROUTER -> openAiClient.stream(messages, options)
            AiProviderType.GEMINI -> geminiClient.stream(messages, options)
        }

    override suspend fun testConnection(options: AiChatOptions): Result<String> =
        when (options.provider) {
            AiProviderType.ANTHROPIC -> anthropicClient.testConnection(options)
            AiProviderType.OPENAI, AiProviderType.GROK, AiProviderType.OPENROUTER -> openAiClient.testConnection(options)
            AiProviderType.GEMINI -> geminiClient.testConnection(options)
        }

    override suspend fun fetchAvailableModels(options: AiChatOptions): Result<List<String>> =
        when (options.provider) {
            AiProviderType.ANTHROPIC -> Result.success(options.provider.suggestedModels)
            AiProviderType.OPENAI, AiProviderType.GROK, AiProviderType.OPENROUTER -> {
                val remote = openAiClient.fetchModels(options)
                if (remote.isSuccess && remote.getOrNull()?.isNotEmpty() == true) {
                    remote
                } else {
                    Result.success(options.provider.suggestedModels)
                }
            }
            AiProviderType.GEMINI -> {
                val remote = geminiClient.fetchModels(options)
                if (remote.isSuccess && remote.getOrNull()?.isNotEmpty() == true) {
                    remote
                } else {
                    Result.success(options.provider.suggestedModels)
                }
            }
        }
}
