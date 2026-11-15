// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Spart LSP-Anfragen: gleiche Anfragen, die gleichzeitig laufen, teilen sich eine Antwort; erfolgreiche, nicht leere
 * Antworten bleiben kurz im Speicher, bis sich irgendein Dokument ändert ([invalidate]) oder [ttlMs] abläuft.
 *
 * Leere Antworten und Fehler werden nie gespeichert – ein Server, der noch indiziert, antwortet später anders.
 * Die Anfrage selbst läuft in [scope]; sie wird erst abgebrochen, wenn alle Wartenden abgebrochen haben.
 */
internal class LspRequestCoalescer(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val ttlMs: Long = DEFAULT_TTL_MS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val now: () -> Long = System::currentTimeMillis
) {
    private class Flight(val deferred: Deferred<Result<Any?>>, var waiters: Int)

    private class Entry(val value: Result<Any?>, val storedAt: Long)

    private val lock = Any()
    private val inFlight = HashMap<String, Flight>()

    // Zugriffsreihenfolge: der am längsten ungenutzte Eintrag fällt zuerst heraus.
    private val cache = object : LinkedHashMap<String, Entry>(INITIAL_CAPACITY, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > maxEntries
    }
    private var epoch = 0L

    /** Zähler für Tests und Diagnose: Treffer im Speicher bzw. mitgenutzte laufende Anfragen. */
    @Volatile var cacheHits = 0L
        private set
    @Volatile var sharedFlights = 0L
        private set

    /** Führt [block] für [key] aus oder liefert die gespeicherte bzw. gerade laufende Antwort; ohne [cache] wird nur zusammengelegt. */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> run(key: String, cache: Boolean = true, block: suspend () -> Result<T>): Result<T> {
        val flight = synchronized(lock) {
            if (cache) cached(key)?.let { return it as Result<T> }
            inFlight[key]?.also { it.waiters++; sharedFlights++ } ?: newFlight(key, cache, block).also { inFlight[key] = it }
        }
        flight.deferred.start()
        try {
            return flight.deferred.await() as Result<T>
        } finally {
            release(key, flight)
        }
    }

    /** Verwirft alles Gespeicherte (Dokument geändert, geöffnet, geschlossen; Server gestartet oder gestoppt). */
    fun invalidate() = synchronized(lock) {
        epoch++
        cache.clear()
        // Laufende Anfragen beantworten ihre Wartenden noch, werden aber nicht mehr geteilt oder gespeichert.
        inFlight.clear()
    }

    private fun cached(key: String): Result<Any?>? {
        val entry = cache[key] ?: return null
        if (now() - entry.storedAt > ttlMs) {
            cache.remove(key)
            return null
        }
        cacheHits++
        return entry.value
    }

    private fun <T> newFlight(key: String, store: Boolean, block: suspend () -> Result<T>): Flight {
        val startedIn = epoch
        return Flight(
            scope.async(start = CoroutineStart.LAZY) {
                val result = block()
                if (store && result.isWorthKeeping()) {
                    synchronized(lock) { if (epoch == startedIn) cache[key] = Entry(result, now()) }
                }
                result
            },
            waiters = 1
        )
    }

    private fun release(key: String, flight: Flight) = synchronized(lock) {
        flight.waiters--
        if (flight.waiters > 0) return@synchronized
        if (inFlight[key] === flight) inFlight.remove(key)
        if (!flight.deferred.isCompleted) flight.deferred.cancel()
    }

    private fun Result<Any?>.isWorthKeeping(): Boolean {
        val value = getOrNull() ?: return false
        return (value as? Collection<*>)?.isNotEmpty() ?: true
    }

    private companion object {
        const val DEFAULT_TTL_MS = 30_000L
        const val DEFAULT_MAX_ENTRIES = 256
        const val INITIAL_CAPACITY = 64
        const val LOAD_FACTOR = 0.75f
    }
}
