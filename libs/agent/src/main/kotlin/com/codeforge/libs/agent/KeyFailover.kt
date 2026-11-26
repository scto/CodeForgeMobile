/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Mehrere API-Schlüssel je Anbieter und das Takten der Anfragen. Nach dem Vorbild von CodeAssist (GPL-3.0):
 * `ApiKeyPool`/`KeyFailoverClient` und `RequestPacer`.
 */
package com.codeforge.libs.agent

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * Die Schlüssel eines Anbieters, Hauptschlüssel zuerst. Jede Anfrage nimmt den ersten Schlüssel, der nicht pausiert;
 * ein Schlüssel, den der Anbieter wegen eines Limits oder als ungültig abgelehnt hat, pausiert eine Weile.
 */
class ApiKeyPool(keys: List<String>, private val clock: () -> Long = System::currentTimeMillis) {
    /** Bereinigte Schlüssel in Reihenfolge. */
    val keys: List<String> = keys.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private val until = LongArray(this.keys.size)
    private val reasons = arrayOfNulls<LlmFailureKind>(this.keys.size)

    /** Anzahl der Schlüssel. */
    val size: Int get() = keys.size

    /** Index des ersten jetzt nutzbaren Schlüssels oder `null`, wenn alle pausieren. */
    @Synchronized
    fun next(): Int? {
        val now = clock()
        return keys.indices.firstOrNull { until[it] <= now }
    }

    /** Nimmt Schlüssel [index] nach der Ablehnung [kind] aus dem Wechsel. */
    @Synchronized
    fun bench(index: Int, kind: LlmFailureKind, retryAfterMs: Long?) {
        val wait = when (kind) {
            LlmFailureKind.RATE_LIMIT -> (retryAfterMs ?: RATE_LIMIT_MS).coerceAtLeast(1_000)
            // Ein abgelehnter Schlüssel heilt nicht von selbst; er bleibt draußen, bis die Schlüssel geändert werden.
            LlmFailureKind.AUTH -> Long.MAX_VALUE / 2
            else -> LONG_LIMIT_MS
        }
        until[index] = clock() + wait
        reasons[index] = kind
    }

    /** Millisekunden, bis der erste Schlüssel wieder nutzbar ist (0 = schon jetzt). */
    @Synchronized
    fun soonestMs(): Long {
        val now = clock()
        return ((until.minOrNull() ?: now) - now).coerceAtLeast(0)
    }

    /** Grund des Schlüssels, der als Nächstes zurückkommt. */
    @Synchronized
    fun soonestReason(): LlmFailureKind? = until.indices.minByOrNull { until[it] }?.let { reasons[it] }

    /** Konstanten. */
    companion object {
        /** Pause nach einem Minutenlimit ohne genannte Wartezeit. */
        const val RATE_LIMIT_MS = 60_000L

        /** Pause nach Tages-/Guthaben-Limit, bevor der Schlüssel erneut versucht wird. */
        const val LONG_LIMIT_MS = 60 * 60_000L

        /** Fehler, bei denen der nächste Schlüssel übernimmt; alles andere schlüge mit jedem Schlüssel gleich fehl. */
        val ROTATE_ON = setOf(LlmFailureKind.RATE_LIMIT, LlmFailureKind.QUOTA, LlmFailureKind.AUTH)
    }
}

/**
 * [LlmClient] über alle Schlüssel aus [pool]. Lehnt der Anbieter eine Anfrage ab, bevor etwas gestreamt wurde, geht
 * sie mit dem nächsten Schlüssel erneut raus; nach den ersten Ausgaben nie (der Nutzer sähe den Anfang doppelt).
 * Mit einem oder keinem Schlüssel reicht der Client nur durch.
 */
class KeyFailoverClient(private val pool: ApiKeyPool, private val clientFor: (String) -> LlmClient) : LlmClient {
    private val clients = ConcurrentHashMap<String, LlmClient>()

    private fun client(key: String): LlmClient = clients.getOrPut(key) { clientFor(key) }

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        if (pool.size <= 1) {
            emitAll(client(pool.keys.firstOrNull().orEmpty()).stream(request))
            return@flow
        }
        var last: LlmStreamEvent.Failed? = null
        val tried = HashSet<Int>()
        while (true) {
            val index = pool.next() ?: break
            if (!tried.add(index)) break
            var streamed = false
            var refused: LlmStreamEvent.Failed? = null
            client(pool.keys[index]).stream(request).collect { event ->
                if (event is LlmStreamEvent.Failed && !streamed) refused = event else { streamed = true; emit(event) }
            }
            val failed = refused ?: return@flow
            val kind = failed.kind
            if (kind == null || kind !in ApiKeyPool.ROTATE_ON) {
                emit(failed)
                return@flow
            }
            pool.bench(index, kind, failed.retryAfterMs)
            last = failed
        }
        val waitS = (pool.soonestMs() + 999) / 1000
        val retry = if (pool.soonestReason() == LlmFailureKind.AUTH) "" else " Erster Schlüssel wieder in ${waitS} s."
        emit(
            LlmStreamEvent.Failed(
                "Alle ${pool.size} API-Schlüssel dieses Anbieters sind gerade gesperrt oder erschöpft.$retry" +
                    (last?.message?.let { "\n$it" } ?: ""),
                kind = last?.kind,
                retryAfterMs = pool.soonestMs(),
            ),
        )
    }
}

/**
 * Hält einen Agentenlauf unter den Minutenlimits des Anbieters, indem *vor* einer Anfrage gewartet wird, statt sie
 * zu senden und ein 429 zu kassieren. Ein Agentenschritt löst viele Anfragen kurz hintereinander aus – genau das
 * sprengt Gratis-Limits. Das Limit kommt vom Nutzer ([configuredRpm]) oder wird aus einem 429 gelernt (eine
 * Anfrage weniger als in der letzten Minute gesendet). Ohne bekanntes Limit wird nie gewartet. Thread-sicher.
 */
class RequestPacer(private val configuredRpm: Int? = null, private val clock: () -> Long = System::currentTimeMillis) {
    private val window = ArrayDeque<Long>()
    private var learnedRpm: Int? = null

    /** Wirksames Limit (Anfragen pro Minute) oder `null`. */
    val rpm: Int? @Synchronized get() = configuredRpm?.takeIf { it > 0 } ?: learnedRpm

    /** Millisekunden bis zur nächsten erlaubten Anfrage (0 = sofort). Zeichnet nichts auf. */
    @Synchronized
    fun delayMs(): Long {
        val now = clock()
        prune(now)
        val limit = rpm ?: return 0
        if (window.size < limit) return 0
        return (window.elementAt(window.size - limit) + WINDOW_MS - now).coerceAtLeast(0)
    }

    /** Merkt eine eben gesendete Anfrage. */
    @Synchronized
    fun record() {
        val now = clock()
        prune(now)
        window.addLast(now)
    }

    /** Lernt aus einem Minutenlimit: künftig eine Anfrage weniger als in der letzten Minute. */
    @Synchronized
    fun learn() {
        prune(clock())
        learnedRpm = (window.size - 1).takeIf { it > 0 } ?: learnedRpm
    }

    private fun prune(now: Long) {
        while (window.isNotEmpty() && window.first() + WINDOW_MS <= now) window.removeFirst()
    }

    private companion object {
        const val WINDOW_MS = 60_000L
    }
}

/** [LlmClient], der jede Anfrage über [pacer] taktet; [onWait] meldet längere Wartezeiten (für die Oberfläche). */
class PacedLlmClient(
    private val inner: LlmClient,
    private val pacer: RequestPacer,
    private val onWait: (Long) -> Unit = {},
) : LlmClient {
    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        val wait = pacer.delayMs()
        if (wait > 0) {
            if (wait >= NOTIFY_MS) onWait(wait)
            delay(wait)
        }
        pacer.record()
        inner.stream(request).collect { event ->
            if (event is LlmStreamEvent.Failed && event.kind == LlmFailureKind.RATE_LIMIT) pacer.learn()
            emit(event)
        }
    }

    private companion object {
        const val NOTIFY_MS = 2_000L
    }
}
