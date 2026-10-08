/**
 * Modul: :libs:terminal-engine
 * @author Thomas Schmid
 *
 * Interaktive Shell im Termux-Bootstrap (/data/data/com.codeforge.app/files/usr) über den
 * vendorten Termux-Terminal-Emulator (:libs:termux-emulator). Es gibt kein PRoot und keine
 * Distro-Auswahl mehr — genau ein Prefix. [start] nimmt optional ein Startkommando entgegen
 * (z. B. `codeforge-env setup …` am Ende des Onboardings).
 *
 * TerminalSession/JNI erwarten den Main-Thread (Handler mit Looper.getMainLooper()) für
 * Konstruktion und I/O-Callbacks — Erstellung und write()/updateSize() laufen auf Dispatchers.Main.
 */
package com.codeforge.libs.terminal_engine

import android.content.Context
import com.codeforge.core.domain.model.TerminalSessionState
import com.codeforge.core.domain.repository.TerminalSessionRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.terminal.TerminalSession
import com.codeforge.terminal.TerminalSessionClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TERMINAL_COLUMNS = 100
private const val TERMINAL_ROWS = 40

@Singleton
class TerminalSessionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TerminalSessionRepository, TerminalSessionClient {

    private var session: TerminalSession? = null

    private val _sessionState = MutableStateFlow(TerminalSessionState.STOPPED)
    override val sessionState: StateFlow<TerminalSessionState> = _sessionState.asStateFlow()

    /**
     * Emittiert den vollständigen sichtbaren Bildschirminhalt bei jeder Änderung — kein
     * Zeilen-Log-Anhängen, ein echter Terminal-Emulator rendert einen aktuellen
     * Bildschirmzustand, keine anwachsende Textliste (siehe TerminalViewModel in
     * :feature:terminal, unverändert).
     */
    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 64)
    override val output: Flow<String> = _output.asSharedFlow()

    override suspend fun start(initialCommand: String?) {
        _sessionState.value = TerminalSessionState.STARTING
        runCatching {
            if (!TermuxEnvironment.isBootstrapInstalled()) {
                error(
                    Res.string(R.string.terminal_engine_termux_bootstrap_ist_unter_nicht, TermuxEnvironment.prefix)
                )
            }

            val homeDir = File(TermuxEnvironment.home).apply { mkdirs() }
            val shellPath = TermuxEnvironment.bashPath
            val argv = arrayOf(shellPath, "-l") // Login-Shell (lädt profile.d/codeforge-android.sh)
            val env = TermuxEnvironment.environment().map { (k, v) -> "$k=$v" }.toTypedArray()

            withContext(Dispatchers.Main) {
                val newSession = TerminalSession(
                    shellPath,
                    homeDir.absolutePath,
                    argv,
                    env,
                    4000,
                    this@TerminalSessionRepositoryImpl
                )
                session = newSession
                newSession.updateSize(TERMINAL_COLUMNS, TERMINAL_ROWS)
            }

            _sessionState.value = TerminalSessionState.RUNNING
            initialCommand?.takeIf { it.isNotBlank() }?.let { sendInput(it) }
        }.onFailure { throwable ->
            _output.tryEmit(Res.string(R.string.terminal_engine_fehler_beim_starten_der_shell, throwable.message))
            _sessionState.value = TerminalSessionState.FAILED
        }
    }

    override suspend fun sendInput(text: String) {
        val activeSession = session
        if (activeSession == null) {
            _output.emit(Res.string(R.string.terminal_engine_fehler_beim_senden_keine_aktive))
            return
        }
        withContext(Dispatchers.Main) {
            // TerminalSession kennt nur write(byte[], offset, count), kein write(String) —
            // anders als die vorherige PRoot-Implementierung angenommen hatte.
            val bytes = "$text\n".toByteArray(Charsets.UTF_8)
            activeSession.write(bytes, 0, bytes.size)
        }
    }

    override suspend fun stop() {
        withContext(Dispatchers.Main) {
            session?.finishIfRunning()
        }
        session = null
        _sessionState.value = TerminalSessionState.STOPPED
    }

    // ---- TerminalSessionClient: Callbacks von TerminalSession, laufen auf Main-Thread ----

    override fun onTextChanged(changedSession: TerminalSession) {
        val screenText = changedSession.emulator?.screen?.transcriptText ?: return
        _output.tryEmit(screenText)
    }

    override fun onTitleChanged(changedSession: TerminalSession) = Unit

    override fun onSessionFinished(finishedSession: TerminalSession) {
        _sessionState.value = TerminalSessionState.EXITED
    }

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) = Unit

    override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit

    override fun onBell(session: TerminalSession) = Unit

    override fun onColorsChanged(session: TerminalSession) = Unit

    override fun onTerminalCursorStateChange(state: Boolean) = Unit

    override fun setTerminalShellPid(session: TerminalSession, pid: Int) = Unit

    override fun getTerminalCursorStyle(): Int? = null

    override fun logError(tag: String?, message: String?) {
        android.util.Log.e(tag ?: "TerminalSession", message ?: "")
    }

    override fun logWarn(tag: String?, message: String?) {
        android.util.Log.w(tag ?: "TerminalSession", message ?: "")
    }

    override fun logInfo(tag: String?, message: String?) {
        android.util.Log.i(tag ?: "TerminalSession", message ?: "")
    }

    override fun logDebug(tag: String?, message: String?) {
        android.util.Log.d(tag ?: "TerminalSession", message ?: "")
    }

    override fun logVerbose(tag: String?, message: String?) {
        android.util.Log.v(tag ?: "TerminalSession", message ?: "")
    }

    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        android.util.Log.e(tag ?: "TerminalSession", message ?: "", e)
    }

    override fun logStackTrace(tag: String?, e: Exception?) {
        android.util.Log.e(tag ?: "TerminalSession", "", e)
    }
}
