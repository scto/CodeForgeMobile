/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

data class InstalledPlugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val isEnabled: Boolean,
    val entryPointClass: String? = null,
    val apiVersion: Int = 1,
    val publisher: String = "",
    val permissions: Set<String> = emptySet(),
    /** Namen der beigesteuerten Contribution-Arten (z. B. "colorThemes"), für die Anzeige. */
    val contributionKinds: List<String> = emptyList()
) {
    val isDeclarativeOnly: Boolean get() = entryPointClass == null
}

sealed interface PluginInstallEvent {
    data class Progress(val percent: Int) : PluginInstallEvent
    data class Success(val plugin: InstalledPlugin) : PluginInstallEvent
    data class Failed(val message: String) : PluginInstallEvent
}
