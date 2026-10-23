// Modul: :core:domain
package com.codeforge.core.domain.ai

import kotlinx.coroutines.flow.Flow

enum class AiRole {
    SYSTEM,
    USER,
    ASSISTANT
}

data class AiUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

data class AiProposedChange(
    val filePath: String,
    val originalContent: String,
    val proposedContent: String,
    val unifiedDiff: String? = null
)

data class AiChatMessage(
    val id: String,
    val role: AiRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val usage: AiUsage? = null,
    val proposedChanges: List<AiProposedChange> = emptyList()
)

sealed class AiEvent {
    data class TextDelta(val text: String) : AiEvent()
    data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : AiEvent()
    object Done : AiEvent()
    data class Error(val message: String, val cause: Throwable? = null) : AiEvent()
}

enum class AiProviderType(
    val id: String,
    val displayName: String,
    val defaultBaseUrl: String,
    val defaultModel: String,
    val suggestedModels: List<String>
) {
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic (Claude)",
        defaultBaseUrl = "https://api.anthropic.com",
        defaultModel = "claude-3-7-sonnet-latest",
        suggestedModels = listOf("claude-3-7-sonnet-latest", "claude-3-5-sonnet-latest", "claude-3-5-haiku-latest")
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI-kompatibel",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o",
        suggestedModels = listOf("gpt-4o", "gpt-4o-mini", "o3-mini", "llama3.2")
    ),
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        defaultModel = "gemini-2.0-flash",
        suggestedModels = listOf("gemini-2.0-flash", "gemini-2.0-flash-lite", "gemini-1.5-pro", "gemini-1.5-flash")
    );

    companion object {
        fun fromId(id: String): AiProviderType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OPENAI
    }
}

data class AiChatOptions(
    val provider: AiProviderType,
    val model: String,
    val baseUrl: String? = null,
    val apiKey: String = "",
    val temperature: Float? = null,
    val maxTokens: Int? = null
)

enum class AiPrivacyPolicy {
    ALWAYS_ASK,
    ONLY_SELECTED,
    FULL_FILE
}

enum class AiContextSource {
    CURRENT_FILE,
    SELECTION,
    OPEN_FILES,
    BUILD_PROBLEMS,
    LSP_DIAGNOSTICS,
    GIT_DIFF
}

data class AiContextItem(
    val source: AiContextSource,
    val title: String,
    val content: String,
    val path: String? = null,
    val isRedacted: Boolean = false,
    val redactedSecretsCount: Int = 0
)

data class AiPreparedContext(
    val items: List<AiContextItem>,
    val fullPrompt: String,
    val totalCharacters: Int,
    val isTruncated: Boolean,
    val redactedCount: Int
)

interface AiProvider {
    fun streamChat(messages: List<AiChatMessage>, options: AiChatOptions): Flow<AiEvent>
    suspend fun testConnection(options: AiChatOptions): Result<String>
    suspend fun fetchAvailableModels(options: AiChatOptions): Result<List<String>>
}
