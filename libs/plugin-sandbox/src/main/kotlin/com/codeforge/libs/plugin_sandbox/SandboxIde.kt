/**
 * Modul: :libs:plugin-sandbox
 *
 * Plugin-API 5 in der Sandbox: eigene Befehle, Ereignisbus und Statusleiste. Der Sandbox-Prozess kann die App nicht
 * selbst aufrufen; was ein Plugin tut, landet in einer Ausgangspost, die jede Antwort an die App mitnimmt
 * (`"outbox"`). Die App ruft Befehle (`op: command`) und Ereignisse (`op: event`) umgekehrt per Anfrage auf und holt
 * die Post regelmäßig ab (`op: flush`).
 */
package com.codeforge.libs.plugin_sandbox

import com.codeforge.core.domain.extension.ide.IdeCommandHandler
import com.codeforge.core.domain.extension.ide.IdeCommandInfo
import com.codeforge.core.domain.extension.ide.IdeEditorSnapshot
import com.codeforge.core.domain.extension.ide.IdeEvent
import com.codeforge.core.domain.extension.ide.IdeNavTarget
import com.codeforge.core.domain.extension.ide.IdeSettingInfo
import com.codeforge.core.domain.extension.ide.IdeTextEdit
import com.codeforge.core.domain.extension.registry.Disposable
import com.codeforge.libs.plugin_api.IdeApi
import com.codeforge.libs.plugin_api.IdeCommandsApi
import com.codeforge.libs.plugin_api.IdeEditorApi
import com.codeforge.libs.plugin_api.IdeEventsApi
import com.codeforge.libs.plugin_api.IdeFileEntry
import com.codeforge.libs.plugin_api.IdeFilesApi
import com.codeforge.libs.plugin_api.IdeGitApi
import com.codeforge.libs.plugin_api.IdeGitStatus
import com.codeforge.libs.plugin_api.IdeNavigationApi
import com.codeforge.libs.plugin_api.IdeSettingsApi
import com.codeforge.libs.plugin_api.IdeStatusBarApi
import com.codeforge.libs.plugin_api.PluginPermissionException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Was die App mit der Ausgangspost eines Sandbox-Plugins tut (Implementierung prüft die Berechtigungen). */
interface SandboxIdeSink {
    /** Neues Sandbox-Plugin mit [permissions]. */
    fun bind(pluginId: String, permissions: Set<String>)

    /** Befehl `<pluginId>.<id>` anmelden; [invoke] ruft ihn in der Sandbox auf. */
    fun registerCommand(pluginId: String, id: String, title: String, invoke: suspend (Map<String, String>) -> String?)

    /** Befehl `<pluginId>.<id>` abmelden. */
    fun unregisterCommand(pluginId: String, id: String)

    /** Ereignis `plugin.<pluginId>.<name>` veröffentlichen. */
    fun publish(pluginId: String, name: String, payload: Map<String, String>)

    /** Ereignisse mit [prefix] an die Sandbox weiterleiten ([deliver] schickt sie hin). */
    fun subscribe(pluginId: String, prefix: String, deliver: (IdeEvent) -> Unit)

    /** Statuseintrag [id] setzen oder ändern. */
    fun setStatus(pluginId: String, id: String, text: String, tooltip: String?, commandId: String?, priority: Int)

    /** Statuseintrag [id] entfernen. */
    fun removeStatus(pluginId: String, id: String)

    /** Plugin entladen: alles Angemeldete entfernen. */
    fun release(pluginId: String)
}

/** Ausgangspost aller Plugins im Sandbox-Prozess. */
internal class SandboxOutbox {
    private val queue = ConcurrentLinkedQueue<JsonObject>()

    fun post(pluginId: String, kind: String, body: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) {
        if (queue.size >= MAX) return
        queue += buildJsonObject { put("pluginId", pluginId); put("kind", kind); body() }
    }

    /** Leert die Post (für die Antwort). */
    fun drain(): JsonArray = buildJsonArray {
        while (true) add(queue.poll() ?: break)
    }

    private companion object {
        const val MAX = 500
    }
}

/** API 5 eines Sandbox-Plugins: Befehle, Ereignisse, Statusleiste; alles andere ist hier gesperrt. */
internal class SandboxIdeApi(private val pluginId: String, private val outbox: SandboxOutbox) : IdeApi {

    private val counter = AtomicInteger()

    /** Befehle (ID → Handler) für `op: command`. */
    val commandHandlers = ConcurrentHashMap<String, IdeCommandHandler>()

    private val listeners = CopyOnWriteArrayList<Pair<String, (IdeEvent) -> Unit>>()

    override val commands: IdeCommandsApi = object : IdeCommandsApi {
        override fun list(): List<IdeCommandInfo> = denied("commands.list")
        override suspend fun execute(id: String, args: Map<String, String>): String? = denied("commands.execute")

        override fun register(id: String, title: String, handler: IdeCommandHandler, category: String): Disposable {
            commandHandlers[id] = handler
            outbox.post(pluginId, "registerCommand") { put("id", id); put("title", title.take(MAX_TITLE)) }
            return Disposable {
                commandHandlers.remove(id)
                outbox.post(pluginId, "unregisterCommand") { put("id", id) }
            }
        }
    }

    override val events: IdeEventsApi = object : IdeEventsApi {
        override fun subscribe(topicPrefix: String, listener: (IdeEvent) -> Unit): Disposable {
            val entry = topicPrefix to listener
            listeners += entry
            outbox.post(pluginId, "subscribe") { put("prefix", topicPrefix); put("n", counter.incrementAndGet()) }
            return Disposable { listeners.remove(entry) }
        }

        override fun publish(name: String, payload: Map<String, String>) =
            outbox.post(pluginId, "publish") { put("name", name); put("payload", JsonObject(payload.mapValues { JsonPrimitive(it.value) })) }
    }

    override val statusBar: IdeStatusBarApi = object : IdeStatusBarApi {
        override fun set(id: String, text: String, tooltip: String?, commandId: String?, priority: Int) =
            outbox.post(pluginId, "status") {
                put("id", id); put("text", text); tooltip?.let { put("tooltip", it) }; commandId?.let { put("command", it) }
                put("priority", priority)
            }

        override fun remove(id: String) = outbox.post(pluginId, "statusRemove") { put("id", id) }
    }

    override val settings: IdeSettingsApi = object : IdeSettingsApi {
        override fun keys(): List<IdeSettingInfo> = denied(SETTINGS)
        override suspend fun get(key: String): String? = denied(SETTINGS)
        override suspend fun set(key: String, value: String): Boolean = denied(SETTINGS)
        override fun onChange(listener: (key: String) -> Unit): Disposable = denied(SETTINGS)
    }

    override val editor: IdeEditorApi = object : IdeEditorApi {
        override fun snapshot(): IdeEditorSnapshot = denied("editor (Editor-Aktionen nutzen)")
        override suspend fun open(path: String, line: Int, column: Int): Boolean = denied(EDITOR)
        override suspend fun applyEdits(path: String?, edits: List<IdeTextEdit>): Boolean = denied(EDITOR)
        override suspend fun select(start: Int, end: Int): Boolean = denied(EDITOR)
        override suspend fun save(path: String?): Boolean = denied(EDITOR)
        override suspend fun close(path: String): Boolean = denied(EDITOR)
    }

    override val files: IdeFilesApi = object : IdeFilesApi {
        override fun projectRoot(): String? = null
        override fun list(dir: String): List<IdeFileEntry> = denied(FILES)
        override fun exists(path: String): Boolean = denied(FILES)
        override fun read(path: String): String = denied(FILES)
        override suspend fun write(path: String, text: String) = denied(FILES)
        override suspend fun mkdirs(path: String) = denied(FILES)
        override suspend fun rename(from: String, to: String) = denied(FILES)
        override suspend fun delete(path: String) = denied(FILES)
    }

    override val git: IdeGitApi = object : IdeGitApi {
        override suspend fun status(): IdeGitStatus = denied(GIT)
        override suspend fun diff(path: String?, staged: Boolean): String = denied(GIT)
        override suspend fun branches(): List<String> = denied(GIT)
        override suspend fun stage(paths: List<String>) = denied(GIT)
        override suspend fun commit(message: String): String = denied(GIT)
        override suspend fun checkout(branch: String) = denied(GIT)
    }

    override val navigation: IdeNavigationApi = object : IdeNavigationApi {
        override fun navigate(target: IdeNavTarget) = denied("navigation")
    }

    /** Liefert ein Ereignis aus der App an die passenden Listener. */
    fun deliver(event: IdeEvent) {
        listeners.filter { event.topic.startsWith(it.first) }.forEach { runCatching { it.second(event) } }
    }

    private fun denied(what: String): Nothing = throw PluginPermissionException(pluginId, "$what$NOT_IN_SANDBOX")

    companion object {
        private const val MAX_TITLE = 120
        private const val NOT_IN_SANDBOX = " (in der Sandbox nicht verfügbar)"
        private const val SETTINGS = "settings"
        private const val EDITOR = "editor"
        private const val FILES = "files"
        private const val GIT = "git"

        /** Ereignis aus der Anfrage `op: event`. */
        fun eventOf(req: JsonObject): IdeEvent = IdeEvent(
            topic = (req["topic"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            payload = (req["payload"] as? JsonObject)?.mapValues { (it.value as? JsonPrimitive)?.contentOrNull.orEmpty() }.orEmpty(),
            source = (req["source"] as? JsonPrimitive)?.contentOrNull ?: IdeEvent.SOURCE_IDE
        )
    }
}
