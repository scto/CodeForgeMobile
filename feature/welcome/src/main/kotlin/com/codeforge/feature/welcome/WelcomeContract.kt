// Modul: :feature:welcome
package com.codeforge.feature.welcome

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.RecentProject
import java.io.File

/** Zustand des Startbildschirms (zuletzt geöffnete Projekte, Absturzbericht, Migration). */
@Immutable
data class WelcomeUiState(
    val recentProjects: List<RecentProject> = emptyList(),
    val selectedProjectId: String? = null,
    /** Normalisierte Pfade der automatisch aus dem Projektstamm gelisteten Projekte (nicht entfernbar). */
    val autoListedPaths: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    /** Projekt, für das gerade die Löschbestätigung angezeigt wird. */
    val pendingDelete: RecentProject? = null,

    // Task 55: Crash-Bericht Dialog
    val pendingCrashLog: File? = null,
    val showCrashDetails: Boolean = false,
    val crashDetailsText: String = "",

    // Task 55: Migration Dialog
    val showMigrationDialog: Boolean = false,
    val legacyProjectsCount: Int = 0
)

/** Aktionen im Startbildschirm. */
sealed interface WelcomeUiEvent {
    /** Neues Projekt. */
    data object CreateProjectClicked : WelcomeUiEvent
    /** Projekt importieren. */
    data object ImportProjectClicked : WelcomeUiEvent
    /** Projekt klonen. */
    data object CloneProjectClicked : WelcomeUiEvent
    /** Terminal öffnen. */
    data object TerminalClicked : WelcomeUiEvent
    /** Einstellungen öffnen. */
    data object SettingsClicked : WelcomeUiEvent
    /** Projektliste neu laden. */
    data object RefreshProjects : WelcomeUiEvent
    /** Projekt markiert. */
    data class RecentProjectSelected(val projectId: String) : WelcomeUiEvent
    /** Projekt geöffnet. */
    data class RecentProjectOpened(val projectId: String) : WelcomeUiEvent
    /** Projekt aus der Liste entfernt. */
    data class RecentProjectRemoved(val projectId: String) : WelcomeUiEvent
    /** „Projekt löschen“ gewählt: Bestätigung anzeigen. */
    data class RecentProjectDeleteRequested(val projectId: String) : WelcomeUiEvent
    /** Löschen bestätigt: Projektordner wird gelöscht. */
    data object DeleteProjectConfirmed : WelcomeUiEvent
    /** Löschen abgebrochen. */
    data object DeleteProjectDismissed : WelcomeUiEvent
    /** Projekteinstellungen öffnen. */
    data class RecentProjectSettingsClicked(val projectId: String) : WelcomeUiEvent

    // Task 55 Events
    /** Absturzbericht ansehen. */
    data object ViewCrashLogClicked : WelcomeUiEvent
    /** Absturzbericht teilen. */
    data object ShareCrashLogClicked : WelcomeUiEvent
    /** Absturzbericht löschen. */
    data object DeleteCrashLogClicked : WelcomeUiEvent
    /** Hinweis auf Absturz schließen. */
    data object DismissCrashLogClicked : WelcomeUiEvent
    /** Absturzdetails schließen. */
    data object DismissCrashDetails : WelcomeUiEvent

    /** Migration bestätigt. */
    data object ConfirmMigrationClicked : WelcomeUiEvent
    /** Migration abgelehnt. */
    data object DeclineMigrationClicked : WelcomeUiEvent
}

/** Einmalige Effekte. */
sealed interface WelcomeUiEffect {
    /** Zum Projektassistenten. */
    data object NavigateToProjectWizard : WelcomeUiEffect
    /** Zur Importauswahl. */
    data object NavigateToImportPicker : WelcomeUiEffect
    /** Zum Klon-Dialog. */
    data object NavigateToCloneDialog : WelcomeUiEffect
    /** Zum Terminal. */
    data object NavigateToTerminal : WelcomeUiEffect
    /** Zu den Einstellungen. */
    data object NavigateToSettings : WelcomeUiEffect
    /** Zum Editor. */
    data class NavigateToEditor(val projectPath: String) : WelcomeUiEffect
    /** Zu den Projekteinstellungen. */
    data class NavigateToProjectSettings(val projectPath: String) : WelcomeUiEffect
    /** Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : WelcomeUiEffect
    /** Absturzdatei teilen. */
    data class ShareCrashFile(val file: File) : WelcomeUiEffect
}
