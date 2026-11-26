/**
 * Modul: :libs:terminal-engine
 * @author Thomas Schmid
 *
 * [SdkRepository] als dünner Aufsatz auf das Termux-Skript `codeforge-env --machine`.
 * Alles Installieren/Entfernen passiert im Skript (Termux-Pakete für JDKs, eigene Builds für
 * Android-spezifische Teile); die App parst nur dessen Zeilenprotokoll. Ersetzt die frühere
 * PRoot-/sdkmanager-/Rootfs-Implementierung.
 */
package com.codeforge.libs.terminal_engine

import com.codeforge.core.domain.runtime.IdeActivityRegistry.trackedAs
import android.content.Context
import com.codeforge.core.domain.model.SdkInstallEvent
import com.codeforge.core.domain.model.ToolItem
import com.codeforge.core.domain.repository.SdkRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** SDK-Verwaltung über das Skript `codeforge-env` in Termux; liest dessen Zeilenprotokoll als Flow. */
@Singleton
class TermuxScriptSdkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SdkRepository {

    /**
     * Installationen laufen nacheinander: Mehrere gleichzeitige `codeforge-env install` (z. B. zwei Platforms aus
     * der Mehrfachauswahl) starten parallel den `sdkmanager` im selben SDK-Verzeichnis und beschädigen sich
     * gegenseitig (Platform-Ordner ohne `android.jar`). Wartende Pakete zeigen 0 % bis sie an der Reihe sind.
     */
    private val installLock = Mutex()

    @Volatile
    private var memo: Pair<Long, List<ToolItem>>? = null

    private val listCache get() = java.io.File(context.filesDir, "sdk-packages.cache")

    override suspend fun sdkRootPath(): String? =
        TermuxEnvironment.sdkRoot.takeIf { TermuxEnvironment.isBootstrapInstalled() }

    private fun prepare(): Result<Unit> = runCatching {
        check(TermuxEnvironment.isBootstrapInstalled()) {
            Res.string(R.string.common_termux_umgebung_ist_noch_nicht)
        }
        ScriptInstaller(context).install().getOrThrow()
    }

    private fun start(args: String, refresh: Boolean = false): Process {
        val spec = TermuxEnvironment.shellScriptSpec("codeforge-env --machine $args")
        return ProcessBuilder(spec.command)
            .directory(java.io.File(spec.workingDirectory))
            .redirectErrorStream(true)
            .apply {
                environment().putAll(spec.processEnv)
                // Umgeht die Zwischenspeicher des Skripts (Release-Listen, Manifest) beim manuellen Aktualisieren.
                if (refresh) environment()["CFSDK_REFRESH"] = "1"
            }
            .start()
    }

    override suspend fun listAvailablePackages(): Result<List<ToolItem>> = listAvailablePackages(refresh = false)

    override suspend fun cachedPackages(): List<ToolItem>? = withContext(Dispatchers.IO) {
        memo?.second ?: runCatching { SdkPackageCache.decode(listCache.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    override suspend fun listAvailablePackages(refresh: Boolean): Result<List<ToolItem>> = withContext(Dispatchers.IO) {
        memo?.takeIf { !refresh && System.currentTimeMillis() - it.first < MEMO_MS }?.let { return@withContext Result.success(it.second) }
        listFromScript(refresh).onSuccess { items ->
            memo = System.currentTimeMillis() to items
            runCatching { listCache.writeText(SdkPackageCache.encode(items)) }
        }
    }

    private fun listFromScript(refresh: Boolean): Result<List<ToolItem>> =
        runCatching {
            prepare().getOrThrow()
            val process = start("list", refresh)
            val items = mutableListOf<ToolItem>()
            var error: String? = null
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    when (val l = SdkScriptProtocol.parse(raw)) {
                        is SdkScriptLine.Item -> items += ToolItem(l.id, l.version, l.installed, l.path)
                        is SdkScriptLine.Error -> error = l.message
                        else -> Unit
                    }
                }
            }
            val exit = process.waitFor()
            if (exit != 0 && items.isEmpty()) error(error ?: Res.string(R.string.terminal_engine_codeforge_env_list_endete_mit, exit))
            items
        }

    override fun installSdkTool(packagePath: String): Flow<SdkInstallEvent> = flow {
        if (!SdkScriptProtocol.isSafeId(packagePath)) {
            emit(SdkInstallEvent.Error(IllegalArgumentException(Res.string(R.string.terminal_engine_ungueltige_paket_id, packagePath))))
            return@flow
        }
        prepare().onFailure { emit(SdkInstallEvent.Error(it)); return@flow }
        emit(SdkInstallEvent.Progress(0, Res.string(R.string.common_installation_wartet)))
        installLock.withLock { runInstall(packagePath) }
        memo = null
    }.flowOn(Dispatchers.IO).trackedAs("SDK $packagePath")

    private suspend fun kotlinx.coroutines.flow.FlowCollector<SdkInstallEvent>.runInstall(packagePath: String) {
        var failure: String? = null
        var succeeded = false
        try {
            val process = start("install '$packagePath'")
            process.inputStream.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    when (val l = SdkScriptProtocol.parse(raw)) {
                        is SdkScriptLine.Progress -> emit(SdkInstallEvent.Progress(l.percent, l.message))
                        is SdkScriptLine.Ok -> if (l.id == packagePath) succeeded = true
                        is SdkScriptLine.Error -> failure = l.message
                        else -> Unit
                    }
                }
            }
            val exit = process.waitFor()
            if (exit == 0 && succeeded) emit(SdkInstallEvent.Success(packagePath))
            else emit(SdkInstallEvent.Error(RuntimeException(failure ?: Res.string(R.string.terminal_engine_codeforge_env_endete_mit_code, exit))))
        } catch (e: Exception) {
            emit(SdkInstallEvent.Error(e))
        }
    }

    override suspend fun uninstallSdkTool(packagePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        memo = null
        runCatching {
            require(SdkScriptProtocol.isSafeId(packagePath)) { Res.string(R.string.terminal_engine_ungueltige_paket_id, packagePath) }
            prepare().getOrThrow()
            val process = start("uninstall '$packagePath'")
            var failure: String? = null
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { raw -> (SdkScriptProtocol.parse(raw) as? SdkScriptLine.Error)?.let { failure = it.message } }
            }
            val exit = process.waitFor()
            if (exit != 0) error(failure ?: Res.string(R.string.terminal_engine_codeforge_env_endete_mit_code, exit))
        }
    }

    private companion object {
        /** Wiederverwendung einer geladenen Liste im Speicher (Seitenwechsel ohne Skriptstart). */
        const val MEMO_MS = 5L * 60 * 1000
    }
}

/** Speicherform der Paketliste (eine Zeile je Paket: `id|version|0/1|pfad`). */
internal object SdkPackageCache {
    /** Liste → Text. */
    fun encode(items: List<ToolItem>): String =
        items.joinToString("\n") { "${it.id}|${it.version}|${if (it.isInstalled) 1 else 0}|${it.path.orEmpty()}" }

    /** Text → Liste; unlesbare Zeilen werden übersprungen. */
    fun decode(text: String): List<ToolItem> = text.lineSequence().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size < 4 || parts[0].isBlank()) {
            null
        } else {
            ToolItem(parts[0], parts[1], parts[2] == "1", parts.drop(3).joinToString("|").ifBlank { null })
        }
    }.toList()
}
