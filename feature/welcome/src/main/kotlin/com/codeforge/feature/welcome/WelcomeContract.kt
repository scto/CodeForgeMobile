// Modul: :feature:welcome
package com.codeforge.feature.welcome

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.RecentProject
import java.io.File

@Immutable
data class WelcomeUiState(
    val recentProjects: List<RecentProject> = emptyList(),
    val selectedProjectId: String? = null,
    val isLoading: Boolean = true,

    // Task 55: Crash-Bericht Dialog
    val pendingCrashLog: File? = null,
    val showCrashDetails: Boolean = false,
    val crashDetailsText: String = "",

    // Task 55: Migration Dialog
    val showMigrationDialog: Boolean = false,
    val legacyProjectsCount: Int = 0
)

sealed interface WelcomeUiEvent {
    data object CreateProjectClicked : WelcomeUiEvent
    data object ImportProjectClicked : WelcomeUiEvent
    data object CloneProjectClicked : WelcomeUiEvent
    data object TerminalClicked : WelcomeUiEvent
    data object SettingsClicked : WelcomeUiEvent
    data class RecentProjectSelected(val projectId: String) : WelcomeUiEvent
    data class RecentProjectOpened(val projectId: String) : WelcomeUiEvent
    data class RecentProjectRemoved(val projectId: String) : WelcomeUiEvent
    data class RecentProjectSettingsClicked(val projectId: String) : WelcomeUiEvent

    // Task 55 Events
    data object ViewCrashLogClicked : WelcomeUiEvent
    data object ShareCrashLogClicked : WelcomeUiEvent
    data object DeleteCrashLogClicked : WelcomeUiEvent
    data object DismissCrashLogClicked : WelcomeUiEvent
    data object DismissCrashDetails : WelcomeUiEvent

    data object ConfirmMigrationClicked : WelcomeUiEvent
    data object DeclineMigrationClicked : WelcomeUiEvent
}

sealed interface WelcomeUiEffect {
    data object NavigateToProjectWizard : WelcomeUiEffect
    data object NavigateToImportPicker : WelcomeUiEffect
    data object NavigateToCloneDialog : WelcomeUiEffect
    data object NavigateToTerminal : WelcomeUiEffect
    data object NavigateToSettings : WelcomeUiEffect
    data class NavigateToEditor(val projectPath: String) : WelcomeUiEffect
    data class NavigateToProjectSettings(val projectPath: String) : WelcomeUiEffect
    data class ShowSnackbar(val message: String) : WelcomeUiEffect
    data class ShareCrashFile(val file: File) : WelcomeUiEffect
}
