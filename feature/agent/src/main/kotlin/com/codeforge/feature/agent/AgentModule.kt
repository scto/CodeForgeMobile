/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 */
package com.codeforge.feature.agent

import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.libs.agent.LlmClient
import com.codeforge.core.domain.ai.AiProviderType
import com.codeforge.libs.ai_client.AnthropicAgentClient
import com.codeforge.libs.mcp.DefaultMcpTransportFactory
import com.codeforge.libs.mcp.McpManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.codeforge.libs.ai_client.GeminiAgentClient
import com.codeforge.libs.ai_client.OpenAiAgentClient
import android.content.Context
import com.codeforge.libs.agent.AgentHistoryStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Singleton
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
    /** Wählt den Client nach Anbieter. */
    @Provides
    fun provideLlmFactory(): AgentLlmFactory = AgentLlmFactory { options ->
        when (options.provider) {
            AiProviderType.ANTHROPIC -> AnthropicAgentClient(options)
            AiProviderType.OPENAI, AiProviderType.GROK, AiProviderType.OPENROUTER -> OpenAiAgentClient(options)
            AiProviderType.GEMINI -> GeminiAgentClient(options)
        }
    }

    /** Verlauf je Projekt unter `files/agent_history`. */
    @Provides
    @Singleton
    fun provideHistoryStore(@ApplicationContext context: Context): AgentHistoryStore =
        AgentHistoryStore(File(context.filesDir, "agent_history"))

    /** Konfiguration der MCP-Verbindungen unter `files/agent`. */
    @Provides
    @Singleton
    fun provideConnections(@ApplicationContext context: Context): AgentConnections =
        AgentConnections(File(context.filesDir, "agent"))

    /** Ein Manager für die ganze App; stdio-Server laufen in der Termux-Umgebung. */
    @Provides
    @Singleton
    fun provideMcpManager(): McpManager =
        McpManager(CoroutineScope(SupervisorJob() + Dispatchers.Default), DefaultMcpTransportFactory(TermuxMcpLauncher))
}
