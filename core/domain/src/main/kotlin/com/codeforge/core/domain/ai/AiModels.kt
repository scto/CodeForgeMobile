// Modul: :core:domain
package com.codeforge.core.domain.ai

import kotlinx.coroutines.flow.Flow

/** Rolle einer Chat-Nachricht: Systemanweisung, Nutzer oder Assistent. */
enum class AiRole {
    SYSTEM,
    USER,
    ASSISTANT
}

/** Token-Verbrauch einer Antwort (Prompt, Completion, Summe). */
data class AiUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

/** Vom Assistenten vorgeschlagene Dateiänderung: Pfad, Original, neuer Inhalt und optional der unified Diff. */
data class AiProposedChange(
    val filePath: String,
    val originalContent: String,
    val proposedContent: String,
    val unifiedDiff: String? = null
)

/** Eine Nachricht im KI-Chat mit Rolle, Inhalt, Zeitstempel, optionalem Verbrauch und vorgeschlagenen Änderungen. */
data class AiChatMessage(
    val id: String,
    val role: AiRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val usage: AiUsage? = null,
    val proposedChanges: List<AiProposedChange> = emptyList()
)

/** Ereignis eines gestreamten KI-Aufrufs. */
sealed class AiEvent {
    /** Teilstück der Antwort. */
    data class TextDelta(val text: String) : AiEvent()
    /** Token-Verbrauch (meist am Ende des Streams). */
    data class Usage(val promptTokens: Int, val completionTokens: Int, val totalTokens: Int) : AiEvent()
    /** Der Stream ist vollständig. */
    object Done : AiEvent()
    /** Fehler beim Aufruf; [cause] enthält optional die Ursache. */
    data class Error(val message: String, val cause: Throwable? = null) : AiEvent()
}

/** Unterstützte KI-Anbieter mit ihren Standardwerten. */
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
        defaultModel = "claude-sonnet-5-5",
        suggestedModels = listOf(
            "claude-fable-5-1",
            "claude-opus-5-5",
            "claude-sonnet-5-5",
            "claude-haiku-4-5",
            "claude-sonnet-4-5",
            "claude-opus-4-1",
            "claude-3-7-sonnet-latest"
        )
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI-kompatibel",
        defaultBaseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-5-mini",
        suggestedModels = listOf(
            "gpt-5",
            "gpt-5-mini",
            "gpt-5-nano",
            "gpt-4.1",
            "gpt-4.1-mini",
            "gpt-4o",
            "gpt-4o-mini",
            "o3",
            "o4-mini",
            "llama3.2"
        )
    ),
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        defaultBaseUrl = "https://generativelanguage.googleapis.com",
        defaultModel = "gemini-3.6-flash",
        // Stand ai.google.dev/gemini-api/docs/models (Nov 2026). 2.0-Modelle und gemini-3-pro-preview sind abgeschaltet;
        // 2.5 läuft nur noch für Bestandsnutzer. Die Live-Liste (fetchAvailableModels) ergänzt/ersetzt diese Vorschläge. gemini-3.8-flash und gemini-4-argon
        // wurden entfernt: auf Thomas' Schlüssel nicht erreichbar (Timeout); nur bestätigte Modelle vorschlagen.
        suggestedModels = listOf(
            "gemini-3.6-flash",
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3.1-pro-preview",
            "gemini-3-flash-preview",
            "gemini-flash-latest",
            "gemini-2.5-pro",
            "gemini-2.5-flash",
            "gemini-2.5-flash-lite"
        )
    );

    companion object {
        /** Erkennt den Anbieter am Schlüssel-Präfix (Gemini `AIza…` oder das neue `AQ.…`, Anthropic `sk-ant-…`), sonst `null`. */
        fun detectFromKey(rawKey: String): AiProviderType? {
            val key = rawKey.trim()
            return when {
                key.startsWith("AIza") || key.startsWith("AQ.") -> GEMINI
                key.startsWith("sk-ant-") -> ANTHROPIC
                else -> null
            }
        }

        /** Liefert den Anbieter zur [id] (Groß-/Kleinschreibung egal); unbekannte IDs ergeben [OPENAI]. */
        fun fromId(id: String): AiProviderType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OPENAI
    }
}

/** Optionen eines Chat-Aufrufs: Anbieter, Modell, optionale Basis-URL, API-Schlüssel, Temperatur und Token-Limit. */
data class AiChatOptions(
    val provider: AiProviderType,
    val model: String,
    val baseUrl: String? = null,
    val apiKey: String = "",
    val temperature: Float? = null,
    val maxTokens: Int? = null
)

/** Datenschutzrichtlinie für den Kontext: immer nachfragen, nur Auswahl oder ganze Datei. */
enum class AiPrivacyPolicy {
    ALWAYS_ASK,
    ONLY_SELECTED,
    FULL_FILE
}

/** Herkunft eines Kontext-Elements. */
enum class AiContextSource {
    CURRENT_FILE,
    SELECTION,
    OPEN_FILES,
    BUILD_PROBLEMS,
    LSP_DIAGNOSTICS,
    GIT_DIFF
}

/** Ein Kontext-Element für den Prompt; [isRedacted] und [redactedSecretsCount] zeigen geschwärzte Geheimnisse an. */
data class AiContextItem(
    val source: AiContextSource,
    val title: String,
    val content: String,
    val path: String? = null,
    val isRedacted: Boolean = false,
    val redactedSecretsCount: Int = 0
)

/** Fertig aufbereiteter Kontext: Elemente, Gesamt-Prompt, Zeichenzahl, ob gekürzt und Anzahl der Schwärzungen. */
data class AiPreparedContext(
    val items: List<AiContextItem>,
    val fullPrompt: String,
    val totalCharacters: Int,
    val isTruncated: Boolean,
    val redactedCount: Int
)

/** Abstraktion eines KI-Anbieters (Streaming-Chat, Verbindungstest, Modellliste). */
interface AiProvider {
    /** Streamt die Antwort auf [messages] als [AiEvent]s. */
    fun streamChat(messages: List<AiChatMessage>, options: AiChatOptions): Flow<AiEvent>
    /** Prüft Zugang und Konfiguration; liefert bei Erfolg eine kurze Meldung. */
    suspend fun testConnection(options: AiChatOptions): Result<String>
    /** Liest die beim Anbieter verfügbaren Modell-IDs. */
    suspend fun fetchAvailableModels(options: AiChatOptions): Result<List<String>>
}
