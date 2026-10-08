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
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TermuxScriptSdkRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : SdkRepository {

    override suspend fun sdkRootPath(): String? =
        TermuxEnvironment.sdkRoot.takeIf { TermuxEnvironment.isBootstrapInstalled() }

    private fun prepare(): Result<Unit> = runCatching {
        check(TermuxEnvironment.isBootstrapInstalled()) {
            Res.string(R.string.common_termux_umgebung_ist_noch_nicht)
        }
        ScriptInstaller(context).install().getOrThrow()
    }

    private fun start(args: String): Process {
        val spec = TermuxEnvironment.shellScriptSpec("codeforge-env --machine $args")
        return ProcessBuilder(spec.command)
            .directory(java.io.File(spec.workingDirectory))
            .redirectErrorStream(true)
            .apply { environment().putAll(spec.processEnv) }
            .start()
    }

    override suspend fun listAvailablePackages(): Result<List<ToolItem>> = withContext(Dispatchers.IO) {
        runCatching {
            prepare().getOrThrow()
            val process = start("list")
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
    }

    override fun installSdkTool(packagePath: String): Flow<SdkInstallEvent> = flow {
        if (!SdkScriptProtocol.isSafeId(packagePath)) {
            emit(SdkInstallEvent.Error(IllegalArgumentException(Res.string(R.string.terminal_engine_ungueltige_paket_id, packagePath))))
            return@flow
        }
        prepare().onFailure { emit(SdkInstallEvent.Error(it)); return@flow }
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
    }.flowOn(Dispatchers.IO)

    override suspend fun uninstallSdkTool(packagePath: String): Result<Unit> = withContext(Dispatchers.IO) {
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
}
