/**
 * Modul: :core:domain
 *
 * Steuerebene der IDE für Plugin-API 5: Befehle, Ereignisbus, Editor, Navigation, Statusleiste und App-Einstellungen.
 * Features melden ihre Befehle und Zustände hier an, Plugins erreichen sie über `PluginContext.ide()`; so bleiben die
 * Features voneinander und von den Plugins entkoppelt.
 */
package com.codeforge.core.domain.extension.ide

import com.codeforge.core.domain.extension.registry.Disposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

// ---------------------------------------------------------------- Befehle

/** Ein ausführbarer Befehl der IDE oder eines Plugins ([pluginId] `null` = CodeForge Mobile selbst). */
data class IdeCommandInfo(
    val id: String,
    val title: String,
    val category: String,
    val pluginId: String? = null,
    /** Beschreibung der Argumente (Schlüssel → Bedeutung), für Plugins und den Agenten. */
    val args: Map<String, String> = emptyMap()
)

/** Ausführung eines Befehls: Argumente als Text, Ergebnis optional als Text. */
fun interface IdeCommandHandler {
    /** Führt den Befehl mit [args] aus; das Ergebnis erscheint beim Aufrufer (Palette: als Meldung). */
    suspend fun run(args: Map<String, String>): String?
}

/** Zentrales Befehlsverzeichnis: Features melden ihre Befehle an, Plugins und Menüs führen sie aus. */
interface IdeCommandRegistry {
    /** Alle angemeldeten Befehle, sortiert nach Kategorie und Titel. */
    val commands: StateFlow<List<IdeCommandInfo>>

    /** Meldet einen Befehl an; ein Befehl gleicher [IdeCommandInfo.id] wird ersetzt. */
    fun register(info: IdeCommandInfo, handler: IdeCommandHandler): Disposable

    /** Führt [id] aus; Fehler (unbekannt, Zeitüberschreitung, Ausnahme) kommen als `Result.failure`. */
    suspend fun execute(id: String, args: Map<String, String> = emptyMap()): Result<String?>
}

/** Standardregister (eins je App-Prozess). */
class IdeCommandRegistryImpl(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) : IdeCommandRegistry {
    private class Entry(val info: IdeCommandInfo, val handler: IdeCommandHandler)

    private val entries = ConcurrentHashMap<String, Entry>()
    private val _commands = MutableStateFlow<List<IdeCommandInfo>>(emptyList())
    override val commands: StateFlow<List<IdeCommandInfo>> = _commands.asStateFlow()

    override fun register(info: IdeCommandInfo, handler: IdeCommandHandler): Disposable {
        require(info.id.isNotBlank() && info.id.length <= MAX_ID) { "Ungültige Befehls-ID" }
        val entry = Entry(info, handler)
        entries[info.id] = entry
        publish()
        return Disposable {
            if (entries.remove(info.id, entry)) publish()
        }
    }

    override suspend fun execute(id: String, args: Map<String, String>): Result<String?> {
        val entry = entries[id] ?: return Result.failure(NoSuchElementException("Unbekannter Befehl: $id"))
        return runCatching { withTimeout(timeoutMs) { entry.handler.run(args) } }
    }

    private fun publish() {
        _commands.value = entries.values.map { it.info }.sortedWith(compareBy({ it.category }, { it.title }))
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 120_000L
        const val MAX_ID = 160
    }
}

// ---------------------------------------------------------------- Ereignisbus

/**
 * Ereignis auf dem Bus. [topic] in Punktschreibweise (`file.saved`, `git.committed`, `plugin.<id>.<name>`),
 * [payload] als Text, [source] `cfm` oder die Plugin-ID.
 */
data class IdeEvent(val topic: String, val payload: Map<String, String> = emptyMap(), val source: String = SOURCE_IDE) {
    companion object {
        const val SOURCE_IDE = "cfm"
    }
}

/** Ereignisbus der IDE: Features und Plugins veröffentlichen, beide abonnieren. */
interface IdeEventBus {
    /** Alle Ereignisse (für Abonnenten mit Coroutinen). */
    val events: SharedFlow<IdeEvent>

    /** Veröffentlicht [event]; blockiert nicht (verwirft bei vollem Puffer). */
    fun publish(event: IdeEvent)

    /** Ruft [listener] für Themen auf, die mit [topicPrefix] beginnen (leer = alle); Fehler im Listener werden geschluckt. */
    fun subscribe(topicPrefix: String, listener: (IdeEvent) -> Unit): Disposable
}

/** Standardbus (eins je App-Prozess). */
class IdeEventBusImpl : IdeEventBus {
    private class Sub(val prefix: String, val listener: (IdeEvent) -> Unit)

    private val subs = CopyOnWriteArrayList<Sub>()
    private val _events = MutableSharedFlow<IdeEvent>(extraBufferCapacity = BUFFER)
    override val events: SharedFlow<IdeEvent> = _events.asSharedFlow()

    override fun publish(event: IdeEvent) {
        _events.tryEmit(event)
        subs.filter { event.topic.startsWith(it.prefix) }.forEach { runCatching { it.listener(event) } }
    }

    override fun subscribe(topicPrefix: String, listener: (IdeEvent) -> Unit): Disposable {
        val sub = Sub(topicPrefix, listener)
        subs += sub
        return Disposable { subs.remove(sub) }
    }

    private companion object {
        const val BUFFER = 64
    }
}

/** Themen, die CodeForge Mobile selbst veröffentlicht. */
object IdeTopics {
    const val FILE_OPENED = "file.opened"
    const val FILE_SAVED = "file.saved"
    const val FILE_CLOSED = "file.closed"
    const val EDITOR_ACTIVATED = "editor.activated"
    const val DIAGNOSTICS_CHANGED = "editor.diagnosticsChanged"
    const val GIT_COMMITTED = "git.committed"
    const val GIT_BRANCH_CHANGED = "git.branchChanged"
    const val BUILD_STARTED = "build.started"
    const val BUILD_FINISHED = "build.finished"
    const val PROJECT_OPENED = "project.opened"
    const val PROJECT_CLOSED = "project.closed"
    const val SETTINGS_CHANGED = "settings.changed"
    const val COMMAND_EXECUTED = "command.executed"
    const val PLUGIN_PREFIX = "plugin."
}

// ---------------------------------------------------------------- Editor

/** Zustand des aktiven Editors. */
data class IdeEditorSnapshot(
    val path: String? = null,
    val text: String = "",
    val caret: Int = 0,
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    val dirty: Boolean = false,
    val openFiles: List<String> = emptyList()
)

/** Textänderung im Editor: [start] bis [end] (Zeichen) durch [newText] ersetzen. */
data class IdeTextEdit(val start: Int, val end: Int, val newText: String)

/** Auftrag an den Editor; [done] liefert `true` bei Erfolg. */
sealed interface IdeEditorRequest {
    val done: CompletableDeferred<Boolean>

    /** Datei öffnen, Cursor auf Zeile/Spalte (0-basiert). */
    data class Open(val path: String, val line: Int = 0, val column: Int = 0) : IdeEditorRequest {
        override val done: CompletableDeferred<Boolean> = CompletableDeferred()
    }

    /** Ändert die geöffnete Datei [path] (`null` = aktive Datei). */
    data class ApplyEdits(val path: String?, val edits: List<IdeTextEdit>) : IdeEditorRequest {
        override val done: CompletableDeferred<Boolean> = CompletableDeferred()
    }

    /** Auswahl in der aktiven Datei setzen. */
    data class Select(val start: Int, val end: Int) : IdeEditorRequest {
        override val done: CompletableDeferred<Boolean> = CompletableDeferred()
    }

    /** Speichert [path] (`null` = alle ungespeicherten). */
    data class Save(val path: String?) : IdeEditorRequest {
        override val done: CompletableDeferred<Boolean> = CompletableDeferred()
    }

    /** Datei schließen. */
    data class Close(val path: String) : IdeEditorRequest {
        override val done: CompletableDeferred<Boolean> = CompletableDeferred()
    }
}

/** Verbindung zum Editor: der Editor meldet seinen Zustand und bearbeitet Aufträge. */
interface IdeEditorBridge {
    val snapshot: StateFlow<IdeEditorSnapshot>
    val requests: SharedFlow<IdeEditorRequest>

    /** Vom Editor: aktueller Zustand. */
    fun publish(snapshot: IdeEditorSnapshot)

    /** Schickt [request] an den Editor und wartet auf das Ergebnis (`false`, wenn kein Editor antwortet). */
    suspend fun request(request: IdeEditorRequest): Boolean
}

/** Standardbrücke (eine je App-Prozess). */
class IdeEditorBridgeImpl(private val timeoutMs: Long = 10_000L) : IdeEditorBridge {
    private val _snapshot = MutableStateFlow(IdeEditorSnapshot())
    override val snapshot: StateFlow<IdeEditorSnapshot> = _snapshot.asStateFlow()
    private val _requests = MutableSharedFlow<IdeEditorRequest>(extraBufferCapacity = 16)
    override val requests: SharedFlow<IdeEditorRequest> = _requests.asSharedFlow()

    override fun publish(snapshot: IdeEditorSnapshot) {
        _snapshot.value = snapshot
    }

    override suspend fun request(request: IdeEditorRequest): Boolean {
        if (_requests.subscriptionCount.value == 0 || !_requests.tryEmit(request)) return false
        return runCatching { withTimeout(timeoutMs) { request.done.await() } }.getOrDefault(false)
    }
}

// ---------------------------------------------------------------- Navigation

/** Ziele, die Plugins ansteuern dürfen. */
sealed interface IdeNavTarget {
    /** Einstellungen; [page] wie in [IdeSettingsPages] (`null` = Übersicht). */
    data class Settings(val page: String? = null) : IdeNavTarget
    /** Terminal, optional mit Startbefehl. */
    data class Terminal(val command: String? = null) : IdeNavTarget
    /** Git-Ansicht von [repoPath] (`null` = geöffnetes Projekt). */
    data class Git(val repoPath: String? = null) : IdeNavTarget
    /** Build-Panel (Ausgabe, Tests, Probleme). */
    data object BuildPanel : IdeNavTarget
    /** Eigener Bildschirm eines Plugins (UI-API). */
    data class PluginScreen(val pluginId: String, val screenId: String) : IdeNavTarget
    /** Werkzeugfenster eines Plugins (UI-API) öffnen. */
    data class PluginToolWindow(val pluginId: String, val toolWindowId: String) : IdeNavTarget
}

/** Seitennamen für [IdeNavTarget.Settings]. */
object IdeSettingsPages {
    val ALL: Set<String> = setOf(
        "theme", "editor", "terminal", "git", "lsp", "formatters", "sdk", "ai", "store", "packages", "build",
        "keymap", "symbols", "colorScheme", "codeStyle", "composePreview", "debug", "about", "plugins"
    )
}

/** Navigationswünsche an die App (der NavHost führt sie aus). */
interface IdeNavigator {
    val requests: SharedFlow<IdeNavTarget>

    /** Wunsch, [target] zu öffnen. */
    fun navigate(target: IdeNavTarget)
}

/** Standardnavigator (einer je App-Prozess). */
class IdeNavigatorImpl : IdeNavigator {
    private val _requests = MutableSharedFlow<IdeNavTarget>(extraBufferCapacity = 8)
    override val requests: SharedFlow<IdeNavTarget> = _requests.asSharedFlow()
    override fun navigate(target: IdeNavTarget) {
        _requests.tryEmit(target)
    }
}

// ---------------------------------------------------------------- Statusleiste

/** Eintrag der Statusleiste unter dem Editor; Tippen führt [commandId] aus. */
data class IdeStatusItem(
    val pluginId: String,
    val id: String,
    val text: String,
    val tooltip: String? = null,
    val commandId: String? = null,
    /** Höher = weiter links. */
    val priority: Int = 0
)

/** Statusleiste: Plugins setzen und entfernen Einträge. */
interface IdeStatusBar {
    val items: StateFlow<List<IdeStatusItem>>

    /** Setzt oder ersetzt den Eintrag. */
    fun set(item: IdeStatusItem)

    /** Entfernt einen Eintrag. */
    fun remove(pluginId: String, id: String)

    /** Entfernt alle Einträge eines Plugins. */
    fun removeAll(pluginId: String)
}

/** Standardleiste (eine je App-Prozess). */
class IdeStatusBarImpl(private val maxPerPlugin: Int = 5) : IdeStatusBar {
    private val _items = MutableStateFlow<List<IdeStatusItem>>(emptyList())
    override val items: StateFlow<List<IdeStatusItem>> = _items.asStateFlow()

    override fun set(item: IdeStatusItem) = _items.update { list ->
        val others = list.filterNot { it.pluginId == item.pluginId && it.id == item.id }
        require(others.count { it.pluginId == item.pluginId } < maxPerPlugin) { "Höchstens $maxPerPlugin Einträge je Plugin" }
        (others + item.copy(text = item.text.take(MAX_TEXT))).sortedByDescending { it.priority }
    }

    override fun remove(pluginId: String, id: String) = _items.update { l -> l.filterNot { it.pluginId == pluginId && it.id == id } }
    override fun removeAll(pluginId: String) = _items.update { l -> l.filterNot { it.pluginId == pluginId } }

    private companion object {
        const val MAX_TEXT = 60
    }
}

// ---------------------------------------------------------------- App-Einstellungen

/** Beschreibung einer freigegebenen App-Einstellung. */
data class IdeSettingInfo(val key: String, val type: String, val description: String)

/** Zugriff auf ausgewählte Einstellungen von CodeForge Mobile (Schlüssel in Punktschreibweise, Werte als Text). */
interface IdeSettingsAccess {
    /** Geänderte Schlüssel (jede Änderung, auch durch den Nutzer). */
    val changes: Flow<String>

    /** Freigegebene Schlüssel. */
    fun keys(): List<IdeSettingInfo>

    /** Aktueller Wert oder `null` bei unbekanntem Schlüssel. */
    suspend fun get(key: String): String?

    /** Setzt [key]; `false` bei unbekanntem Schlüssel oder ungültigem Wert. */
    suspend fun set(key: String, value: String): Boolean
}
