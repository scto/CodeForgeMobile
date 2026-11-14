/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.projectsroot

import androidx.compose.runtime.Immutable

/** Zustand des Bildschirms „Projektverzeichnis“. */
@Immutable
data class ProjectsRootState(
    /** Tatsächlich verwendeter Projektstamm. */
    val currentPath: String = "",
    /** Gesetzter eigener Pfad oder `null`, wenn der Standard gilt. */
    val customPath: String? = null,
    /** Standardpfad im externen Speicher (`CodeForgeMobileProjects`). */
    val externalDefaultPath: String = "",
    /** Ob der externe Speicher aktuell nutzbar ist (Berechtigung erteilt). */
    val externalUsable: Boolean = false,
    /** Eingabefeld. */
    val input: String = "",
)

/** Aktionen des Bildschirms. */
sealed interface ProjectsRootEvent {
    /** Eingabe geändert. */
    data class InputChanged(val value: String) : ProjectsRootEvent
    /** Eingegebenen Pfad übernehmen. */
    data object Apply : ProjectsRootEvent
    /** Auf den Standard zurücksetzen. */
    data object ResetToDefault : ProjectsRootEvent
    /** Standardpfad im externen Speicher ins Eingabefeld übernehmen. */
    data object UseExternalDefault : ProjectsRootEvent
    /** Berechtigung/Pfad neu einlesen (z. B. nach Rückkehr aus den Systemeinstellungen). */
    data object Refresh : ProjectsRootEvent
}

/** Einmalige Effekte. */
sealed interface ProjectsRootEffect {
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : ProjectsRootEffect
}
