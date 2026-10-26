/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.runtime.Immutable

@Immutable
data class DebugSettingsUiState(
    val logEnabled: Boolean = true,
    val logDirectory: String = "",
    val logFileCount: Int = 0,
    val logSizeBytes: Long = 0L,
    val lastMessage: DebugMessage? = null
)

sealed interface DebugMessage {
    data class LogsCleared(val count: Int) : DebugMessage
}

sealed interface DebugSettingsUiEvent {
    data class LogEnabledChanged(val enabled: Boolean) : DebugSettingsUiEvent
    data object ClearLogs : DebugSettingsUiEvent
    data object RefreshStats : DebugSettingsUiEvent
    data object MessageShown : DebugSettingsUiEvent
}
