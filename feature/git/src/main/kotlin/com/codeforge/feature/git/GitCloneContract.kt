// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.runtime.Immutable

/** Phase des Klonens. */
enum class GitClonePhase { IDLE, CLONING, DONE, FAILED }

/** Zustand des Klon-Bildschirms (URL, Zielordner, Fortschritt, Phase, Fehler). */
@Immutable
data class GitCloneUiState(
    val repositoryUrl: String = "",
    val targetDirectory: String = "",
    /** true, sobald der Nutzer den Zielordner selbst geändert hat; dann wird er nie automatisch ersetzt. */
    val targetEdited: Boolean = false,
    val currentTask: String = "",
    val progressPercent: Int = 0,
    val phase: GitClonePhase = GitClonePhase.IDLE,
    val errorMessage: String? = null
)

/** Aktionen im Klon-Bildschirm. */
sealed interface GitCloneUiEvent {
    /** Die URL wurde geändert. */
    data class UrlChanged(val url: String) : GitCloneUiEvent
    /** Der Zielordner wurde geändert. */
    data class TargetDirChanged(val path: String) : GitCloneUiEvent
    /** Bildschirm wurde (wieder) sichtbar, z. B. nach Erteilen der Speicherberechtigung. */
    data object ScreenResumed : GitCloneUiEvent
    /** Klonen starten. */
    data object CloneClicked : GitCloneUiEvent
    /** Nach einem Fehler erneut versuchen. */
    data object RetryClicked : GitCloneUiEvent
}

/** Einmalige Effekte des Klon-Bildschirms. */
sealed interface GitCloneUiEffect {
    /** Zum Dateibaum des geklonten Projekts wechseln. */
    data class NavigateToFileTree(val rootPath: String) : GitCloneUiEffect
}
