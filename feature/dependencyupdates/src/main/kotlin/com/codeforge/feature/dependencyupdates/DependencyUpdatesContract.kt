/**
 * Modul: :feature:dependencyupdates
 * @author Thomas Schmid
 */
package com.codeforge.feature.dependencyupdates

import androidx.compose.runtime.Immutable
import com.codeforge.libs.dependency_updater_api.DependencyUpdate

@Immutable
data class DependencyUpdatesUiState(
    /** Aktuell im Dialog angezeigtes Update (`null` = kein Dialog). */
    val current: DependencyUpdate? = null,
    /** Anzahl weiterer, noch nicht entschiedener Updates hinter [current]. */
    val remaining: Int = 0,
    val isApplying: Boolean = false,
    val errorMessage: String? = null
)

sealed interface DependencyUpdatesEvent {
    /** Update dauerhaft ignorieren (pro Bibliothek + Zielversion). */
    data object Dismiss : DependencyUpdatesEvent

    /** Beim nächsten Öffnen des Projekts erneut fragen. */
    data object AskLater : DependencyUpdatesEvent

    data object Update : DependencyUpdatesEvent
}
