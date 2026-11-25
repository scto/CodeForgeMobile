/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Ereignisse, die App-Module (Editor, Git) an Plugins melden. */
sealed interface HostEvent {
    /** Eine Datei wurde geöffnet. */
    data class FileOpened(val path: String) : HostEvent
    /** Eine Datei wurde gespeichert. */
    data class FileSaved(val path: String) : HostEvent
    /** Eine Datei wurde geschlossen. */
    data class FileClosed(val path: String) : HostEvent
    /** Ein Commit wurde erstellt. */
    data class GitCommitted(val repoPath: String, val message: String) : HostEvent
    /** Der aktuelle Branch hat gewechselt. */
    data class GitBranchChanged(val repoPath: String, val branch: String) : HostEvent
    /** Eine andere geöffnete Datei wurde zur aktiven (Tab-Wechsel). */
    data class EditorActivated(val path: String) : HostEvent
    /** Die Diagnosen einer Datei haben sich geändert. */
    data class DiagnosticsChanged(val path: String, val errors: Int, val warnings: Int) : HostEvent
    /** Ein Gradle-Task wurde gestartet. */
    data class BuildStarted(val task: String) : HostEvent
    /** Ein Gradle-Task ist beendet ([success] = ohne Fehler). */
    data class BuildFinished(val task: String, val success: Boolean) : HostEvent
    /** Ein Projekt wurde geöffnet. */
    data class ProjectOpened(val rootPath: String) : HostEvent
    /** Das Projekt wurde geschlossen. */
    data class ProjectClosed(val rootPath: String) : HostEvent

    /** Benötigte Plugin-Berechtigung (siehe [PluginPermissions]). */
    val permission: String
        get() = when (this) {
            is FileOpened, is FileSaved, is FileClosed, is EditorActivated, is DiagnosticsChanged -> PluginPermissions.FILE_EVENTS
            is GitCommitted, is GitBranchChanged -> PluginPermissions.GIT_EVENTS
            is BuildStarted, is BuildFinished -> PluginPermissions.BUILD_EVENTS
            is ProjectOpened, is ProjectClosed -> PluginPermissions.PROJECT_EVENTS
        }
}

/** Eingang für Ereignisse; Standard im Test/ohne Plugin-System ist [NoOpPluginEventSink]. */
interface PluginEventSink {
    /** Meldet [event] an die Plugins (nur an solche mit passender Berechtigung). */
    fun publish(event: HostEvent)
}

/** Ereignissenke ohne Wirkung (Tests, deaktiviertes Plugin-System). */
object NoOpPluginEventSink : PluginEventSink {
    override fun publish(event: HostEvent) = Unit
}

/** Ort, an dem ein Plugin-Menüeintrag erscheint: Editor-Menü oder Kontextmenü des Dateibaums. */
enum class PluginMenuTarget { EDITOR_MENU, FILE_CONTEXT_MENU }

/** Ein von einem Plugin registrierter Befehl. */
data class PluginCommandInfo(val pluginId: String, val commandId: String, val title: String)

/** Menüeintrag eines Plugins, der den Befehl [commandId] auslöst. */
data class PluginMenuEntry(
    val pluginId: String,
    val id: String,
    val title: String,
    val target: PluginMenuTarget,
    val commandId: String
)

/** Was die UI von Code-Plugins sieht: registrierte Befehle/Menüeinträge, Ausführung, Meldungen. */
interface PluginUiRepository {
    /** Alle registrierten Plugin-Befehle. */
    val commands: StateFlow<List<PluginCommandInfo>>
    /** Alle Plugin-Menüeinträge. */
    val menuEntries: StateFlow<List<PluginMenuEntry>>

    /** Kurze Nachrichten von Plugins (`showMessage`), von der UI als Snackbar gezeigt. */
    val messages: SharedFlow<String>

    /** Führt den Befehl aus; [activeFilePath] ist die aktive/ausgewählte Datei oder null. Fehler des Plugins werden als Failure gemeldet. */
    suspend fun runCommand(pluginId: String, commandId: String, activeFilePath: String?): Result<Unit>
}

/**
 * Berechtigungsregeln für Code-Plugins: Jede Berechtigung muss im Manifest stehen. Die frühere Ausnahme für API 1
 * (`shell`, `lsp` ohne Angabe) ist seit 3.4.0 entfernt, solche Plugins werden gar nicht mehr geladen.
 */
object PluginPermissionPolicy {
    /** Ob [permission] bei den Berechtigungen [granted] erlaubt ist. */
    fun isAllowed(granted: Set<String>, permission: String): Boolean = permission in granted
}
