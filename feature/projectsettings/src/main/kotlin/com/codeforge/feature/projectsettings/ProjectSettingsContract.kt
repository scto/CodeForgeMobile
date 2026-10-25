package com.codeforge.feature.projectsettings

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.BuildTypeSettings
import com.codeforge.core.domain.model.ProjectBuildSettings
import com.codeforge.core.domain.model.ProjectSigningAssignment

enum class ProjectBuildTypeTab {
    RELEASE,
    DEBUG
}

@Immutable
data class ProjectSettingsUiState(
    val projectPath: String = "",
    val selectedTab: ProjectBuildTypeTab = ProjectBuildTypeTab.RELEASE,
    val settings: ProjectBuildSettings = ProjectBuildSettings(""),
    val signingAssignment: ProjectSigningAssignment? = null,
    val isLoading: Boolean = false,
    val isSavedMessageVisible: Boolean = false
) {
    val currentTabSettings: BuildTypeSettings
        get() = when (selectedTab) {
            ProjectBuildTypeTab.RELEASE -> settings.release
            ProjectBuildTypeTab.DEBUG -> settings.debug
        }
}

sealed interface ProjectSettingsIntent {
    data class Load(val projectPath: String) : ProjectSettingsIntent
    data class SelectTab(val tab: ProjectBuildTypeTab) : ProjectSettingsIntent
    data class ToggleMinify(val enabled: Boolean) : ProjectSettingsIntent
    data class ToggleShrink(val enabled: Boolean) : ProjectSettingsIntent
    data class ToggleCrunch(val enabled: Boolean) : ProjectSettingsIntent
    data class ToggleDebuggable(val enabled: Boolean) : ProjectSettingsIntent
    data class ToggleSigning(val enabled: Boolean) : ProjectSettingsIntent
    object Save : ProjectSettingsIntent
    object OpenSigningManager : ProjectSettingsIntent
}

sealed interface ProjectSettingsEffect {
    data class ShowMessage(val message: String) : ProjectSettingsEffect
    data class NavigateToSigning(val projectPath: String) : ProjectSettingsEffect
}
