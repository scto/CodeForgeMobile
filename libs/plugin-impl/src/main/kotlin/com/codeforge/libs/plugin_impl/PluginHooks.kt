/**
 * Modul: :libs:plugin-impl
 * @author Thomas Schmid
 */
package com.codeforge.libs.plugin_impl

import com.codeforge.core.domain.extension.HostEvent
import com.codeforge.core.domain.extension.PluginCommandInfo
import com.codeforge.core.domain.extension.PluginEventSink
import com.codeforge.core.domain.extension.PluginMenuEntry
import com.codeforge.core.domain.extension.PluginMenuTarget
import com.codeforge.core.domain.extension.PluginPermissionPolicy
import com.codeforge.core.domain.extension.PluginUiRepository
import com.codeforge.libs.plugin_api.CommandArgs
import com.codeforge.libs.plugin_api.PluginCommandHandler
import com.codeforge.libs.plugin_api.PluginEvent
import com.codeforge.libs.plugin_api.PluginEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zentrale Anlaufstelle zwischen Code-Plugins und App-UI: hält die von Plugins registrierten Befehle,
 * Menüeinträge und Ereignis-Listener und verteilt [HostEvent]s. Fehler eines Plugins werden abgefangen
 * und nie an die App weitergereicht; ein Befehl hat ein Zeitlimit.
 */
@Singleton
class PluginHooks @Inject constructor() : PluginUiRepository, PluginEventSink {

    private class Cmd(val title: String, val handler: PluginCommandHandler)
    private class Listener(
        val pluginId: String,
        val apiVersion: Int,
        val granted: Set<String>,
        val listener: PluginEventListener
    )

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handlers = LinkedHashMap<String, LinkedHashMap<String, Cmd>>()
    private val menus = ArrayList<PluginMenuEntry>()
    private val listeners = ArrayList<Listener>()

    private val _commands = MutableStateFlow<List<PluginCommandInfo>>(emptyList())
    override val commands: StateFlow<List<PluginCommandInfo>> = _commands.asStateFlow()

    private val _menuEntries = MutableStateFlow<List<PluginMenuEntry>>(emptyList())
    override val menuEntries: StateFlow<List<PluginMenuEntry>> = _menuEntries.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun republish() {
        _commands.value = handlers.flatMap { (pid, m) -> m.map { (id, c) -> PluginCommandInfo(pid, id, c.title) } }
        _menuEntries.value = menus.toList()
    }

    fun registerCommand(pluginId: String, id: String, title: String, handler: PluginCommandHandler) {
        require(id.isNotBlank() && id.length <= MAX_ID) { "Ungültige Befehls-ID" }
        require(title.isNotBlank() && title.length <= MAX_TITLE) { "Ungültiger Befehlstitel" }
        synchronized(lock) {
            val own = handlers.getOrPut(pluginId) { LinkedHashMap() }
            require(own.size < MAX_COMMANDS || id in own) { "Zu viele Befehle (max. $MAX_COMMANDS je Plugin)" }
            own[id] = Cmd(title, handler)
            republish()
        }
    }

    fun registerMenuItem(pluginId: String, id: String, title: String, target: PluginMenuTarget, commandId: String) {
        require(id.isNotBlank() && id.length <= MAX_ID) { "Ungültige Menü-ID" }
        require(title.isNotBlank() && title.length <= MAX_TITLE) { "Ungültiger Menütitel" }
        synchronized(lock) {
            require(handlers[pluginId]?.containsKey(commandId) == true) { "Unbekannter Befehl: $commandId" }
            menus.removeAll { it.pluginId == pluginId && it.id == id }
            require(menus.count { it.pluginId == pluginId } < MAX_COMMANDS) { "Zu viele Menüeinträge" }
            menus.add(PluginMenuEntry(pluginId, id, title, target, commandId))
            republish()
        }
    }

    fun addListener(pluginId: String, apiVersion: Int, granted: Set<String>, listener: PluginEventListener) {
        synchronized(lock) { listeners.add(Listener(pluginId, apiVersion, granted, listener)) }
    }

    fun postMessage(text: String) {
        _messages.tryEmit(text.take(MAX_MESSAGE))
    }

    /** Entfernt alles, was [pluginId] registriert hat (beim Deaktivieren/Deinstallieren). */
    fun unregisterAll(pluginId: String) {
        synchronized(lock) {
            handlers.remove(pluginId)
            menus.removeAll { it.pluginId == pluginId }
            listeners.removeAll { it.pluginId == pluginId }
            republish()
        }
    }

    override fun publish(event: HostEvent) {
        val targets = synchronized(lock) {
            listeners.filter { PluginPermissionPolicy.isAllowed(it.apiVersion, it.granted, event.permission) }
        }
        if (targets.isEmpty()) return
        val sdkEvent = event.toSdk()
        for (t in targets) {
            scope.launch {
                runCatching { t.listener.onEvent(sdkEvent) }
                    .onFailure { e -> runCatching { android.util.Log.w("PluginHooks", "Listener von ${t.pluginId} fehlgeschlagen", e) } }
            }
        }
    }

    override suspend fun runCommand(pluginId: String, commandId: String, activeFilePath: String?): Result<Unit> {
        val cmd = synchronized(lock) { handlers[pluginId]?.get(commandId) }
            ?: return Result.failure(IllegalArgumentException("Befehl nicht gefunden: $pluginId/$commandId"))
        return try {
            withTimeout(COMMAND_TIMEOUT_MS) { cmd.handler.run(CommandArgs(activeFilePath)) }
            Result.success(Unit)
        } catch (e: TimeoutCancellationException) {
            Result.failure(IllegalStateException("Befehl hat das Zeitlimit überschritten"))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    internal companion object {
        const val MAX_ID = 100
        const val MAX_TITLE = 120
        const val MAX_COMMANDS = 100
        const val MAX_MESSAGE = 500
        const val COMMAND_TIMEOUT_MS = 60_000L

        fun HostEvent.toSdk(): PluginEvent = when (this) {
            is HostEvent.FileOpened -> PluginEvent.FileOpened(path)
            is HostEvent.FileSaved -> PluginEvent.FileSaved(path)
            is HostEvent.FileClosed -> PluginEvent.FileClosed(path)
            is HostEvent.GitCommitted -> PluginEvent.GitCommitted(repoPath, message)
            is HostEvent.GitBranchChanged -> PluginEvent.GitBranchChanged(repoPath, branch)
        }
    }
}
