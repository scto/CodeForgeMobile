/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

enum class ToolType { JDK, BUILD_TOOLS, PLATFORM, NDK, CMAKE }

data class ToolItem(
    val id: String,
    val version: String,
    val isInstalled: Boolean,
    val path: String?
)

sealed interface SdkInstallEvent {
    data class Progress(val percent: Int, val message: String) : SdkInstallEvent
    data class Success(val packagePath: String) : SdkInstallEvent
    data class Error(val exception: Throwable) : SdkInstallEvent
}
