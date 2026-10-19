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

interface LogcatRepository {
    fun startStream(source: LogcatSource, filter: LogcatFilter? = null): Flow<LogEntry>
    fun clearLogs()
    fun getBufferedLogs(): List<LogEntry>
    fun getFilteredLogs(filter: LogcatFilter): List<LogEntry>
    suspend fun resolvePidForPackage(packageName: String): Int?
    fun detectAvailableSources(): List<LogcatSourceAvailability>
}
