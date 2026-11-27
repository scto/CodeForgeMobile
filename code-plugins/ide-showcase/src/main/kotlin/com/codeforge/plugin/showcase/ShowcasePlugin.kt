/**
 * Plugin: IDE-Werkzeugkasten (API 5, App-Prozess)
 *
 * Referenz für die ganze Plugin-API 5. Jeder Bereich kommt mindestens einmal vor:
 * - Befehle: eigene anmelden (`ide.commands.register`), fremde ausführen (`cfm.build.run`) und auflisten
 * - Ereignisse: alle abonnieren (Protokoll), eigene senden (`plugin.<id>.note`)
 * - App-Einstellungen lesen/schreiben (`editor.minimap`) und beobachten
 * - Editor: Zustand, Textänderungen, Datei öffnen, speichern
 * - Dateien: Projektbaum lesen, `NOTES.md` schreiben
 * - Git: Zustand, Diff, bereitstellen und committen (mit Rückfrage und Fortschritt)
 * - Navigation, Statusleiste, Benachrichtigung
 * - Oberflächen: Werkzeugfenster (Baum), Bildschirm (Übersicht), Einstellungsseite (schreibt `contributes.settings`)
 * - `contributes.menus`/`keybindings` mit `when` (siehe plugin.json)
 */
package com.codeforge.plugin.showcase

import com.codeforge.core.domain.extension.ide.IdeCommandHandler
import com.codeforge.core.domain.extension.ide.IdeEvent
import com.codeforge.core.domain.extension.ide.IdeNavTarget
import com.codeforge.libs.plugin_api.CodeForgePlugin
import com.codeforge.libs.plugin_api.IdeApi
import com.codeforge.libs.plugin_api.PluginContext
import com.codeforge.libs.plugin_ui_api.PluginUiPoints
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ShowcasePlugin : CodeForgePlugin {
    private var scope: CoroutineScope? = null

    override fun onLoad(context: PluginContext) {
        val s = CoroutineScope(SupervisorJob() + Dispatchers.Default).also { scope = it }
        val ide = context.ide()
        val state = ShowcaseState(context, ide, s)
        state.install()
        context.register(PluginUiPoints.TOOL_WINDOW, TreeToolWindow(state))
        context.register(PluginUiPoints.SCREEN, DashboardScreen(state))
        context.register(PluginUiPoints.SETTINGS_PAGE, ShowcaseSettingsPage(state))
    }

    override fun onUnload() {
        scope?.cancel()
        scope = null
    }
}

/** Gemeinsamer Zustand für Befehle und Oberflächen. */
class ShowcaseState(val context: PluginContext, val ide: IdeApi, val scope: CoroutineScope) {
    val log = EventLog()
    private val _logVersion = MutableStateFlow(0)

    /** Ändert sich mit jedem neuen Ereignis (für die Oberfläche). */
    val logVersion: StateFlow<Int> = _logVersion

    val pluginId: String get() = context.pluginId
    private val time = SimpleDateFormat("HH:mm:ss", Locale.ROOT)

    fun install() {
        registerCommands()
        ide.events.subscribe("") { e -> onEvent(e) }
        context.settings().addChangeListener { key ->
            record("Plugin-Einstellung geändert: $key")
            if (key == "statusBar") refreshStatus()
        }
        ide.settings.onChange { key -> record("App-Einstellung geändert: $key") }
        refreshStatus()
    }

    private fun registerCommands() {
        val c = ide.commands
        c.register("dashboard", "Werkzeugkasten öffnen", IdeCommandHandler {
            ide.navigation.navigate(IdeNavTarget.PluginScreen(pluginId, DashboardScreen.ID)); null
        }, CATEGORY)
        c.register("tree", "Projektbaum öffnen", IdeCommandHandler {
            ide.navigation.navigate(IdeNavTarget.PluginToolWindow(pluginId, TreeToolWindow.ID)); null
        }, CATEGORY)
        c.register("upper", "Auswahl in Großbuchstaben", IdeCommandHandler {
            val edit = TextOps.upperSelection(ide.editor.snapshot()) ?: return@IdeCommandHandler "Nichts ausgewählt"
            if (ide.editor.applyEdits(null, listOf(edit))) null else "Editor nicht bereit"
        }, CATEGORY)
        c.register("todo", "TODO einfügen", IdeCommandHandler {
            val author = context.settings().getString("author").ifBlank { "ich" }
            ide.editor.applyEdits(null, listOf(TextOps.todoAt(ide.editor.snapshot(), author))); null
        }, CATEGORY)
        c.register("reveal", "Im Editor öffnen", IdeCommandHandler { args ->
            val path = args["path"] ?: return@IdeCommandHandler "Keine Datei"
            if (ide.editor.open(path)) null else "Nicht geöffnet: $path"
        }, CATEGORY)
        c.register("gitSummary", "Git-Zustand", IdeCommandHandler { TextOps.gitSummary(ide.git.status()) }, CATEGORY)
        c.register("note", "Notiz in NOTES.md", IdeCommandHandler { args -> appendNote(args["path"]) }, CATEGORY)
        c.register("minimap", "Minimap umschalten", IdeCommandHandler {
            val on = ide.settings.get(MINIMAP) == "true"
            ide.settings.set(MINIMAP, (!on).toString())
            "Minimap " + if (on) "aus" else "an"
        }, CATEGORY)
    }

    private fun onEvent(e: IdeEvent) {
        val filter = context.settings().getString("eventFilter")
        val shown = when (filter) {
            "file" -> e.topic.startsWith("file.")
            "git" -> e.topic.startsWith("git.")
            else -> true
        }
        if (shown) record("${e.topic} ${e.payload.entries.joinToString { "${it.key}=${it.value}" }} (${e.source})")
        if (e.topic.startsWith("git.") || e.topic == "file.saved" || e.topic == "project.opened") refreshStatus()
    }

    fun record(line: String) {
        log.add("${time.format(Date())}  $line")
        _logVersion.value++
    }

    /** Git-Zustand in der Statusleiste; Klick öffnet den Projektbaum. */
    fun refreshStatus() {
        if (!context.settings().getBoolean("statusBar")) {
            ide.statusBar.remove(STATUS)
            return
        }
        scope.launch {
            val text = runCatching { "⎇ " + TextOps.gitSummary(ide.git.status()) }.getOrElse { "⎇ –" }
            ide.statusBar.set(STATUS, text, "Werkzeugkasten: Projektbaum öffnen", "$pluginId.tree", priority = 1)
        }
    }

    /** Hängt eine Zeile an NOTES.md im Projekt an und meldet das als eigenes Ereignis. */
    suspend fun appendNote(about: String?): String {
        val root = ide.files.projectRoot() ?: return "Kein Projekt geöffnet"
        val notes = "$root/NOTES.md"
        val old = if (ide.files.exists(notes)) ide.files.read(notes) else "# Notizen\n"
        val target = about ?: ide.editor.snapshot().path ?: "Projekt"
        ide.files.write(notes, old.trimEnd() + "\n- ${time.format(Date())} ${target.removePrefix("$root/")}\n")
        ide.events.publish("note", mapOf("path" to notes))
        return "Notiz gespeichert"
    }

    /** Alles bereitstellen und committen – mit Rückfrage, Fortschritt und Benachrichtigung. */
    suspend fun commitAll(message: String): String {
        val status = ide.git.status()
        val files = status.changes.keys.toList()
        if (files.isEmpty() && status.staged.isEmpty()) return "Nichts zu committen"
        val ui = context.feedback()
        if (!ui.confirm("Committen?", "${files.size + status.staged.size} Dateien: „$message“", "Committen")) return "Abgebrochen"
        val hash = ui.withProgress("Commit …") { p ->
            p.report("Bereitstellen", 30)
            if (files.isNotEmpty()) ide.git.stage(files)
            p.report("Commit", 70)
            ide.git.commit(message)
        }
        runCatching { ui.notify("Commit erstellt", "$hash – $message") }
        refreshStatus()
        return "Commit $hash"
    }

    companion object {
        const val CATEGORY = "Werkzeugkasten"
        const val STATUS = "git"
        const val MINIMAP = "editor.minimap"
    }
}
