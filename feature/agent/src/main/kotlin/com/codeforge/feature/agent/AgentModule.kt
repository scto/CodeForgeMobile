/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 */
package com.codeforge.feature.agent

import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.libs.agent.LlmClient
import com.codeforge.libs.ai_client.AnthropicAgentClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Erzeugt den [LlmClient] für die gewählten Optionen (in Tests durch einen Fake ersetzbar). */
fun interface AgentLlmFactory {
    /** Client für [options]. */
    fun create(options: AiChatOptions): LlmClient
}

/** Hilt-Bindung der Produktiv-Fabrik. */
@Module
@InstallIn(SingletonComponent::class)
object AgentModule {
    /** Anthropic ist bisher der einzige Anbieter mit Werkzeugaufrufen. */
    @Provides
    fun provideLlmFactory(): AgentLlmFactory = AgentLlmFactory { AnthropicAgentClient(it) }
}
