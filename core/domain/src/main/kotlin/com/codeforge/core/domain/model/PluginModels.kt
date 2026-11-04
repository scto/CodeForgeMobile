/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

/** Ein installiertes Plugin mit Metadaten, Berechtigungen und Aktivierungsstatus. */
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
    val contributionKinds: List<String> = emptyList(),
    /** IDs der Plugins, die dieses Bundle (`extensionPack`) zusammenfasst. */
    val packMembers: List<String> = emptyList()
) {
    /** Ob das Plugin keinen ausführbaren Code enthält. */
    val isDeclarativeOnly: Boolean get() = entryPointClass == null
}

/** Ereignis einer Plugin-Installation. */
sealed interface PluginInstallEvent {
    /** Fortschritt in Prozent. */
    data class Progress(val percent: Int) : PluginInstallEvent
    /** Installation erfolgreich. */
    data class Success(val plugin: InstalledPlugin) : PluginInstallEvent
    /** Installation fehlgeschlagen. */
    data class Failed(val message: String) : PluginInstallEvent
}
