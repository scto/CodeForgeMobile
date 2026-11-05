/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Anbieterneutrales Nachrichten- und Ereignismodell des KI-Agenten. Die Anbieter-Adapter (Anthropic, OpenAI, Gemini)
 * übersetzen es in ihr Wire-Format; die [AgentLoop] kennt nur dieses Modell.
 */
package com.codeforge.libs.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonObject

/** Absender einer [AgentMessage]. Ergebnisse von Werkzeugaufrufen werden als [USER]-Nachricht zurückgegeben. */
enum class AgentRole { USER, ASSISTANT }

/** Bestandteil einer Nachricht. */
sealed interface ContentPart {
    /** Freitext. */
    data class Text(val text: String) : ContentPart

    /** Werkzeugaufruf des Modells: eindeutige [id], Werkzeug [name] und die [arguments] als JSON-Objekt. */
    data class ToolUse(val id: String, val name: String, val arguments: JsonObject) : ContentPart

    /** Ergebnis zum Aufruf [toolUseId]; [isError] markiert Fehler und Ablehnungen. */
    data class ToolResultPart(val toolUseId: String, val content: String, val isError: Boolean = false) : ContentPart
}

/** Eine Nachricht des Gesprächs. */
data class AgentMessage(val role: AgentRole, val parts: List<ContentPart>) {
    /** Zusammengefügter Text aller [ContentPart.Text]-Teile. */
    val text: String get() = parts.filterIsInstance<ContentPart.Text>().joinToString("") { it.text }
}

/** Beschreibung eines Werkzeugs für das Modell; [parameters] ist ein JSON-Schema (`type: object`). */
data class ToolSpec(val name: String, val description: String, val parameters: JsonObject)

/** Warum das Modell die Antwort beendet hat. */
enum class StopReason { END_TURN, TOOL_USE, MAX_TOKENS, OTHER }

/** Anfrage an ein Sprachmodell. */
data class LlmRequest(
    val system: String,
    val messages: List<AgentMessage>,
    val tools: List<ToolSpec>,
    val maxTokens: Int = 4096,
)

/** Ereignisse eines gestreamten Modellaufrufs. Werkzeugaufrufe kommen vollständig (Argumente fertig zusammengesetzt). */
sealed interface LlmStreamEvent {
    /** Teilstück des Antworttextes. */
    data class TextDelta(val text: String) : LlmStreamEvent

    /** Fertig zusammengesetzter Werkzeugaufruf. */
    data class ToolCallCompleted(val id: String, val name: String, val arguments: JsonObject) : LlmStreamEvent

    /** Token-Verbrauch (darf mehrfach kommen; zählt der Aufrufer als Momentaufnahme). */
    data class Usage(val inputTokens: Int, val outputTokens: Int) : LlmStreamEvent

    /** Ende der Modellantwort. */
    data class Completed(val stopReason: StopReason) : LlmStreamEvent

    /** Aufruf gescheitert; danach folgt kein weiteres Ereignis. */
    data class Failed(val message: String) : LlmStreamEvent
}

/** Sprachmodell mit Werkzeugaufrufen. Implementierungen liegen in den Anbieter-Adaptern (`:libs:ai-client`). */
fun interface LlmClient {
    /** Streamt die Antwort auf [request]. */
    fun stream(request: LlmRequest): Flow<LlmStreamEvent>
}
