// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.TerminalSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interaktive Shell-Session im Termux-Bootstrap (echtes PTY über :libs:termux-emulator).
 * Implementiert in :libs:terminal-engine, konsumiert von :feature:terminal.
 */
interface TerminalSessionRepository {
    val sessionState: StateFlow<TerminalSessionState>
    val output: Flow<String>

    /**
     * Startet die Login-Shell. [initialCommand] wird nach dem Start einmalig eingegeben
     * (z. B. `codeforge-env setup --jdk 17` am Ende des Onboardings).
     */
    suspend fun start(initialCommand: String? = null)
    suspend fun sendInput(text: String)
    suspend fun stop()
}
