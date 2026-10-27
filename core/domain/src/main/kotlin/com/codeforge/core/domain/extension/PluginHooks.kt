/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.extension

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/** Ereignisse, die App-Module (Editor, Git) an Plugins melden. */
sealed interface HostEvent {
    data class FileOpened(val path: String) : HostEvent
    data class FileSaved(val path: String) : HostEvent
    data class FileClosed(val path: String) : HostEvent
    data class GitCommitted(val repoPath: String, val message: String) : HostEvent
    data class GitBranchChanged(val repoPath: String, val branch: String) : HostEvent

    /** Benötigte Plugin-Berechtigung (siehe [PluginPermissions]). */
    val permission: String
        get() = when (this) {
            is FileOpened, is FileSaved, is FileClosed -> PluginPermissions.FILE_EVENTS
            is GitCommitted, is GitBranchChanged -> PluginPermissions.GIT_EVENTS
        }
}

/** Eingang für Ereignisse; Standard im Test/ohne Plugin-System ist [NoOpPluginEventSink]. */
interface PluginEventSink {
    fun publish(event: HostEvent)
}

object NoOpPluginEventSink : PluginEventSink {
    override fun publish(event: HostEvent) = Unit
}

enum class PluginMenuTarget { EDITOR_MENU, FILE_CONTEXT_MENU }

data class PluginCommandInfo(val pluginId: String, val commandId: String, val title: String)

data class PluginMenuEntry(
    val pluginId: String,
    val id: String,
    val title: String,
    val target: PluginMenuTarget,
    val commandId: String
)

/** Was die UI von Code-Plugins sieht: registrierte Befehle/Menüeinträge, Ausführung, Meldungen. */
interface PluginUiRepository {
    val commands: StateFlow<List<PluginCommandInfo>>
    val menuEntries: StateFlow<List<PluginMenuEntry>>

    /** Kurze Nachrichten von Plugins (`showMessage`), von der UI als Snackbar gezeigt. */
    val messages: SharedFlow<String>

    /** Führt den Befehl aus; [activeFilePath] ist die aktive/ausgewählte Datei oder null. Fehler des Plugins werden als Failure gemeldet. */
    suspend fun runCommand(pluginId: String, commandId: String, activeFilePath: String?): Result<Unit>
}

/**
 * Berechtigungsregeln für Code-Plugins. Neue Berechtigungen (`commands`, `menus`, `file-events`, `git-events`)
 * müssen immer im Manifest stehen. Die älteren Zugriffe (`shell`, `lsp`) bleiben für v1-Plugins (ohne
 * `apiVersion`) erlaubt, damit bestehende Plugins weiterlaufen; ab `apiVersion` 2 sind auch sie deklarationspflichtig.
 */
object PluginPermissionPolicy {
    private val LEGACY_OPEN = setOf(PluginPermissions.SHELL, PluginPermissions.LSP)

    fun isAllowed(apiVersion: Int, granted: Set<String>, permission: String): Boolean =
        permission in granted || (apiVersion < 2 && permission in LEGACY_OPEN)
}
