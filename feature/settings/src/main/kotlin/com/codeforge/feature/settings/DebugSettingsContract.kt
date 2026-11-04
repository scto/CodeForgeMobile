/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.runtime.Immutable

/** Zustand der Debug-Einstellungen (Protokollierung, Dateien, Größe). */
@Immutable
data class DebugSettingsUiState(
    val logEnabled: Boolean = true,
    val logDirectory: String = "",
    val logFileCount: Int = 0,
    val logSizeBytes: Long = 0L,
    val lastMessage: DebugMessage? = null
)

/** Einmalige Hinweise. */
sealed interface DebugMessage {
    /** Protokolle wurden gelöscht ([count] Dateien). */
    data class LogsCleared(val count: Int) : DebugMessage
}

/** Aktionen in den Debug-Einstellungen. */
sealed interface DebugSettingsUiEvent {
    /** Protokollierung ein-/ausgeschaltet. */
    data class LogEnabledChanged(val enabled: Boolean) : DebugSettingsUiEvent
    /** Protokolle löschen. */
    data object ClearLogs : DebugSettingsUiEvent
    /** Statistik neu lesen. */
    data object RefreshStats : DebugSettingsUiEvent
    /** Hinweis wurde angezeigt. */
    data object MessageShown : DebugSettingsUiEvent
}
