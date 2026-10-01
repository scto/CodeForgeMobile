/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

enum class ToolType { JDK, BUILD_TOOLS, PLATFORM, NDK, CMAKE }

data class JavaInfo(
    val isInstalled: Boolean,
    val path: String? = null,
    val version: String? = null,
    val details: String? = null
)

data class ToolItem(
    val id: String,
    val version: String,
    val description: String = "",
    val isInstalled: Boolean,
    val path: String? = null
)

sealed interface SdkInstallEvent {
    data class Progress(val percent: Int, val message: String) : SdkInstallEvent
    data class Success(val packagePath: String) : SdkInstallEvent
    data class Error(val exception: Throwable) : SdkInstallEvent
}

enum class SdkUpdateInterval(val label: String, val durationMs: Long) {
    HOURLY("Stündlich", 3_600_000L),
    EVERY_6_HOURS("Alle 6 Stunden", 21_600_000L),
    DAILY("Täglich", 86_400_000L),
    WEEKLY("Wöchentlich", 604_800_000L),
    MONTHLY("Monatlich", 2_592_000_000L)
}
