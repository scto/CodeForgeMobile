/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

/** Art eines installierbaren SDK-Werkzeugs. */
enum class ToolType { JDK, CMDLINE_TOOLS, PLATFORM_TOOLS, BUILD_TOOLS, PLATFORM, NDK, CMAKE }

/** Ein Werkzeug mit Version, Installationsstatus und Pfad. */
data class ToolItem(
    val id: String,
    val version: String,
    val isInstalled: Boolean,
    val path: String?
)

/** Ereignis einer SDK-Installation. */
sealed interface SdkInstallEvent {
    /** Fortschritt mit Meldung. */
    data class Progress(val percent: Int, val message: String) : SdkInstallEvent
    /** Installation erfolgreich; [packagePath] ist der Installationspfad. */
    data class Success(val packagePath: String) : SdkInstallEvent
    /** Installation fehlgeschlagen. */
    data class Error(val exception: Throwable) : SdkInstallEvent
}
