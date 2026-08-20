// Modul: :feature:welcome
package com.codeforge.feature.welcome

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.RecentProject

@Immutable
data class WelcomeUiState(
    val recentProjects: List<RecentProject> = emptyList(),
    val selectedProjectId: String? = null,
    val isLoading: Boolean = true
)

sealed interface WelcomeUiEvent {
    data object CreateProjectClicked : WelcomeUiEvent
    data object ImportProjectClicked : WelcomeUiEvent
    data object CloneProjectClicked : WelcomeUiEvent
    data object SettingsClicked : WelcomeUiEvent
    data class RecentProjectSelected(val projectId: String) : WelcomeUiEvent
    data class RecentProjectOpened(val projectId: String) : WelcomeUiEvent
    data class RecentProjectRemoved(val projectId: String) : WelcomeUiEvent
}

sealed interface WelcomeUiEffect {
    data object NavigateToProjectWizard : WelcomeUiEffect
    data object NavigateToImportPicker : WelcomeUiEffect
    data object NavigateToCloneDialog : WelcomeUiEffect
    data object NavigateToSettings : WelcomeUiEffect
    data class NavigateToEditor(val projectPath: String) : WelcomeUiEffect
    data class ShowSnackbar(val message: String) : WelcomeUiEffect
}
