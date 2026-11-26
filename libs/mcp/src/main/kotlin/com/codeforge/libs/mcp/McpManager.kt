/**
 * Modul: :libs:mcp
 * @author Thomas Schmid
 */
package com.codeforge.libs.mcp

import com.codeforge.libs.agent.AgentTool
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/** Woher ein Server-Eintrag stammt: aus der App (vom Nutzer angelegt) oder aus einer Projektdatei (`.mcp.json`). */
enum class McpSource { APP, PROJECT }

/** Ein Eintrag samt Herkunft; Projekt-Server verbinden erst nach ausdrücklicher Freigabe ([trusted]). */
data class McpServerEntry(val config: McpServerConfig, val source: McpSource, val trusted: Boolean)

/** Verbindungszustand eines Servers. */
enum class McpStatus { DISABLED, NEEDS_TRUST, NEEDS_AUTH, CONNECTING, READY, FAILED }

/** Zustand eines Servers für die Oberfläche. */
data class McpServerState(
    val entry: McpServerEntry,
    val status: McpStatus,
    val toolCount: Int = 0,
    val message: String? = null,
    val instructions: String? = null,
    /** Herausforderung des Servers bei [McpStatus.NEEDS_AUTH] (für die Anmeldung). */
    val wwwAuthenticate: String? = null,
    /** `true`, wenn für den Server eine Anmeldung gespeichert ist. */
    val signedIn: Boolean = false,
)

/** Baut den Transport eines Servers. */
fun interface McpTransportFactory {
    /** Transport für [config]. */
    fun create(config: McpServerConfig): McpTransport
}

/** Standardfabrik: HTTP (mit Rückfall), SSE und stdio über [launcher]. */
class DefaultMcpTransportFactory(private val launcher: McpProcessLauncher?) : McpTransportFactory {
    override fun create(config: McpServerConfig): McpTransport = when (config.transport) {
        McpTransportKind.HTTP -> FallbackHttpTransport(config.url.orEmpty(), config.headers)
        McpTransportKind.SSE -> LegacySseTransport(config.url.orEmpty(), config.headers)
        McpTransportKind.STDIO -> StdioTransport(
            config.command.orEmpty(),
            config.args,
            config.env,
            launcher ?: throw IOException("stdio-Server werden auf diesem Gerät nicht unterstützt (keine Termux-Umgebung)."),
        )
    }
}

/**
 * Hält die Verbindungen zu allen konfigurierten Servern im Gleichklang mit der Konfiguration: verbindet im Hintergrund,
 * benennt Werkzeuge in `mcp__<server>__<werkzeug>` um und meldet je Server einen Zustand.
 *
 * Ein Aufruf wird nur wiederholt, wenn der Server ihn nachweislich nicht ausgeführt hat (abgelaufene Sitzung) –
 * ein schreibendes Werkzeug läuft so nie doppelt.
 */
class McpManager(
    private val scope: CoroutineScope,
    /** Gespeicherte OAuth-Anmeldungen; `null` = keine Anmeldung über den Browser. */
    private val tokens: McpTokenStore? = null,
    private val factory: McpTransportFactory,
) {
    private class Live(val entry: McpServerEntry, val job: Job) {
        @Volatile var client: McpClient? = null
    }

    private val lock = Mutex()
    private val live = LinkedHashMap<String, Live>()

    private val _servers = MutableStateFlow<List<McpServerState>>(emptyList())

    /** Zustand aller Server in Konfigurationsreihenfolge. */
    val servers: StateFlow<List<McpServerState>> = _servers.asStateFlow()

    private val _tools = MutableStateFlow<List<AgentTool>>(emptyList())

    /** Werkzeuge aller bereiten Server. */
    val tools: StateFlow<List<AgentTool>> = _tools.asStateFlow()

    /** Hinweise der Server (`instructions`) aller bereiten Server, begrenzt, für den System-Prompt. */
    fun serverNotes(): String = _servers.value
        .filter { it.status == McpStatus.READY && !it.instructions.isNullOrBlank() }
        .joinToString("\n") { "[${it.entry.config.name}] ${it.instructions!!.take(MAX_NOTE)}" }

    /**
     * Gleicht die laufenden Verbindungen mit [entries] ab: neue/geänderte Server verbinden, entfernte/geänderte beenden.
     * Bei doppelten Namen gewinnt der Projekt-Eintrag.
     */
    suspend fun setEntries(entries: List<McpServerEntry>) = lock.withLock {
        val byName = LinkedHashMap<String, McpServerEntry>()
        entries.sortedBy { it.source == McpSource.PROJECT }.forEach { byName[it.config.name] = it }
        val desired = byName.values.toList()

        for ((name, l) in live.toMap()) {
            val want = byName[name]
            if (want == null || want != l.entry || !isConnectable(want)) {
                l.job.cancel()
                l.client?.close()
                live.remove(name)
            }
        }
        val states = LinkedHashMap<String, McpServerState>()
        val toStart = ArrayList<McpServerEntry>()
        for (e in desired) {
            when {
                e.config.disabled -> states[e.config.name] = McpServerState(e, McpStatus.DISABLED)
                e.source == McpSource.PROJECT && !e.trusted -> states[e.config.name] = McpServerState(e, McpStatus.NEEDS_TRUST)
                else -> {
                    states[e.config.name] = _servers.value.firstOrNull { it.entry == e && live.containsKey(e.config.name) }
                        ?: McpServerState(e, McpStatus.CONNECTING)
                    if (!live.containsKey(e.config.name)) toStart += e
                }
            }
        }
        // Zustände zuerst veröffentlichen: Die Verbindungsjobs aktualisieren sie sofort nach dem Start.
        _servers.value = states.values.toList()
        rebuildTools()
        toStart.forEach(::startConnection)
    }

    /** Verbindet [name] neu (z. B. nach einem Fehler). */
    suspend fun reconnect(name: String) {
        val entry = lock.withLock {
            live.remove(name)?.also { it.job.cancel(); it.client?.close() }?.entry
                ?: _servers.value.firstOrNull { it.entry.config.name == name }?.entry
        } ?: return
        setEntries(_servers.value.map { it.entry }.filter { it.config.name != name } + entry)
    }

    /** Beendet alle Verbindungen. */
    suspend fun close() = lock.withLock {
        live.values.forEach { it.job.cancel(); it.client?.close() }
        live.clear()
        _servers.value = _servers.value.map { it.copy(status = McpStatus.DISABLED, toolCount = 0) }
        _tools.value = emptyList()
    }

    private fun isConnectable(e: McpServerEntry) = !e.config.disabled && (e.source == McpSource.APP || e.trusted)

    private fun startConnection(entry: McpServerEntry) {
        val name = entry.config.name
        // Erst eintragen, dann starten: connect() setzt live[name].client und darf den Eintrag nie verpassen.
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                connect(entry)
            } catch (e: CancellationException) {
                throw e
            } catch (e: McpAuthRequiredException) {
                update(name) { authState(entry, e) }
            } catch (e: Throwable) {
                update(name) { McpServerState(entry, McpStatus.FAILED, message = describe(e)) }
            }
        }
        live[name] = Live(entry, job)
        job.start()
    }

    private fun authState(entry: McpServerEntry, e: McpAuthRequiredException): McpServerState {
        val oauth = tokens != null && entry.config.url != null
        return McpServerState(
            entry,
            if (oauth) McpStatus.NEEDS_AUTH else McpStatus.FAILED,
            message = if (oauth) "Anmeldung nötig" else describe(e),
            wwwAuthenticate = e.wwwAuthenticate,
        )
    }

    // Schlüssel der Anmeldung eines Servers: Name und URL.
    private fun tokenKey(config: McpServerConfig) = config.name + "|" + config.url.orEmpty()

    // Ergänzt den Authorization-Header aus der gespeicherten Anmeldung (erneuert sie, wenn abgelaufen).
    private suspend fun withAuth(config: McpServerConfig): McpServerConfig {
        val store = tokens ?: return config
        if (config.url == null || config.headers.keys.any { it.equals("Authorization", ignoreCase = true) }) return config
        var t = store.get(tokenKey(config)) ?: return config
        if (t.expired()) {
            t = runCatching { McpOAuth.refresh(t) }.getOrElse { store.put(tokenKey(config), null); return config }
            store.put(tokenKey(config), t)
        }
        return config.copy(headers = config.headers + ("Authorization" to "Bearer ${t.accessToken}"))
    }

    /**
     * Meldet beim Server [name] über den Browser an ([openBrowser] öffnet die URL) und verbindet danach neu.
     * Wirft bei Fehlern [McpOAuth.OAuthException] bzw. eine `IOException`.
     */
    suspend fun signIn(name: String, openBrowser: (String) -> Unit) {
        val store = tokens ?: throw IOException("Anmeldung nicht verfügbar")
        val state = _servers.value.firstOrNull { it.entry.config.name == name } ?: throw IOException("Unbekannter Server: $name")
        val url = state.entry.config.url ?: throw IOException("Nur HTTP-Server unterstützen eine Anmeldung")
        val t = McpOAuth.signIn(url, state.wwwAuthenticate, openBrowser)
        store.put(tokenKey(state.entry.config), t)
        reconnect(name)
    }

    /** Entfernt die gespeicherte Anmeldung von [name] und verbindet neu. */
    suspend fun signOut(name: String) {
        val state = _servers.value.firstOrNull { it.entry.config.name == name } ?: return
        tokens?.put(tokenKey(state.entry.config), null)
        reconnect(name)
    }

    private suspend fun connect(entry: McpServerEntry) {
        val name = entry.config.name
        val transport = factory.create(withAuth(entry.config))
        val client = McpClient(transport)
        try {
            client.connect()
            val infos = client.listTools()
            live[name]?.client = client
            client.onToolsChanged = { scope.launch { refreshTools(name) } }
            client.onDisconnected = { cause ->
                update(name) { McpServerState(entry, McpStatus.FAILED, message = "Verbindung verloren: ${cause?.message ?: "vom Server beendet"}") }
                rebuildTools()
            }
            toolCache[name] = buildTools(entry.config, infos)
            val signedIn = tokens?.get(tokenKey(entry.config)) != null
            update(name) { McpServerState(entry, McpStatus.READY, infos.size, instructions = client.instructions, signedIn = signedIn) }
            rebuildTools()
        } catch (e: Throwable) {
            client.close()
            throw e
        }
    }

    private suspend fun refreshTools(name: String) {
        val l = live[name] ?: return
        val client = l.client ?: return
        val infos = runCatching { client.listTools() }.getOrNull() ?: return
        toolCache[name] = buildTools(l.entry.config, infos)
        update(name) { it.copy(toolCount = infos.size) }
        rebuildTools()
    }

    private val toolCache = java.util.concurrent.ConcurrentHashMap<String, List<AgentTool>>()

    private fun buildTools(config: McpServerConfig, infos: List<McpToolInfo>): List<AgentTool> {
        val used = HashSet<String>()
        return infos.map { info ->
            var n = McpRemoteTool.toolName(config.name, info.name)
            var i = 2
            while (!used.add(n)) n = McpRemoteTool.toolName(config.name, info.name + "_" + i++)
            McpRemoteTool(n, config.name, info) { tool, args -> callWithRetry(config.name, tool, args) }
        }
    }

    private suspend fun callWithRetry(server: String, tool: String, args: JsonObject): McpCallResult {
        val client = live[server]?.client ?: throw IOException("Server '$server' ist nicht verbunden.")
        return try {
            client.callTool(tool, args)
        } catch (e: McpSessionExpiredException) {
            // Der Server hat die Anfrage vor der Ausführung abgewiesen: neu verbinden und genau einmal wiederholen.
            val entry = live[server]?.entry ?: throw e
            val fresh = McpClient(factory.create(withAuth(entry.config)))
            fresh.connect()
            live[server]?.client?.close()
            live[server]?.client = fresh
            fresh.callTool(tool, args)
        }
    }

    private fun update(name: String, change: (McpServerState) -> McpServerState) {
        _servers.update { list -> list.map { if (it.entry.config.name == name) change(it) else it } }
    }

    private fun rebuildTools() {
        val ready = _servers.value.filter { it.status == McpStatus.READY }.map { it.entry.config.name }.toSet()
        _tools.value = toolCache.filterKeys { it in ready }.toSortedMap().values.flatten()
    }

    private fun describe(e: Throwable): String = when (e) {
        is McpAuthRequiredException -> "Anmeldung nötig (401). Anmelden oder Token unter „Header“ eintragen."
        is McpTimeoutException -> e.message.orEmpty()
        else -> e.message ?: e.toString()
    }

    private companion object {
        const val MAX_NOTE = 600
    }
}
