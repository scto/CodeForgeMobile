/**
 * Modul: :feature:projectsettings
 * @author Thomas Schmid
 */
package com.codeforge.feature.projectsettings.projectinfo

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ProjectStats

/** Zustand des Bildschirms „Projektinfo“. */
@Immutable
sealed interface ProjectInfoState {
    /** Auswertung läuft; [filesRead] Dateien sind gelesen. */
    data class Loading(val filesRead: Int = 0) : ProjectInfoState

    /** Auswertung fertig. */
    data class Loaded(val stats: ProjectStats) : ProjectInfoState

    /** Auswertung fehlgeschlagen. */
    data class Failed(val message: String) : ProjectInfoState
}

/** Ein Abschnitt eines Diagramms (Wert und Anteil 0..1). */
@Immutable
data class ChartSlice(val label: String, val value: Long, val fraction: Float)

/** Höchstens [max] Abschnitte; der Rest wird unter [otherLabel] zusammengefasst. Werte ≤ 0 entfallen. */
fun toSlices(entries: List<Pair<String, Long>>, max: Int, otherLabel: String): List<ChartSlice> {
    val positive = entries.filter { it.second > 0 }.sortedByDescending { it.second }
    val total = positive.sumOf { it.second }.takeIf { it > 0 } ?: return emptyList()
    val head = if (positive.size > max) positive.take(max - 1) else positive
    val rest = positive.drop(head.size).sumOf { it.second }
    return (head + listOfNotNull((otherLabel to rest).takeIf { rest > 0 }))
        .map { (label, value) -> ChartSlice(label, value, value.toFloat() / total) }
}
