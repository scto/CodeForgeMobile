// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.ai.AiChatMessage
import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.domain.ai.AiPrivacyPolicy
import com.codeforge.core.domain.ai.AiProviderType
import kotlinx.coroutines.flow.Flow

interface AiRepository {
    fun getAiOptions(): Flow<AiChatOptions>
    suspend fun saveAiOptions(options: AiChatOptions)

    /** Gespeicherte Einstellungen (Modell, Basis-URL, API-Schlüssel) eines Anbieters – unabhängig vom aktiven Anbieter. */
    suspend fun getProviderOptions(provider: AiProviderType): AiChatOptions

    /** Speichert Modell/Basis-URL/Schlüssel eines Anbieters, ohne den aktiven Anbieter zu wechseln (leerer Schlüssel = löschen). */
    suspend fun saveProviderOptions(options: AiChatOptions)

    fun getPrivacyPolicy(): Flow<AiPrivacyPolicy>
    suspend fun savePrivacyPolicy(policy: AiPrivacyPolicy)

    fun getDailyLimit(): Flow<Int>
    suspend fun saveDailyLimit(limit: Int)

    fun getTodayRequestCount(): Flow<Int>
    suspend fun incrementTodayRequestCount()

    fun getHistory(projectId: String): Flow<List<AiChatMessage>>
    suspend fun saveHistory(projectId: String, history: List<AiChatMessage>)
    suspend fun clearHistory(projectId: String)
}
