// Modul: :libs:terminal-engine
package com.codeforge.libs.terminal_engine

import android.content.Context
import com.codeforge.core.domain.repository.TerminalSession
import com.codeforge.core.domain.repository.TerminalSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import com.codeforge.core.domain.repository.SystemPathsRepository
import com.codeforge.core.datastore.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

@Singleton
class TerminalSessionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemPaths: SystemPathsRepository,
    private val settingsRepository: SettingsRepository,
    private val rootfsBootstrapper: RootfsBootstrapper
) : TerminalSessionRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    override val activeSessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _isWakeLockAcquired = MutableStateFlow(false)
    override val isWakeLockAcquired: StateFlow<Boolean> = _isWakeLockAcquired.asStateFlow()

    private val nativeSessions = mutableMapOf<String, com.nyamux.terminal.TerminalSession>()

    override suspend fun createSession(command: String?): TerminalSession = withContext(Dispatchers.IO) {
        val rootfsDir = File(systemPaths.getDistroDir("ubuntu"))
        if (!rootfsDir.exists()) rootfsDir.mkdirs()

        rootfsBootstrapper.ensureRootfsReady { }

        val localDir = File(systemPaths.getLocalDir())
        val localTmpDir = File(localDir, "tmp")
        if (!localTmpDir.exists()) localTmpDir.mkdirs()
        if (!localDir.exists()) localDir.mkdirs()
        File(localDir, "stat").apply { if (!exists()) writeText("") }
        File(localDir, "vmstat").apply { if (!exists()) writeText("") }

        val localBinDir = File(systemPaths.getLocalBinDir())
        if (!localBinDir.exists()) localBinDir.mkdirs()

        // Extract Ubuntu bootstrap init scripts
        val initUbuntuHostFile = File(localBinDir, "init-ubuntu-host")
        runCatching {
            context.assets.open("init-ubuntu-host.sh").use { input ->
                FileOutputStream(initUbuntuHostFile).use { output -> input.copyTo(output) }
            }
            initUbuntuHostFile.setExecutable(true)
        }
        val initUbuntuRootFile = File(localBinDir, "init-ubuntu-root")
        runCatching {
            context.assets.open("init-ubuntu-root.sh").use { input ->
                FileOutputStream(initUbuntuRootFile).use { output -> input.copyTo(output) }
            }
            initUbuntuRootFile.setExecutable(true)
        }
        val initUbuntuFile = File(localBinDir, "init-ubuntu")
        runCatching {
            context.assets.open("init-ubuntu.sh").use { input ->
                FileOutputStream(initUbuntuFile).use { output -> input.copyTo(output) }
            }
            initUbuntuFile.setExecutable(true)
        }

        val prefix = context.filesDir.absolutePath
        val linkerPath = "/system/bin/linker64"
        val prootBinaryPath = "${context.applicationInfo.nativeLibraryDir}/libproot.so"

        val envMap = ProotCommandBuilder.defaultEnv(rootfsDir.absolutePath).toMutableMap()
        envMap["PREFIX"] = prefix
        envMap["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
        envMap["LINKER"] = linkerPath
        envMap["NATIVE_LIB_DIR"] = context.applicationInfo.nativeLibraryDir
        envMap["PKG"] = context.packageName
        envMap["PROOT_TMP_DIR"] = systemPaths.getLocalTmpDir()
        envMap["TMPDIR"] = systemPaths.getLocalTmpDir()
        envMap["PROOT_BINARY"] = prootBinaryPath
        envMap["PROOT_LOADER"] = "${context.applicationInfo.nativeLibraryDir}/libproot-loader.so"
        envMap["PROOT_LOADER_32"] = "${context.applicationInfo.nativeLibraryDir}/libproot-loader32.so"

        val envList = envMap.map { (k, v) -> "$k=$v" }.toMutableList()

        val termSettings = settingsRepository.appSettings.first().terminal
        val scrollback = if (termSettings.scrollbackLines > 0) termSettings.scrollbackLines else 2000
        val initHostFile = File(localBinDir, "init-ubuntu-host")

        val statFile = File(localDir, "stat")
        if (!statFile.exists()) statFile.createNewFile()
        val vmstatFile = File(localDir, "vmstat")
        if (!vmstatFile.exists()) vmstatFile.createNewFile()

        val prootArgs = arrayOf("-c", initHostFile.absolutePath)
        val shell = "/system/bin/sh"

        val nativeSession = withContext(Dispatchers.Main) {
            com.nyamux.terminal.TerminalSession(
                shell,
                rootfsDir.absolutePath,
                prootArgs,
                envList.toTypedArray(),
                scrollback,
                object : com.nyamux.terminal.TerminalSessionClient {
                    override fun onTextChanged(session: com.nyamux.terminal.TerminalSession) {}
                    override fun onTitleChanged(session: com.nyamux.terminal.TerminalSession) {}
                    override fun onSessionFinished(session: com.nyamux.terminal.TerminalSession) {
                        val sessionEntry = nativeSessions.entries.firstOrNull { it.value == session }
                        val targetId = sessionEntry?.key
                        if (targetId != null) {
                            scope.launch {
                                killSession(targetId)
                            }
                        }
                    }
                    override fun onCopyTextToClipboard(session: com.nyamux.terminal.TerminalSession, text: String?) {}
                    override fun onPasteTextFromClipboard(session: com.nyamux.terminal.TerminalSession?) {}
                    override fun onBell(session: com.nyamux.terminal.TerminalSession) {}
                    override fun onColorsChanged(session: com.nyamux.terminal.TerminalSession) {}
                    override fun onTerminalCursorStateChange(state: Boolean) {}
                    override fun setTerminalShellPid(session: com.nyamux.terminal.TerminalSession, pid: Int) {}
                    override fun getTerminalCursorStyle(): Int = 0
                    override fun logError(tag: String?, message: String?) {}
                    override fun logWarn(tag: String?, message: String?) {}
                    override fun logInfo(tag: String?, message: String?) {}
                    override fun logDebug(tag: String?, message: String?) {}
                    override fun logVerbose(tag: String?, message: String?) {}
                    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
                    override fun logStackTrace(tag: String?, e: Exception?) {}
                }
            )
        }

        val id = UUID.randomUUID().toString()
        val pid = nativeSession.pid

        val sessionCount = _sessions.value.size
        val titleName = if (sessionCount == 0) "main" else "main #$sessionCount"
        val newSession = TerminalSession(id = id, processId = pid, title = titleName, isRunning = true)
        nativeSessions[id] = nativeSession
        _sessions.update { it + newSession }
        newSession
    }

    override suspend fun killSession(sessionId: String) {
        nativeSessions[sessionId]?.finishIfRunning()
        nativeSessions.remove(sessionId)
        _sessions.update { it.filter { session -> session.id != sessionId } }
    }

    override suspend fun killAllSessions() {
        nativeSessions.values.forEach { session ->
            runCatching { session.finishIfRunning() }
        }
        nativeSessions.clear()
        _sessions.update { emptyList() }
    }

    override suspend fun renameSession(sessionId: String, newTitle: String) {
        _sessions.update { list ->
            list.map { session ->
                if (session.id == sessionId) session.copy(title = newTitle) else session
            }
        }
    }

    override fun setWakeLockState(acquired: Boolean) {
        _isWakeLockAcquired.value = acquired
    }

    override suspend fun sendVirtualKey(sessionId: String, key: String) {
        val session = nativeSessions[sessionId] ?: return
        when (key.uppercase()) {
            "ESC" -> session.write("\u001B")
            "TAB" -> session.write("\t")
            "UP", "▲" -> session.write("\u001B[A")
            "DN", "DOWN", "▼" -> session.write("\u001B[B")
            "LEFT", "◀" -> session.write("\u001B[D")
            "RIGHT", "▶" -> session.write("\u001B[C")
            "HOME" -> session.write("\u001B[H")
            "END" -> session.write("\u001B[F")
            "PGUP" -> session.write("\u001B[5~")
            "PGDN" -> session.write("\u001B[6~")
            "DEL" -> session.write("\u001B[3~")
            "CTRL+A", "CTRL-A" -> session.write("\u0001")
            "CTRL+B", "CTRL-B" -> session.write("\u0002")
            "CTRL+C", "CTRL-C" -> session.write("\u0003")
            "CTRL+D", "CTRL-D" -> session.write("\u0004")
            "CTRL+E", "CTRL-E" -> session.write("\u0005")
            "CTRL+F", "CTRL-F" -> session.write("\u0006")
            "CTRL+G", "CTRL-G" -> session.write("\u0007")
            "CTRL+H", "CTRL-H" -> session.write("\u0008")
            "CTRL+I", "CTRL-I" -> session.write("\t")
            "CTRL+J", "CTRL-J" -> session.write("\n")
            "CTRL+K", "CTRL-K" -> session.write("\u000B")
            "CTRL+L", "CTRL-L" -> session.write("\u000C")
            "CTRL+M", "CTRL-M" -> session.write("\r")
            "CTRL+N", "CTRL-N" -> session.write("\u000E")
            "CTRL+O", "CTRL-O" -> session.write("\u000F")
            "CTRL+P", "CTRL-P" -> session.write("\u0010")
            "CTRL+Q", "CTRL-Q" -> session.write("\u0011")
            "CTRL+R", "CTRL-R" -> session.write("\u0012")
            "CTRL+S", "CTRL-S" -> session.write("\u0013")
            "CTRL+T", "CTRL-T" -> session.write("\u0014")
            "CTRL+U", "CTRL-U" -> session.write("\u0015")
            "CTRL+V", "CTRL-V" -> session.write("\u0016")
            "CTRL+W", "CTRL-W" -> session.write("\u0017")
            "CTRL+X", "CTRL-X" -> session.write("\u0018")
            "CTRL+Y", "CTRL-Y" -> session.write("\u0019")
            "CTRL+Z", "CTRL-Z" -> session.write("\u001A")
            else -> session.write(key)
        }
    }

    override fun getNativeSession(sessionId: String): Any? {
        return nativeSessions[sessionId]
    }
}

private fun com.nyamux.terminal.TerminalSession.write(text: String) {
    val bytes = text.toByteArray(Charsets.UTF_8)
    write(bytes, 0, bytes.size)
}

