// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.ai.AiChatMessage
import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.domain.ai.AiPrivacyPolicy
import kotlinx.coroutines.flow.Flow

interface AiRepository {
    fun getAiOptions(): Flow<AiChatOptions>
    suspend fun saveAiOptions(options: AiChatOptions)

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
