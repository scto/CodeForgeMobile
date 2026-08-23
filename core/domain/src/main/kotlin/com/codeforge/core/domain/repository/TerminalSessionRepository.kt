// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.TerminalSessionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interaktive Shell-Session innerhalb der per :libs:terminal-engine gebootstrappten
 * PRoot-Rootfs. Implementiert in :libs:terminal-engine, konsumiert von :feature:terminal.
 *
 * Hinweis: Die aktuelle Implementierung nutzt einen einfachen stdin/stdout-Pipe-Prozess
 * (kein echtes PTY). Für ein authentisches Terminal-Erlebnis (Zeilenbearbeitung,
 * Escape-Sequenzen, Fenstergrößen-Resize) wäre eine native PTY-Anbindung via JNI nötig
 * (ioctl TIOCSPTY, analog zu Termux' term-Bibliothek) — als nächster Ausbauschritt notiert.
 */
interface TerminalSessionRepository {
    val sessionState: StateFlow<TerminalSessionState>
    val output: Flow<String>

    suspend fun start(distro: String)
    suspend fun sendInput(text: String)
    suspend fun stop()
}
