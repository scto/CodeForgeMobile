/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.runtime

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Art eines sichtbaren Vorgangs. */
enum class IdeProgressKind { INDEXING, GRADLE, INSTALL }

/** Ein laufender Vorgang für die Fortschrittsleiste. */
data class IdeProgress(
    val id: Long,
    val kind: IdeProgressKind,
    /** Was läuft (Projektname, Gradle-Aufruf). */
    val title: String,
    /** Woran gerade gearbeitet wird (Datei, Gradle-Task). */
    val detail: String = "",
    /** Fortschritt in Prozent oder `null`, wenn unbekannt. */
    val percent: Int? = null,
)

/**
 * Prozessweite Liste sichtbarer Vorgänge (Indexierung, Gradle-Läufe). Die App zeigt sie als Leiste über allen
 * Bildschirmen. Reines Kotlin, damit jedes Modul melden kann.
 */
object IdeProgressRegistry {
    private val ids = AtomicLong()
    private val _items = MutableStateFlow<List<IdeProgress>>(emptyList())
    /** Laufende Vorgänge in Startreihenfolge. */
    val items: StateFlow<List<IdeProgress>> = _items.asStateFlow()

    /** Meldet einen Vorgang an und liefert seine ID für [update] und [end]. */
    fun begin(kind: IdeProgressKind, title: String): Long {
        val id = ids.incrementAndGet()
        _items.update { it + IdeProgress(id, kind, title) }
        return id
    }

    /** Aktualisiert Detailtext und Fortschritt; `percent = null` lässt den bisherigen Wert stehen. */
    fun update(id: Long, detail: String, percent: Int? = null) {
        _items.update { list ->
            list.map { if (it.id == id) it.copy(detail = detail, percent = percent?.coerceIn(0, 100) ?: it.percent) else it }
        }
    }

    /** Meldet den Vorgang ab. */
    fun end(id: Long) {
        _items.update { list -> list.filterNot { it.id == id } }
    }
}
