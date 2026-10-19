/**
 * Modul: :core:data
 * @author Thomas Schmid
 */
package com.codeforge.core.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.codeforge.core.domain.model.LogEntry
import com.codeforge.core.domain.model.LogcatFilter
import com.codeforge.core.domain.model.LogcatRingBuffer
import com.codeforge.core.domain.model.LogcatSource
import com.codeforge.core.domain.model.LogcatSourceAvailability
import com.codeforge.core.domain.parser.LogcatParser
import com.codeforge.core.domain.repository.LogcatRepository
import com.codeforge.core.resources.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Singleton
class LogcatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logcatParser: LogcatParser,
) : LogcatRepository {

    private val ringBuffer = LogcatRingBuffer(capacity = 50_000)

    // Für JVM-Unit-Tests überschreibbar
    internal var processLauncher: (command: List<String>, env: Map<String, String>, workingDir: File) -> Process = { cmd, env, dir ->
        ProcessBuilder(cmd).apply {
            directory(dir)
            environment().putAll(env)
            redirectErrorStream(true)
        }.start()
    }

    internal var permissionChecker: (String) -> Boolean = { perm ->
        runCatching {
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    override fun startStream(source: LogcatSource, filter: LogcatFilter?): Flow<LogEntry> = flow {
        val cmd = buildCommand(source, filter)
        val process = processLauncher(cmd, emptyMap(), context.filesDir)

        try {
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var currentEntry: LogEntry? = null

            while (currentCoroutineContext().isActive) {
                val line = withContext(Dispatchers.IO) { reader.readLine() } ?: break
                when (val r = logcatParser.parseLine(line)) {
                    is LogcatParser.ParseLineResult.NewEntry -> {
                        val prev = currentEntry
                        if (prev != null && prev.isCrash && r.entry.tag.equals("AndroidRuntime", ignoreCase = true) && prev.pid == r.entry.pid) {
                            val newFrames = if (r.entry.stackFrames.isNotEmpty()) {
                                prev.stackFrames + r.entry.stackFrames
                            } else {
                                prev.stackFrames
                            }
                            currentEntry = prev.copy(
                                message = "${prev.message}\n${r.entry.message}",
                                stackFrames = newFrames
                            )
                        } else {
                            if (currentEntry != null) {
                                ringBuffer.add(currentEntry)
                                emit(currentEntry)
                            }
                            currentEntry = r.entry
                        }
                    }
                    is LogcatParser.ParseLineResult.Continuation -> {
                        if (currentEntry != null) {
                            val frame = r.stackFrame
                            val newFrames = if (frame != null) {
                                currentEntry.stackFrames + frame
                            } else {
                                currentEntry.stackFrames
                            }
                            val isCrash = currentEntry.isCrash || r.text.contains("FATAL EXCEPTION", ignoreCase = true)
                            currentEntry = currentEntry.copy(
                                message = "${currentEntry.message}\n${r.text}",
                                isCrash = isCrash,
                                stackFrames = newFrames
                            )
                        }
                    }
                    is LogcatParser.ParseLineResult.Ignored -> {}
                }
            }

            if (currentEntry != null) {
                ringBuffer.add(currentEntry)
                emit(currentEntry)
            }
        } finally {
            withContext(Dispatchers.IO) {
                runCatching { process.destroyForcibly() }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun clearLogs() {
        ringBuffer.clear()
    }

    override fun getBufferedLogs(): List<LogEntry> {
        return ringBuffer.snapshot()
    }

    override fun getFilteredLogs(filter: LogcatFilter): List<LogEntry> {
        return ringBuffer.filteredSnapshot(filter)
    }

    override suspend fun resolvePidForPackage(packageName: String): Int? = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext null
        runCatching {
            val proc = processLauncher(listOf("pidof", packageName), emptyMap(), context.filesDir)
            val pidText = proc.inputStream.bufferedReader().readLine()?.trim()
            proc.destroyForcibly()
            pidText?.split("\\s+".toRegex())?.firstOrNull()?.toIntOrNull()
        }.getOrNull()
    }

    override fun detectAvailableSources(): List<LogcatSourceAvailability> {
        val hasReadLogs = permissionChecker(Manifest.permission.READ_LOGS)

        val adbBinary = findAdbBinary()
        val hasAdb = adbBinary != null

        return listOf(
            LogcatSourceAvailability(
                source = LogcatSource.OWN_PROCESS,
                isAvailable = true,
                explanationResId = R.string.logcat_quelle_own_process_info
            ),
            LogcatSourceAvailability(
                source = LogcatSource.READ_LOGS_PERMISSION,
                isAvailable = hasReadLogs,
                explanationResId = if (hasReadLogs) R.string.logcat_quelle_read_logs_info else R.string.logcat_quelle_nicht_verfuegbar,
                helpInstructionResId = if (!hasReadLogs) R.string.logcat_quelle_read_logs_hilfe else null
            ),
            LogcatSourceAvailability(
                source = LogcatSource.ADB_LOOPBACK,
                isAvailable = hasAdb,
                explanationResId = if (hasAdb) R.string.logcat_quelle_adb_info else R.string.logcat_quelle_nicht_verfuegbar,
                helpInstructionResId = if (!hasAdb) R.string.logcat_quelle_adb_hilfe else null
            )
        )
    }

    private fun buildCommand(source: LogcatSource, filter: LogcatFilter?): List<String> {
        val baseCmd = when (source) {
            LogcatSource.ADB_LOOPBACK -> {
                val adb = findAdbBinary() ?: "adb"
                mutableListOf(adb, "logcat", "-v", "threadtime")
            }
            else -> mutableListOf("logcat", "-v", "threadtime")
        }

        val pid = filter?.targetPid
        if (pid != null && pid > 0) {
            baseCmd.add("--pid=$pid")
        }

        return baseCmd
    }

    private fun findAdbBinary(): String? {
        val candidates = listOf(
            "/data/data/com.termux/files/home/android-sdk/platform-tools/adb",
            "/data/data/com.termux/files/usr/bin/adb",
            "adb"
        )
        for (candidate in candidates) {
            val file = File(candidate)
            if (file.exists() && file.canExecute()) {
                return file.absolutePath
            }
        }
        return null
    }
}
