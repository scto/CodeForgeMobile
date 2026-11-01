/**
 * Modul: :feature:plugins
 * @author Thomas Schmid
 */
package com.codeforge.feature.plugins

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.extension.RequirementContribution
import com.codeforge.core.domain.model.InstalledPlugin
import com.codeforge.core.ui.LspInfoModel
import com.codeforge.core.ui.LspRuntimeInfo

@Immutable
data class PluginsUiState(
    val plugins: List<InstalledPlugin> = emptyList(),
    val loadedPluginIds: Set<String> = emptySet(),
    val installProgressPercent: Int? = null,
    val isLoading: Boolean = true,
    /** Noch nicht installierte Programme je aktiviertem Plugin (`contributes.requires`). */
    val missingRequirements: Map<String, List<RequirementContribution>> = emptyMap(),
    val requirementDialog: RequirementDialog? = null,
    /** Offener Info-Dialog eines Language-Server-Plugins (Zustand/Laufzeit/Allgemein). */
    val infoDialog: PluginInfoDialog? = null
)

@Immutable
data class PluginInfoDialog(val pluginId: String, val model: LspInfoModel, val runtime: LspRuntimeInfo? = null)

/** Dialog zum Installieren fehlender Programme: erst Bestätigung, dann Live-Ausgabe. */
sealed interface RequirementDialog {
    val pluginId: String

    data class Confirm(override val pluginId: String, val missing: List<RequirementContribution>) : RequirementDialog
    data class Running(
        override val pluginId: String,
        val lines: List<String> = emptyList(),
        val done: Boolean = false,
        val exitCode: Int? = null
    ) : RequirementDialog
}

sealed interface PluginsUiEvent {
    data class ToggleEnabled(val pluginId: String, val enabled: Boolean) : PluginsUiEvent
    data class Uninstall(val pluginId: String) : PluginsUiEvent
    data class InstallFromFile(val archivePath: String) : PluginsUiEvent
    data class RequirementsClicked(val pluginId: String) : PluginsUiEvent
    data object RequirementsConfirmed : PluginsUiEvent
    data object RequirementsDismissed : PluginsUiEvent
    data class InfoClicked(val pluginId: String) : PluginsUiEvent
    data object InfoDismissed : PluginsUiEvent
}

sealed interface PluginsUiEffect {
    data class ShowSnackbar(val message: String) : PluginsUiEffect
}
