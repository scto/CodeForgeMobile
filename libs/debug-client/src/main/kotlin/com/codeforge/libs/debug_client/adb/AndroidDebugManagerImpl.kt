/**
 * Modul: :libs:debug-client
 * @author Thomas Schmid
 */
package com.codeforge.libs.debug_client.adb

import com.codeforge.core.domain.model.AndroidDebugTarget
import com.codeforge.core.domain.model.DebugConfig
import com.codeforge.core.domain.model.DebugSessionState
import com.codeforge.core.domain.parser.AxmlManifestParser
import com.codeforge.core.domain.repository.AndroidDebugManager
import com.codeforge.core.domain.repository.DebugRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeoutException
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDebugManagerImpl @Inject constructor(
    private val adbRunner: AdbRunner,
    private val debugRepository: DebugRepository
) : AndroidDebugManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var activeTarget: AndroidDebugTarget? = null

    init {
        // Bereinige Portweiterleitung automatisch, wenn die Debug-Sitzung beendet wird
        debugRepository.state.onEach { state ->
            if (state is DebugSessionState.Terminated || state is DebugSessionState.Failed) {
                val target = activeTarget
                if (target != null) {
                    activeTarget = null
                    scope.run {
                        removeForward(target.port)
                        if (target.stopAppOnDetach) {
                            adbRunner.execute("shell", "am", "force-stop", target.packageName)
                        }
                    }
                }
            }
        }.launchIn(scope)
    }

    override suspend fun isAdbAvailable(): Boolean = adbRunner.isAvailable()

    override suspend fun getDebuggablePids(): Result<List<Int>> = withContext(Dispatchers.IO) {
        val res = adbRunner.execute("jdwp")
        if (!res.isSuccess) {
            return@withContext Result.failure(RuntimeException("adb jdwp fehlgeschlagen: ${res.stderr}"))
        }
        val pids = res.stdout.lines().mapNotNull { it.trim().toIntOrNull() }
        Result.success(pids)
    }

    override suspend fun isAppDebuggable(packageName: String, apkFile: File?): Result<Boolean> = withContext(Dispatchers.IO) {
        // 1. Wenn APK-Datei vorhanden ist, prüfe AXML Manifest
        if (apkFile != null && apkFile.exists() && apkFile.isFile) {
            val manifestBytes = readManifestBytes(apkFile)
            if (manifestBytes != null) {
                val metadata = AxmlManifestParser.parse(manifestBytes)
                if (metadata?.isDebuggable != null) {
                    return@withContext Result.success(metadata.isDebuggable == true)
                }
            }
        }

        // 2. Prüfung via ADB Dumpsys
        if (adbRunner.isAvailable()) {
            val dumpsys = adbRunner.execute("shell", "dumpsys", "package", packageName)
            if (dumpsys.isSuccess && dumpsys.stdout.isNotBlank()) {
                val stdout = dumpsys.stdout
                val hasDebuggableFlag = stdout.contains("DEBUGGABLE", ignoreCase = false)
                if (hasDebuggableFlag) {
                    return@withContext Result.success(true)
                }
                // Wenn Paket gefunden wurde, aber kein DEBUGGABLE-Flag hat
                if (stdout.contains("Package [$packageName]")) {
                    return@withContext Result.success(false)
                }
            }

            // 3. Wenn App bereits läuft, prüfe ob ihre PID im JDWP-Server gelistet ist
            val pid = findProcessId(packageName)
            if (pid != null) {
                val jdwpPids = getDebuggablePids().getOrDefault(emptyList())
                if (jdwpPids.contains(pid)) {
                    return@withContext Result.success(true)
                }
            }
        }

        // Falls unklar, standardmäßig als potenziell debuggbar annehmen
        Result.success(true)
    }

    override suspend fun startAppWaitingForDebugger(
        packageName: String,
        activityName: String?,
        timeoutMs: Long
    ): Result<Int> = withContext(Dispatchers.IO) {
        if (!adbRunner.isAvailable()) {
            return@withContext Result.failure(IllegalStateException("ADB-Binary nicht verfügbar"))
        }

        // App mit -D (Waiting for Debugger) starten
        val startResult = if (!activityName.isNullOrBlank()) {
            val component = if (activityName.startsWith(".")) {
                "$packageName/$packageName$activityName"
            } else if (activityName.contains("/")) {
                activityName
            } else {
                "$packageName/$activityName"
            }
            adbRunner.execute("shell", "am", "start", "-D", "-n", component)
        } else {
            adbRunner.execute("shell", "am", "start", "-D", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-p", packageName)
        }

        if (!startResult.isSuccess) {
            return@withContext Result.failure(RuntimeException("am start -D fehlgeschlagen: ${startResult.stderr} ${startResult.stdout}"))
        }

        // Warte auf Erscheinen der PID im System und im JDWP-Server
        val startTime = System.currentTimeMillis()
        var foundPid: Int? = null

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val pid = findProcessId(packageName)
            if (pid != null) {
                val jdwpPids = getDebuggablePids().getOrDefault(emptyList())
                if (jdwpPids.contains(pid)) {
                    foundPid = pid
                    break
                }
            }
            delay(200)
        }

        if (foundPid != null) {
            Result.success(foundPid)
        } else {
            Result.failure(TimeoutException("App $packageName wurde gestartet, erschien jedoch nicht innerhalb von ${timeoutMs}ms im JDWP-Server"))
        }
    }

    override suspend fun forwardPort(port: Int, pid: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val res = adbRunner.execute("forward", "tcp:$port", "jdwp:$pid")
        if (!res.isSuccess) {
            Result.failure(RuntimeException("adb forward tcp:$port jdwp:$pid fehlgeschlagen: ${res.stderr}"))
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun removeForward(port: Int): Result<Unit> = withContext(Dispatchers.IO) {
        val res = adbRunner.execute("forward", "--remove", "tcp:$port")
        if (!res.isSuccess && !res.stderr.contains("not found", ignoreCase = true)) {
            Result.failure(RuntimeException("adb forward --remove tcp:$port fehlgeschlagen: ${res.stderr}"))
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun attach(target: AndroidDebugTarget, apkFile: File?): Result<Unit> = withContext(Dispatchers.IO) {
        if (!adbRunner.isAvailable()) {
            return@withContext Result.failure(IllegalStateException("ADB nicht gefunden. Bitte Android Platform-Tools installieren."))
        }

        // Prüfe Debug-Fähigkeit
        val debuggableRes = isAppDebuggable(target.packageName, apkFile)
        if (debuggableRes.isSuccess && debuggableRes.getOrNull() == false) {
            return@withContext Result.failure(
                IllegalStateException("App ${target.packageName} ist nicht debuggbar (android:debuggable ist false). Bitte Debug-Build verwenden.")
            )
        }

        // PID ermitteln
        val pid: Int = when {
            target.waitForDebugger -> {
                val startRes = startAppWaitingForDebugger(target.packageName, target.activityName)
                if (startRes.isFailure) {
                    return@withContext Result.failure(startRes.exceptionOrNull() ?: RuntimeException("Konnte App nicht starten"))
                }
                startRes.getOrThrow()
            }
            target.pid != null -> {
                target.pid!!
            }
            else -> {
                findProcessId(target.packageName) ?: return@withContext Result.failure(
                    IllegalStateException("Laufender Prozess für ${target.packageName} nicht gefunden")
                )
            }
        }

        // Port-Weiterleitung einrichten
        val forwardRes = forwardPort(target.port, pid)
        if (forwardRes.isFailure) {
            return@withContext forwardRes
        }

        // Debug-Sitzung starten (Attach DAP)
        val config = DebugConfig(
            host = target.host,
            port = target.port,
            attachOnly = true
        )

        val startRes = debugRepository.start(config)
        if (startRes.isFailure) {
            // Bei Fehler Forwarding sofort wieder abbauen
            removeForward(target.port)
            return@withContext startRes
        }

        activeTarget = target
        Result.success(Unit)
    }

    override suspend fun detach(target: AndroidDebugTarget): Result<Unit> = withContext(Dispatchers.IO) {
        activeTarget = null
        debugRepository.stop()
        removeForward(target.port)

        if (target.stopAppOnDetach) {
            adbRunner.execute("shell", "am", "force-stop", target.packageName)
        }

        Result.success(Unit)
    }

    private suspend fun findProcessId(packageName: String): Int? {
        val pidofRes = adbRunner.execute("shell", "pidof", "-s", packageName)
        if (pidofRes.isSuccess && pidofRes.stdout.isNotBlank()) {
            val pid = pidofRes.stdout.trim().toIntOrNull()
            if (pid != null) return pid
        }

        // Fallback: pidof ohne -s
        val pidofAllRes = adbRunner.execute("shell", "pidof", packageName)
        if (pidofAllRes.isSuccess && pidofAllRes.stdout.isNotBlank()) {
            val firstPid = pidofAllRes.stdout.trim().split(Regex("\\s+")).firstOrNull()?.toIntOrNull()
            if (firstPid != null) return firstPid
        }

        return null
    }

    private fun readManifestBytes(apkFile: File): ByteArray? {
        return runCatching {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml") ?: return@use null
                zip.getInputStream(entry).use { it.readBytes() }
            }
        }.getOrNull()
    }
}
