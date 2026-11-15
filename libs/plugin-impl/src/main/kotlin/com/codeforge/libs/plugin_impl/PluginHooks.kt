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
import com.codeforge.core.domain.extension.registry.Disposable
import com.codeforge.core.domain.extension.registry.ExtensionPoint
import com.codeforge.core.domain.extension.registry.ExtensionRegistry
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
class PluginHooks @Inject constructor(
    private val registry: ExtensionRegistry
) : PluginUiRepository, PluginEventSink {

    internal class Cmd(val id: String, val title: String, val handler: PluginCommandHandler)
    internal class Listener(val apiVersion: Int, val granted: Set<String>, val listener: PluginEventListener)

    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Befehle, Menüs und Listener liegen im gemeinsamen ExtensionRegistry; hier nur die Handles zum Ersetzen.
    private val commandHandles = HashMap<Pair<String, String>, Disposable>()
    private val menuHandles = HashMap<Pair<String, String>, Disposable>()

    private val _commands = MutableStateFlow<List<PluginCommandInfo>>(emptyList())
    override val commands: StateFlow<List<PluginCommandInfo>> = _commands.asStateFlow()

    private val _menuEntries = MutableStateFlow<List<PluginMenuEntry>>(emptyList())
    override val menuEntries: StateFlow<List<PluginMenuEntry>> = _menuEntries.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun republish() {
        _commands.value = registry.contributions(COMMAND_POINT).map { PluginCommandInfo(it.pluginId, it.value.id, it.value.title) }
        _menuEntries.value = registry.extensions(MENU_POINT)
    }

    private fun commandsOf(pluginId: String) = registry.contributions(COMMAND_POINT).filter { it.pluginId == pluginId }

    /**
     * Registriert einen Befehl eines Plugins; ein vorhandener Befehl mit gleicher [id] wird ersetzt.
     *
     * @param pluginId Plugin, dem der Befehl gehört.
     * @param id Befehls-ID (nicht leer, begrenzte Länge).
     * @param title angezeigter Titel.
     * @param handler wird bei Aufruf ausgeführt.
     * @throws IllegalArgumentException bei ungültiger ID.
     */
    fun registerCommand(pluginId: String, id: String, title: String, handler: PluginCommandHandler) {
        require(id.isNotBlank() && id.length <= MAX_ID) { "Ungültige Befehls-ID" }
        require(title.isNotBlank() && title.length <= MAX_TITLE) { "Ungültiger Befehlstitel" }
        synchronized(lock) {
            val key = pluginId to id
            require(commandsOf(pluginId).size < MAX_COMMANDS || key in commandHandles) {
                "Zu viele Befehle (max. $MAX_COMMANDS je Plugin)"
            }
            commandHandles.remove(key)?.dispose()
            commandHandles[key] = registry.register(COMMAND_POINT, Cmd(id, title, handler), pluginId)
            republish()
        }
    }

    /**
     * Hängt einen registrierten Befehl in ein App-Menü ein.
     *
     * @param pluginId Plugin, dem der Eintrag gehört.
     * @param id Eintrags-ID.
     * @param title angezeigter Titel.
     * @param target Menü, in dem der Eintrag erscheint.
     * @param commandId ID des auszuführenden Befehls.
     */
    fun registerMenuItem(pluginId: String, id: String, title: String, target: PluginMenuTarget, commandId: String) {
        require(id.isNotBlank() && id.length <= MAX_ID) { "Ungültige Menü-ID" }
        require(title.isNotBlank() && title.length <= MAX_TITLE) { "Ungültiger Menütitel" }
        synchronized(lock) {
            require(commandsOf(pluginId).any { it.value.id == commandId }) { "Unbekannter Befehl: $commandId" }
            val key = pluginId to id
            menuHandles.remove(key)?.dispose()
            require(registry.extensions(MENU_POINT).count { it.pluginId == pluginId } < MAX_COMMANDS) { "Zu viele Menüeinträge" }
            menuHandles[key] = registry.register(MENU_POINT, PluginMenuEntry(pluginId, id, title, target, commandId), pluginId)
            republish()
        }
    }

    /**
     * Registriert einen Ereignis-Listener; zugestellt werden nur Ereignisse, für die [granted] die Berechtigung enthält.
     *
     * @param pluginId Plugin des Listeners.
     * @param apiVersion API-Version des Plugins (Version 1 hat die Ereignisberechtigungen implizit).
     * @param granted gewährte Berechtigungen.
     * @param listener Empfänger.
     */
    fun addListener(pluginId: String, apiVersion: Int, granted: Set<String>, listener: PluginEventListener) {
        registry.register(LISTENER_POINT, Listener(apiVersion, granted, listener), pluginId)
    }

    /**
     * Meldet dem Nutzer eine kurze Nachricht (wird auf die maximale Länge gekürzt).
     *
     * @param text Nachrichtentext.
     */
    fun postMessage(text: String) {
        _messages.tryEmit(text.take(MAX_MESSAGE))
    }

    /** Entfernt alles, was [pluginId] registriert hat (beim Deaktivieren/Deinstallieren). */
    fun unregisterAll(pluginId: String) {
        synchronized(lock) {
            registry.unregisterAll(pluginId)
            commandHandles.keys.removeAll { it.first == pluginId }
            menuHandles.keys.removeAll { it.first == pluginId }
            republish()
        }
    }

    override fun publish(event: HostEvent) {
        val targets = registry.contributions(LISTENER_POINT)
            .filter { PluginPermissionPolicy.isAllowed(it.value.apiVersion, it.value.granted, event.permission) }
        if (targets.isEmpty()) return
        val sdkEvent = event.toSdk()
        for (t in targets) {
            scope.launch {
                runCatching { t.value.listener.onEvent(sdkEvent) }
                    .onFailure { e -> runCatching { android.util.Log.w("PluginHooks", "Listener von ${t.pluginId} fehlgeschlagen", e) } }
            }
        }
    }

    override suspend fun runCommand(pluginId: String, commandId: String, activeFilePath: String?): Result<Unit> {
        val cmd = commandsOf(pluginId).firstOrNull { it.value.id == commandId }?.value
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
        /** Befehle der Plugins. */
        val COMMAND_POINT = ExtensionPoint<Cmd>("plugin.command")

        /** Menüeinträge der Plugins. */
        val MENU_POINT = ExtensionPoint<PluginMenuEntry>("plugin.menu")

        /** Ereignis-Empfänger der Plugins. */
        val LISTENER_POINT = ExtensionPoint<Listener>("plugin.eventListener")

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
