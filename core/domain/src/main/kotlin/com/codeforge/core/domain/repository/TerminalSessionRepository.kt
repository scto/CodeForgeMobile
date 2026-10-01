// Modul: :core:domain
package com.codeforge.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

data class TerminalSession(
    val id: String,
    val processId: Int = 0,
    val title: String,
    val isRunning: Boolean = true
)

interface TerminalSessionRepository {
    val activeSessions: StateFlow<List<TerminalSession>>
    val isWakeLockAcquired: StateFlow<Boolean>

    suspend fun createSession(command: String? = null): TerminalSession
    suspend fun killSession(sessionId: String)
    suspend fun killAllSessions()
    suspend fun renameSession(sessionId: String, newTitle: String)
    fun setWakeLockState(acquired: Boolean)
    suspend fun sendVirtualKey(sessionId: String, key: String)
    fun getNativeSession(sessionId: String): Any?
}
