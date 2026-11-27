// Modul: :libs:plugin-impl
package com.codeforge.libs.plugin_impl.ide

import com.codeforge.core.domain.extension.PluginPermissionPolicy
import com.codeforge.core.domain.extension.PluginPermissions
import com.codeforge.core.domain.extension.ide.IdeCommandHandler
import com.codeforge.core.domain.extension.ide.IdeCommandInfo
import com.codeforge.core.domain.extension.ide.IdeCommandRegistry
import com.codeforge.core.domain.extension.ide.IdeEditorBridge
import com.codeforge.core.domain.extension.ide.IdeEditorRequest
import com.codeforge.core.domain.extension.ide.IdeEditorSnapshot
import com.codeforge.core.domain.extension.ide.IdeEvent
import com.codeforge.core.domain.extension.ide.IdeEventBus
import com.codeforge.core.domain.extension.ide.IdeNavTarget
import com.codeforge.core.domain.extension.ide.IdeNavigator
import com.codeforge.core.domain.extension.ide.IdeSettingInfo
import com.codeforge.core.domain.extension.ide.IdeSettingsAccess
import com.codeforge.core.domain.extension.ide.IdeStatusBar
import com.codeforge.core.domain.extension.ide.IdeStatusItem
import com.codeforge.core.domain.extension.ide.IdeTextEdit
import com.codeforge.core.domain.extension.ide.IdeTopics
import com.codeforge.core.domain.extension.registry.Disposable
import com.codeforge.core.domain.repository.GitRepository
import com.codeforge.core.navigation.FileSyncBridge
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Alles, was die API 5 auf der Host-Seite braucht (eine Instanz je App-Prozess). */
@Singleton
class IdeHost @Inject constructor(
    val commands: IdeCommandRegistry,
    val bus: IdeEventBus,
    val settings: IdeSettingsAccess,
    val editor: IdeEditorBridge,
    val navigator: IdeNavigator,
    val statusBar: IdeStatusBar,
    private val git: Provider<GitRepository>,
    val fileSync: FileSyncBridge
) {
    /** Git-Repository (lazy, damit Tests ohne Git-Implementierung auskommen). */
    fun gitRepository(): GitRepository = git.get()
}

/**
 * API 5 eines Plugins: prüft je Aufruf die Berechtigung und merkt sich alles Angemeldete, damit [close] es beim
 * Entladen entfernt. [projectRoot] liefert die Wurzel des geöffneten Projekts.
 */
class HostIdeApi(
    private val pluginId: String,
    private val granted: Set<String>,
    private val host: IdeHost,
    private val projectRoot: () -> String?
) : IdeApi {

    private val disposables = CopyOnWriteArrayList<Disposable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val commands: IdeCommandsApi = object : IdeCommandsApi {
        override fun list(): List<IdeCommandInfo> {
            require(PluginPermissions.COMMANDS_EXECUTE)
            return host.commands.commands.value
        }

        override suspend fun execute(id: String, args: Map<String, String>): String? {
            require(PluginPermissions.COMMANDS_EXECUTE)
            return host.commands.execute(id, args).getOrThrow()
        }

        override fun register(id: String, title: String, handler: IdeCommandHandler, category: String): Disposable {
            require(PluginPermissions.COMMANDS)
            val info = IdeCommandInfo("$pluginId.$id", title.take(MAX_TITLE), category.take(MAX_TITLE), pluginId)
            return track(host.commands.register(info, handler))
        }
    }

    override val events: IdeEventsApi = object : IdeEventsApi {
        override fun subscribe(topicPrefix: String, listener: (IdeEvent) -> Unit): Disposable {
            require(PluginPermissions.EVENTS)
            return track(host.bus.subscribe(topicPrefix, listener))
        }

        override fun publish(name: String, payload: Map<String, String>) {
            require(PluginPermissions.EVENTS)
            require(name.isNotBlank() && name.length <= MAX_TITLE) { "Ungültiger Ereignisname" }
            host.bus.publish(IdeEvent("${IdeTopics.PLUGIN_PREFIX}$pluginId.$name", payload.entries.take(MAX_PAYLOAD).associate { it.toPair() }, pluginId))
        }
    }

    override val settings: IdeSettingsApi = object : IdeSettingsApi {
        override fun keys(): List<IdeSettingInfo> {
            require(PluginPermissions.SETTINGS_READ)
            return host.settings.keys()
        }

        override suspend fun get(key: String): String? {
            require(PluginPermissions.SETTINGS_READ)
            return host.settings.get(key)
        }

        override suspend fun set(key: String, value: String): Boolean {
            require(PluginPermissions.SETTINGS_WRITE)
            return host.settings.set(key, value)
        }

        override fun onChange(listener: (key: String) -> Unit): Disposable {
            require(PluginPermissions.SETTINGS_READ)
            val job = scope.launch { host.settings.changes.collect { key -> runCatching { listener(key) } } }
            return track(Disposable { job.cancel() })
        }
    }

    override val editor: IdeEditorApi = object : IdeEditorApi {
        override fun snapshot(): IdeEditorSnapshot {
            require(PluginPermissions.EDITOR)
            return host.editor.snapshot.value
        }

        override suspend fun open(path: String, line: Int, column: Int): Boolean {
            require(PluginPermissions.EDITOR)
            return host.editor.request(IdeEditorRequest.Open(resolve(path).path, line, column))
        }

        override suspend fun applyEdits(path: String?, edits: List<IdeTextEdit>): Boolean {
            require(PluginPermissions.EDITOR)
            return host.editor.request(IdeEditorRequest.ApplyEdits(path?.let { resolve(it).path }, edits.take(MAX_EDITS)))
        }

        override suspend fun select(start: Int, end: Int): Boolean {
            require(PluginPermissions.EDITOR)
            return host.editor.request(IdeEditorRequest.Select(start, end))
        }

        override suspend fun save(path: String?): Boolean {
            require(PluginPermissions.EDITOR)
            return host.editor.request(IdeEditorRequest.Save(path?.let { resolve(it).path }))
        }

        override suspend fun close(path: String): Boolean {
            require(PluginPermissions.EDITOR)
            return host.editor.request(IdeEditorRequest.Close(resolve(path).path))
        }
    }

    override val files: IdeFilesApi = object : IdeFilesApi {
        override fun projectRoot(): String? = this@HostIdeApi.projectRoot()

        override fun list(dir: String): List<IdeFileEntry> {
            require(PluginPermissions.FILES_READ)
            return resolve(dir).listFiles().orEmpty().sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map { IdeFileEntry(it.path, it.name, it.isDirectory, if (it.isFile) it.length() else 0L, it.lastModified()) }
        }

        override fun exists(path: String): Boolean {
            require(PluginPermissions.FILES_READ)
            return resolve(path).exists()
        }

        override fun read(path: String): String {
            require(PluginPermissions.FILES_READ)
            val file = resolve(path)
            if (file.length() > MAX_READ) throw IOException("Datei zu groß (> ${MAX_READ / MEGABYTE} MB)")
            return file.readText()
        }

        override suspend fun write(path: String, text: String) = change(path) { it.parentFile?.mkdirs(); it.writeText(text) }
        override suspend fun mkdirs(path: String) = change(path) { it.mkdirs() }

        override suspend fun rename(from: String, to: String) {
            require(PluginPermissions.FILES_WRITE)
            val source = resolve(from)
            val target = resolve(to)
            withContext(Dispatchers.IO) {
                host.fileSync.requestFlush(setOf(source.path))
                if (!source.renameTo(target)) throw IOException("Umbenennen fehlgeschlagen: $from")
            }
            host.fileSync.notifyDirectoryChange(source.parent.orEmpty())
            host.fileSync.notifyExternalChange(listOf(source.path, target.path))
        }

        override suspend fun delete(path: String) = change(path) { if (!it.deleteRecursively()) throw IOException("Löschen fehlgeschlagen: $path") }

        private suspend fun change(path: String, block: (File) -> Unit) {
            require(PluginPermissions.FILES_WRITE)
            val file = resolve(path)
            withContext(Dispatchers.IO) { block(file) }
            host.fileSync.notifyExternalChange(listOf(file.path))
            host.fileSync.notifyDirectoryChange(file.parent.orEmpty())
        }
    }

    override val git: IdeGitApi = object : IdeGitApi {
        private fun repo(): String = projectRoot() ?: throw IOException("Kein Projekt geöffnet")

        override suspend fun status(): IdeGitStatus {
            require(PluginPermissions.GIT_READ)
            val s = host.gitRepository().status(repo()).getOrThrow()
            return IdeGitStatus(
                branch = s.branch,
                ahead = s.ahead,
                behind = s.behind,
                changes = s.entries.filterNot { it.staged }.associate { it.path to it.status.name },
                staged = s.staged.map { it.path }
            )
        }

        override suspend fun diff(path: String?, staged: Boolean): String {
            require(PluginPermissions.GIT_READ)
            return host.gitRepository().diff(repo(), path, staged).getOrThrow()
        }

        override suspend fun branches(): List<String> {
            require(PluginPermissions.GIT_READ)
            return host.gitRepository().branches(repo()).getOrThrow().filterNot { it.isRemote }.map { it.name }
        }

        override suspend fun stage(paths: List<String>) {
            require(PluginPermissions.GIT_WRITE)
            host.gitRepository().stage(repo(), paths).getOrThrow()
        }

        override suspend fun commit(message: String): String {
            require(PluginPermissions.GIT_WRITE)
            require(message.isNotBlank()) { "Leere Commit-Nachricht" }
            return host.gitRepository().commit(repo(), message).getOrThrow().also {
                host.bus.publish(IdeEvent(IdeTopics.GIT_COMMITTED, mapOf("repo" to repo(), "message" to message), pluginId))
            }
        }

        override suspend fun checkout(branch: String) {
            require(PluginPermissions.GIT_WRITE)
            host.gitRepository().checkout(repo(), branch).getOrThrow()
            host.bus.publish(IdeEvent(IdeTopics.GIT_BRANCH_CHANGED, mapOf("repo" to repo(), "branch" to branch), pluginId))
        }
    }

    override val navigation: IdeNavigationApi = object : IdeNavigationApi {
        override fun navigate(target: IdeNavTarget) {
            require(PluginPermissions.UI)
            host.navigator.navigate(if (target is IdeNavTarget.Git && target.repoPath == null) IdeNavTarget.Git(projectRoot()) else target)
        }
    }

    override val statusBar: IdeStatusBarApi = object : IdeStatusBarApi {
        override fun set(id: String, text: String, tooltip: String?, commandId: String?, priority: Int) {
            require(PluginPermissions.UI)
            host.statusBar.set(IdeStatusItem(pluginId, id, text, tooltip?.take(MAX_TITLE), commandId, priority))
        }

        override fun remove(id: String) = host.statusBar.remove(pluginId, id)
    }

    private fun require(permission: String) {
        if (!PluginPermissionPolicy.isAllowed(granted, permission)) throw PluginPermissionException(pluginId, permission)
    }

    private fun track(d: Disposable): Disposable {
        disposables += d
        return Disposable {
            d.dispose()
            disposables.remove(d)
        }
    }

    /** Entfernt Befehle, Abos und Statuseinträge des Plugins. */
    fun close() {
        disposables.forEach { runCatching { it.dispose() } }
        disposables.clear()
        host.statusBar.removeAll(pluginId)
        scope.cancel()
    }

    // Pfad im Projekt (absolut oder relativ); außerhalb wird abgelehnt.
    private fun resolve(path: String): File {
        val root = File(projectRoot() ?: throw IOException("Kein Projekt geöffnet")).canonicalFile
        val file = (if (File(path).isAbsolute) File(path) else File(root, path)).canonicalFile
        if (file != root && !file.path.startsWith(root.path + File.separator)) throw SecurityException("Außerhalb des Projekts: $path")
        return file
    }

    private companion object {
        const val MAX_TITLE = 120
        const val MAX_PAYLOAD = 50
        const val MAX_EDITS = 1_000
        const val MEGABYTE = 1024 * 1024
        const val MAX_READ = 8L * MEGABYTE
    }
}
