/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.runtime

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update

/** Zustand der laufenden IDE-Arbeit; Grundlage der Benachrichtigung mit Wakelock- und Beenden-Schalter. */
data class IdeActivityState(
    /** Bezeichnungen heikler laufender Vorgänge (Build, Klonen, SDK-Installation …), in Startreihenfolge. */
    val tasks: List<String> = emptyList(),
    val terminals: Int = 0,
    val wakeLock: Boolean = false
) {
    /** Die Benachrichtigung (und damit der Vordergrunddienst) wird gebraucht, solange etwas davon gilt. */
    val needsForeground: Boolean get() = tasks.isNotEmpty() || terminals > 0 || wakeLock
}

/**
 * Prozessweite Anmeldung heikler Vorgänge. Module melden sich mit [begin]/[end] oder [trackedAs] an; die App zeigt
 * daraus eine Benachrichtigung (Wakelock, Beenden, Anzahl Terminals). Reines Kotlin, damit jedes Modul es nutzen kann.
 */
object IdeActivityRegistry {
    private val ids = AtomicLong()
    private val running = MutableStateFlow<List<Pair<Long, String>>>(emptyList())
    private val terminalCount = MutableStateFlow(0)
    private val wakeLockWanted = MutableStateFlow(false)

    private val _state = MutableStateFlow(IdeActivityState())
    /** Aktueller Zustand aller angemeldeten Vorgänge. */
    val state: StateFlow<IdeActivityState> = _state.asStateFlow()

    private val _exitRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** „Beenden“ aus der Benachrichtigung; die Activity schließt sich. */
    val exitRequests: SharedFlow<Unit> = _exitRequests.asSharedFlow()

    /**
     * Meldet einen Vorgang an.
     *
     * @param label Anzeigename (Build, Klonen …).
     * @return ID für [end].
     */
    fun begin(label: String): Long {
        val id = ids.incrementAndGet()
        running.update { it + (id to label) }
        publish()
        return id
    }

    /** Meldet den Vorgang [id] ab. */
    fun end(id: Long) {
        running.update { list -> list.filterNot { it.first == id } }
        publish()
    }

    /** Setzt die Anzahl offener Terminals. */
    fun setTerminalCount(count: Int) {
        terminalCount.value = count.coerceAtLeast(0)
        publish()
    }

    /** Schaltet den Wakelock-Wunsch um. */
    fun setWakeLock(enabled: Boolean) {
        wakeLockWanted.value = enabled
        publish()
    }

    /** Fordert das Beenden der App an (siehe [exitRequests]). */
    fun requestExit() {
        _exitRequests.tryEmit(Unit)
    }

    /** Nur für Tests: alles zurücksetzen. */
    internal fun reset() {
        running.value = emptyList()
        terminalCount.value = 0
        wakeLockWanted.value = false
        publish()
    }

    private fun publish() {
        _state.value = IdeActivityState(
            tasks = running.value.map { it.second }.distinct(),
            terminals = terminalCount.value,
            wakeLock = wakeLockWanted.value
        )
    }

    /** Meldet den Vorgang an, solange der Flow läuft (auch bei Abbruch oder Fehler wieder ab). */
    fun <T> Flow<T>.trackedAs(label: String): Flow<T> {
        var id = 0L
        return onStart { id = begin(label) }.onCompletion { if (id != 0L) end(id) }
    }
}
