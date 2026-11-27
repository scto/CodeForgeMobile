/**
 * Plugin: Sitzungsstatistik (Sandbox, API 5)
 *
 * Zeigt, was alles in der Sandbox geht:
 * - eigene Befehle (`ide.commands.register`), eingehängt über `contributes.menus` (Editor, Dateibaum, Palette) und
 *   `contributes.keybindings`, jeweils mit `when`-Bedingung
 * - Ereignisse abonnieren (`file.*`, `build.finished`, `git.committed`) und eigene senden (`plugin.<id>.saved`)
 * - Statusleiste mit Klick-Befehl
 * - Einstellungen aus `contributes.settings` (Schnappschuss; Änderungen laden das Plugin neu)
 * - eine klassische Editor-Aktion (`editor`), die die Statistik als Kommentar einfügt
 */
package com.codeforge.plugin.session

import com.codeforge.core.domain.extension.ide.IdeCommandHandler
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.EditorAction
import com.codeforge.libs.plugin_api.EditorContext
import com.codeforge.libs.plugin_api.EditorEffect
import com.codeforge.libs.plugin_api.IdeApi
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_api.PluginExtensionPoints
import com.codeforge.libs.plugin_api.TextEdit

class SessionStatsPlugin : CodeForgePlugin {
    private val stats = SessionStats()

    override fun onLoad(context: PluginContext) {
        val ide = context.ide()
        val settings = context.settings()
        val showStatus = settings.getString("statusBar") != "false"
        val long = settings.getString("format") == "long"
        val showCommand = "${context.pluginId}.show"

        fun refresh() {
            if (showStatus) ide.statusBar.set(STATUS_ID, stats.statusText(long), stats.summary(), showCommand)
        }

        ide.commands.register("show", "Sitzungsstatistik anzeigen", IdeCommandHandler { stats.summary() }, CATEGORY)
        ide.commands.register("reset", "Sitzungsstatistik zurücksetzen", IdeCommandHandler {
            stats.reset()
            refresh()
            "Statistik zurückgesetzt"
        }, CATEGORY)
        ide.commands.register("file", "Wie oft gespeichert?", IdeCommandHandler { args ->
            val path = args["path"] ?: return@IdeCommandHandler "Keine Datei gewählt"
            "${path.substringAfterLast('/')}: ${stats.savesOf(path)}× gespeichert in dieser Sitzung"
        }, CATEGORY)

        listOf("file.", "build.finished", "git.committed").forEach { prefix ->
            ide.events.subscribe(prefix) { event ->
                if (stats.onEvent(event.topic, event.payload)) {
                    refresh()
                    if (event.topic == "file.saved") publishSaved(ide)
                }
            }
        }
        refresh()

        context.register(PluginExtensionPoints.EDITOR_ACTION, object : EditorAction {
            override val id = "insertSummary"
            override val title = "Sitzungsstatistik als Kommentar einfügen"
            override val kind = EditorAction.KIND_SOURCE
            override suspend fun perform(context: EditorContext): List<EditorEffect> {
                val comment = commentFor(context.languageId, stats.summary())
                return listOf(EditorEffect.ApplyEdits(listOf(TextEdit(context.cursorOffset, context.cursorOffset, comment + "\n"))))
            }
        })
    }

    override fun onUnload() = Unit

    // Andere Plugins können `plugin.<id>.saved` abonnieren.
    private fun publishSaved(ide: IdeApi) = ide.events.publish("saved", mapOf("count" to stats.saves.toString()))

    companion object {
        private const val STATUS_ID = "stats"
        private const val CATEGORY = "Statistik"

        /** Zeilenkommentar passend zur Sprache. */
        fun commentFor(languageId: String, text: String): String = when (languageId) {
            "python", "shellscript", "yaml", "toml", "ruby", "perl", "r" -> "# $text"
            "html", "xml", "markdown" -> "<!-- $text -->"
            "sql", "lua", "haskell" -> "-- $text"
            else -> "// $text"
        }
    }
}
