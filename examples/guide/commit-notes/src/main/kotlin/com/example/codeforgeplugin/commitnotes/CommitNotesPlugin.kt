/**
 * Modul: :examples:guide-commit-notes
 * @author Thomas Schmid
 *
 * Beispiel-Code-Plugin (API v2): hört auf Git-Commits, schreibt sie in `$HOME/.codeforge-commit-notes.log` und
 * zeigt die letzten Einträge über einen Befehl im Editor-Menü an. Berechtigungen: `commands`, `menus`, `git-events`.
 */
package com.example.codeforgeplugin.commitnotes

import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginEvent
import com.codeforge.libs.plugin_api.PluginHostServices
import com.codeforge.libs.plugin_api.PluginMenuTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Protokolliert Commits und zeigt die letzten [SHOWN] Einträge an. */
class CommitNotesPlugin : CodeForgePlugin {

    private val lock = Any()
    private lateinit var logFile: File

    override fun onLoad(context: PluginContext) {
        val host: PluginHostServices = context.hostServices
        logFile = File(host.homeDir, LOG_NAME)

        host.registerCommand(COMMAND_ID, "Letzte Commits anzeigen") {
            val lines = withContext(Dispatchers.IO) { lastEntries() }
            host.showMessage(if (lines.isEmpty()) "Noch keine Commits protokolliert." else lines.joinToString("\n"))
        }
        host.registerMenuItem("commit-notes-menu", "Letzte Commits", PluginMenuTarget.EDITOR_MENU, COMMAND_ID)

        host.addEventListener { event ->
            if (event is PluginEvent.GitCommitted) append(event)
        }
    }

    override fun onUnload() = Unit

    /** Beim vollständigen Entfernen die selbst angelegte Logdatei löschen (nicht beim bloßen Deaktivieren). */
    override suspend fun onUninstall(context: PluginContext) {
        withContext(Dispatchers.IO) { File(context.hostServices.homeDir, LOG_NAME).delete() }
    }

    private fun append(event: PluginEvent.GitCommitted) {
        val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        val message = event.message.lineSequence().firstOrNull().orEmpty().take(MAX_MESSAGE)
        val line = "$time  ${File(event.repoPath).name}: $message"
        synchronized(lock) { logFile.appendText(line + "\n") }
    }

    private fun lastEntries(): List<String> = synchronized(lock) {
        if (!logFile.isFile) emptyList() else logFile.readLines().takeLast(SHOWN)
    }

    private companion object {
        const val LOG_NAME = ".codeforge-commit-notes.log"
        const val COMMAND_ID = "show-commits"
        const val SHOWN = 5
        const val MAX_MESSAGE = 80
    }
}
