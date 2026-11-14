/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.runtime

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Wie dringend Speicher gebraucht wird. */
enum class MemoryPressure {
    /** Das System wird knapp: nur Ungenutztes freigeben (Caches, Sprachserver ohne offene Datei). */
    LOW,
    /** Das System ist sehr knapp: zusätzlich alles, was sich von selbst wieder startet. */
    CRITICAL,
    /** Der Nutzer hat „Speicher freigeben“ gedrückt: wie [CRITICAL], dazu der Gradle-Daemon. */
    MANUAL
}

/**
 * Ein Teil der IDE, der Speicher abgeben kann (Hilfsprozesse, Caches). Module steuern ihn per Hilt-Multibinding bei.
 * Freigeben darf keine laufende Arbeit abbrechen; was freigegeben wurde, muss sich bei Bedarf von selbst neu aufbauen.
 */
interface MemoryReleaser {
    /** Anzeigename dessen, was freigegeben wird. */
    val label: String

    /** Gibt frei, was zur Dringlichkeit passt, und liefert die Anzahl freigegebener Dinge (0 = nichts zu tun). */
    suspend fun release(pressure: MemoryPressure): Int
}

/** Ruft alle [MemoryReleaser] auf. Läuft nie doppelt; ein Fehler in einem Teil hält die anderen nicht auf. */
@Singleton
class ReleaseMemoryUseCase @Inject constructor(
    private val releasers: Set<@JvmSuppressWildcards MemoryReleaser>
) {
    private val mutex = Mutex()

    /** Liefert je Teil die Anzahl freigegebener Dinge (nur Einträge > 0). */
    suspend operator fun invoke(pressure: MemoryPressure): Map<String, Int> = mutex.withLock {
        releasers
            .associate { r -> r.label to runCatching { r.release(pressure) }.getOrDefault(0) }
            .filterValues { it > 0 }
    }
}
