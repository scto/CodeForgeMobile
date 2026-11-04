// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.ai.AiChatMessage
import com.codeforge.core.domain.ai.AiChatOptions
import com.codeforge.core.domain.ai.AiPrivacyPolicy
import com.codeforge.core.domain.ai.AiProviderType
import kotlinx.coroutines.flow.Flow

/** Persistenz und Zähler des KI-Assistenten: Optionen, Datenschutz, Tageslimit, Verlauf je Projekt. */
interface AiRepository {
    /** Aktuelle Chat-Optionen (Anbieter, Modell, Schlüssel, Basis-URL). */
    fun getAiOptions(): Flow<AiChatOptions>
    /** Speichert die Chat-Optionen. */
    suspend fun saveAiOptions(options: AiChatOptions)

    /** Gespeicherte Einstellungen (Modell, Basis-URL, API-Schlüssel) eines Anbieters – unabhängig vom aktiven Anbieter. */
    suspend fun getProviderOptions(provider: AiProviderType): AiChatOptions

    /** Speichert Modell/Basis-URL/Schlüssel eines Anbieters, ohne den aktiven Anbieter zu wechseln (leerer Schlüssel = löschen). */
    suspend fun saveProviderOptions(options: AiChatOptions)

    /** Datenschutzeinstellungen: welche Inhalte an den Anbieter gesendet werden dürfen. */
    fun getPrivacyPolicy(): Flow<AiPrivacyPolicy>
    /** Speichert die Datenschutzeinstellungen. */
    suspend fun savePrivacyPolicy(policy: AiPrivacyPolicy)

    /** Maximale Anzahl KI-Anfragen pro Tag. */
    fun getDailyLimit(): Flow<Int>
    /** Speichert das Tageslimit. */
    suspend fun saveDailyLimit(limit: Int)

    /** Anzahl der heute gestellten Anfragen. */
    fun getTodayRequestCount(): Flow<Int>
    /** Erhöht den heutigen Anfragezähler um eins. */
    suspend fun incrementTodayRequestCount()

    /** Chat-Verlauf des Projekts [projectId]. */
    fun getHistory(projectId: String): Flow<List<AiChatMessage>>
    /** Speichert den Verlauf des Projekts [projectId]. */
    suspend fun saveHistory(projectId: String, history: List<AiChatMessage>)
    /** Löscht den Verlauf des Projekts [projectId]. */
    suspend fun clearHistory(projectId: String)
}
