/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.LogEntry
import com.codeforge.core.domain.model.LogcatFilter
import com.codeforge.core.domain.model.LogcatSource
import com.codeforge.core.domain.model.LogcatSourceAvailability
import kotlinx.coroutines.flow.Flow

/** Zugriff auf Logcat-Einträge (eigener Prozess, `READ_LOGS` oder ADB-Loopback). */
interface LogcatRepository {
    /**
     * Startet den Strom der Einträge.
     *
     * @param source Quelle.
     * @param filter optionaler Filter.
     */
    fun startStream(source: LogcatSource, filter: LogcatFilter? = null): Flow<LogEntry>
    /** Leert den Puffer. */
    fun clearLogs()
    /** Liefert alle gepufferten Einträge. */
    fun getBufferedLogs(): List<LogEntry>
    /** Liefert die gepufferten Einträge, die [filter] erfüllen. */
    fun getFilteredLogs(filter: LogcatFilter): List<LogEntry>
    /**
     * Ermittelt die Prozess-ID einer App.
     *
     * @return PID oder `null`, wenn die App nicht läuft.
     */
    suspend fun resolvePidForPackage(packageName: String): Int?
    /** Ermittelt, welche Logcat-Quellen auf diesem Gerät verfügbar sind. */
    fun detectAvailableSources(): List<LogcatSourceAvailability>
}
