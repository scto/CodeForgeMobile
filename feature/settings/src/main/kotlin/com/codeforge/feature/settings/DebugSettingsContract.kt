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
    /** Höchstzahl der Log-Dateien. */
    val maxLogFiles: Int = com.codeforge.core.common.log.CfmLogger.DEFAULT_MAX_LOG_FILES,
    /** Abgeschlossene Log-Dateien zippen. */
    val compressLogs: Boolean = false,
    /** Ein Export läuft. */
    val isExporting: Boolean = false,
    val lastMessage: DebugMessage? = null
)

/** Einmalige Hinweise. */
sealed interface DebugMessage {
    /** Protokolle wurden gelöscht ([count] Dateien). */
    data class LogsCleared(val count: Int) : DebugMessage
    /** [count] Dateien wurden nach [directory] exportiert. */
    data class Exported(val count: Int, val directory: String) : DebugMessage
    /** Der Export ist fehlgeschlagen. */
    data class ExportFailed(val reason: String) : DebugMessage
}

/** Aktionen in den Debug-Einstellungen. */
sealed interface DebugSettingsUiEvent {
    /** Protokollierung ein-/ausgeschaltet. */
    data class LogEnabledChanged(val enabled: Boolean) : DebugSettingsUiEvent
    /** Höchstzahl der Log-Dateien geändert. */
    data class MaxLogFilesChanged(val value: Int) : DebugSettingsUiEvent
    /** Zippen abgeschlossener Log-Dateien ein-/ausgeschaltet. */
    data class CompressLogsChanged(val enabled: Boolean) : DebugSettingsUiEvent
    /** Alle Log-Dateien exportieren. */
    data class ExportLogs(val format: com.codeforge.core.common.log.CfmLogExporter.Format) : DebugSettingsUiEvent
    /** Protokolle löschen. */
    data object ClearLogs : DebugSettingsUiEvent
    /** Statistik neu lesen. */
    data object RefreshStats : DebugSettingsUiEvent
    /** Hinweis wurde angezeigt. */
    data object MessageShown : DebugSettingsUiEvent
}
