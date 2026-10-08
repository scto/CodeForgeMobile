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
    val entryPointClass: String? = null
)

sealed interface PluginInstallEvent {
    data class Progress(val percent: Int) : PluginInstallEvent
    data class Success(val plugin: InstalledPlugin) : PluginInstallEvent
    data class Failed(val message: String) : PluginInstallEvent
}
