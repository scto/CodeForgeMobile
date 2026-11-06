/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 *
 * Zustand, Aktionen und Einmal-Effekte des KI-Agenten.
 */
package com.codeforge.feature.agent

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.agent.AgentAttachment
import com.codeforge.libs.agent.PermissionMode
import com.codeforge.libs.mcp.McpStatus

/** Fortschritt eines Werkzeugaufrufs in der Chat-Liste. */
enum class ToolCallStatus { RUNNING, OK, ERROR, DENIED }

/** Eintrag der Chat-Liste; [id] ist je Sitzung eindeutig (stabile Keys in der `LazyColumn`). */
@Immutable
sealed interface AgentChatItem {
    /** Eindeutige ID. */
    val id: Long

    /** Nachricht des Nutzers. */
    data class User(override val id: Long, val text: String) : AgentChatItem

    /** Antwort des Agenten (wächst während des Streams). */
    data class Assistant(override val id: Long, val text: String) : AgentChatItem

    /** Werkzeugaufruf mit lesbarer Kurzfassung [summary] und gekürztem Ergebnis [detail]. */
    data class ToolCall(
        override val id: Long,
        val callId: String,
        val summary: String,
        val status: ToolCallStatus,
        val detail: String = "",
    ) : AgentChatItem

    /** Hinweis oder Fehler. */
    data class Notice(override val id: Long, val text: String, val isError: Boolean) : AgentChatItem

    /** Zusammenfassung der Dateiänderungen eines Zuges; [reverted] = bereits rückgängig gemacht. */
    data class Changes(
        override val id: Long,
        val turnId: Long,
        val files: List<ChangedFile>,
        val reverted: Boolean = false,
    ) : AgentChatItem {
        /** Pfade der geänderten Dateien. */
        val paths: List<String> get() = files.map { it.path }
    }
}

/** Geänderte Datei eines Zuges mit Unified-[diff] (leer bei Binär-/Riesendateien). */
@Immutable
data class ChangedFile(val path: String, val diff: String)

/** Offene Freigabeanfrage für einen schreibenden/ausführenden Werkzeugaufruf. */
@Immutable
data class PendingApproval(val toolName: String, val summary: String, val preview: String? = null)

/** Eintrag der Aufgabenliste des Agenten. */
@Immutable
data class AgentTodo(val content: String, val status: String)

/** Ein MCP-Server in der Verbindungsliste; [fromProject] = stammt aus der `.mcp.json` des Projekts. */
@Immutable
data class McpServerUi(
    val name: String,
    val target: String,
    val status: McpStatus,
    val toolCount: Int,
    val message: String?,
    val fromProject: Boolean,
)

/** UI-Zustand des Agenten-Panels. */
@Immutable
data class AgentUiState(
    val items: List<AgentChatItem> = emptyList(),
    val input: String = "",
    val mode: PermissionMode = PermissionMode.ASK_EACH,
    val running: Boolean = false,
    val pendingApproval: PendingApproval? = null,
    val todos: List<AgentTodo> = emptyList(),
    val rootPath: String = "",
    val mcpServers: List<McpServerUi> = emptyList(),
    val showConnections: Boolean = false,
    val attachments: List<AgentAttachment> = emptyList(),
    /** Kurzbeschreibung gefundener, noch nicht freigegebener Projektregeln/Skills; `null` = nichts offen. */
    val untrustedContext: String? = null,
)

/** Aktionen des Nutzers. */
sealed interface AgentUiEvent {
    /** Panel für das Projekt [rootPath] öffnen (wechselt der Pfad, beginnt ein neuer Chat). */
    data class Initialize(val rootPath: String) : AgentUiEvent

    /** Eingabetext geändert. */
    data class InputChanged(val value: String) : AgentUiEvent

    /** Eingabe senden. */
    data object Send : AgentUiEvent

    /** Lauf abbrechen. */
    data object Stop : AgentUiEvent

    /** Berechtigungsmodus wechseln. */
    data class ModeChanged(val mode: PermissionMode) : AgentUiEvent

    /** Freigabe erteilen ([allow] = true) oder ablehnen. */
    data class Decide(val allow: Boolean) : AgentUiEvent

    /** Änderungen des Zuges [turnId] rückgängig machen. */
    data class Revert(val turnId: Long) : AgentUiEvent

    /** Neuen Chat beginnen (Verlauf verwerfen). */
    data object NewChat : AgentUiEvent

    /** Verbindungsliste (MCP) ein- oder ausblenden. */
    data class ShowConnections(val show: Boolean) : AgentUiEvent

    /**
     * MCP-Server hinzufügen: [target] ist eine URL (`https://…`, HTTP) oder eine Befehlszeile (stdio, durch Leerzeichen getrennt);
     * [token] wird bei URLs als `Authorization: Bearer …` gesendet.
     */
    data class AddMcpServer(val target: String, val name: String, val token: String) : AgentUiEvent

    /** App-Server entfernen. */
    data class RemoveMcpServer(val name: String) : AgentUiEvent

    /** App-Server aktivieren/deaktivieren. */
    data class ToggleMcpServer(val name: String) : AgentUiEvent

    /** Projekt-Server freigeben. */
    data class TrustMcpServer(val name: String) : AgentUiEvent

    /** Server neu verbinden. */
    data class ReconnectMcpServer(val name: String) : AgentUiEvent

    /** Auswahl/Datei aus dem Editor als Anhang der nächsten Nachricht vormerken. */
    data class Attach(val attachment: AgentAttachment) : AgentUiEvent

    /** Vorgemerkten Anhang entfernen. */
    data class RemoveAttachment(val index: Int) : AgentUiEvent

    /** Gefundene Projektregeln/Skills freigeben. */
    data object TrustContext : AgentUiEvent
}
