// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import android.content.Context
import com.codeforge.core.domain.model.TerminalSessionState
import com.codeforge.core.domain.repository.DistroBootstrapRepository
import com.codeforge.core.domain.repository.TerminalSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TerminalSessionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val distroBootstrapRepository: DistroBootstrapRepository
) : TerminalSessionRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var process: Process? = null
    private var stdinWriter: BufferedWriter? = null

    private val _sessionState = MutableStateFlow(TerminalSessionState.STOPPED)
    override val sessionState: StateFlow<TerminalSessionState> = _sessionState.asStateFlow()

    private val _output = MutableSharedFlow<String>(extraBufferCapacity = 256)
    override val output: Flow<String> = _output.asSharedFlow()

    override suspend fun start(distro: String): Unit = withContext(Dispatchers.IO) {
        runCatching {
            _sessionState.value = TerminalSessionState.STARTING

            val rootfsDir = File(distroBootstrapRepository.rootfsPath(distro))
            if (!rootfsDir.exists()) {
                error("Rootfs für '$distro' ist nicht eingerichtet. Bitte zuerst im Onboarding oder in den Einstellungen bootstrappen.")
            }

            // TODO: prootBinaryPath setzt ein natives libproot.so-Artefakt im jniLibs-
            // Verzeichnis der App voraus (siehe ProotCommandBuilder-Dokumentation).
            val prootBinaryPath = "${context.applicationInfo.nativeLibraryDir}/libproot.so"
            val command = ProotCommandBuilder.build(
                prootBinaryPath = prootBinaryPath,
                rootfsDir = rootfsDir,
                command = listOf("/bin/sh")
            )

            val startedProcess = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()

            process = startedProcess
            stdinWriter = startedProcess.outputStream.bufferedWriter()

            scope.launch { readOutputLoop(startedProcess) }

            _sessionState.value = TerminalSessionState.RUNNING
        }.onFailure { throwable ->
            _output.tryEmit("Fehler beim Starten der Shell: ${throwable.message}\n")
            _sessionState.value = TerminalSessionState.FAILED
        }
    }

    private suspend fun readOutputLoop(process: Process) {
        val reader = process.inputStream.bufferedReader()
        val buffer = CharArray(4096)
        try {
            while (true) {
                val readCount = reader.read(buffer)
                if (readCount == -1) break
                _output.emit(String(buffer, 0, readCount))
            }
        } catch (throwable: Throwable) {
            _output.emit("\n[Ausgabestrom beendet: ${throwable.message}]\n")
        } finally {
            if (_sessionState.value != TerminalSessionState.STOPPED) {
                _sessionState.value = TerminalSessionState.EXITED
            }
        }
    }

    override suspend fun sendInput(text: String): Unit = withContext(Dispatchers.IO) {
        runCatching {
            val writer = stdinWriter ?: error("Keine aktive Sitzung.")
            writer.write(text)
            writer.write("\n")
            writer.flush()
        }.onFailure { throwable ->
            _output.emit("Fehler beim Senden: ${throwable.message}\n")
        }
    }

    override suspend fun stop(): Unit = withContext(Dispatchers.IO) {
        runCatching {
            stdinWriter?.close()
            process?.destroy()
        }
        stdinWriter = null
        process = null
        _sessionState.value = TerminalSessionState.STOPPED
    }
}
