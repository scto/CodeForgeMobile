package com.codeforge.feature.projectsettings

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.BuildTypeSettings
import com.codeforge.core.domain.model.ProjectBuildSettings
import com.codeforge.core.domain.model.ProjectSigningAssignment

/** Build-Typ-Reiter (Debug/Release). */
enum class ProjectBuildTypeTab {
    RELEASE,
    DEBUG
}

/** Zustand der Projekteinstellungen. */
@Immutable
data class ProjectSettingsUiState(
    val projectPath: String = "",
    val selectedTab: ProjectBuildTypeTab = ProjectBuildTypeTab.RELEASE,
    val settings: ProjectBuildSettings = ProjectBuildSettings(""),
    val signingAssignment: ProjectSigningAssignment? = null,
    val isLoading: Boolean = false,
    val isSavedMessageVisible: Boolean = false
) {
    /** Einstellungen des gewählten Reiters. */
    val currentTabSettings: BuildTypeSettings
        get() = when (selectedTab) {
            ProjectBuildTypeTab.RELEASE -> settings.release
            ProjectBuildTypeTab.DEBUG -> settings.debug
        }
}

/** Aktionen der Projekteinstellungen. */
sealed interface ProjectSettingsIntent {
    /** Einstellungen laden. */
    data class Load(val projectPath: String) : ProjectSettingsIntent
    /** Reiter wählen. */
    data class SelectTab(val tab: ProjectBuildTypeTab) : ProjectSettingsIntent
    /** Verkleinern (R8) ein-/ausschalten. */
    data class ToggleMinify(val enabled: Boolean) : ProjectSettingsIntent
    /** Ressourcen verkleinern ein-/ausschalten. */
    data class ToggleShrink(val enabled: Boolean) : ProjectSettingsIntent
    /** PNG-Optimierung ein-/ausschalten. */
    data class ToggleCrunch(val enabled: Boolean) : ProjectSettingsIntent
    /** Debugbar ein-/ausschalten. */
    data class ToggleDebuggable(val enabled: Boolean) : ProjectSettingsIntent
    /** Signierung ein-/ausschalten. */
    data class ToggleSigning(val enabled: Boolean) : ProjectSettingsIntent
    /** Speichern. */
    object Save : ProjectSettingsIntent
    /** Signaturverwaltung öffnen. */
    object OpenSigningManager : ProjectSettingsIntent
}

/** Einmalige Effekte. */
sealed interface ProjectSettingsEffect {
    /** Meldung anzeigen. */
    data class ShowMessage(val message: String) : ProjectSettingsEffect
    /** Zur Signaturverwaltung. */
    data class NavigateToSigning(val projectPath: String) : ProjectSettingsEffect
}
