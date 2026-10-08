/**
 * Modul: :feature:plugins
 * @author Thomas Schmid
 */
package com.codeforge.feature.plugins

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.InstalledPlugin

@Immutable
data class PluginsUiState(
    val plugins: List<InstalledPlugin> = emptyList(),
    val loadedPluginIds: Set<String> = emptySet(),
    val installProgressPercent: Int? = null,
    val isLoading: Boolean = true
)

sealed interface PluginsUiEvent {
    data class ToggleEnabled(val pluginId: String, val enabled: Boolean) : PluginsUiEvent
    data class Uninstall(val pluginId: String) : PluginsUiEvent
    data class InstallFromFile(val archivePath: String) : PluginsUiEvent
}

sealed interface PluginsUiEffect {
    data class ShowSnackbar(val message: String) : PluginsUiEffect
}
