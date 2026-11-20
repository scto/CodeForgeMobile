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
    val packMembers: List<String> = emptyList(),
    /** Mindestversion der App; leer = keine Einschränkung. */
    val minHostVersion: String = "",
    /** IDs der Plugins, von denen dieses abhängt. */
    val dependsOn: List<String> = emptyList(),
    /** Wann der Code geladen wird (leer = beim Start); siehe `ActivationEvents`. */
    val activationEvents: List<String> = emptyList(),
    /** SHA-256 des Signaturschlüssels (leer = nicht signiert); Updates müssen denselben Schlüssel nutzen. */
    val signerFingerprint: String = "",
    /** Herkunft aus `build-info.json` (z. B. Commit und Build-Zeit); leer = unbekannt. */
    val buildInfo: String = "",
    /** Code läuft im isolierten Sandbox-Prozess (siehe `SandboxPolicy`). */
    val sandboxed: Boolean = false
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
