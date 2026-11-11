/**
 * Modul: :libs:terminal-engine
 * @author Thomas Schmid
 *
 * [PackageRepository] als dünner Aufsatz auf `codeforge-env --machine` (pkglist, pkg, remove, update, upgrade).
 */
package com.codeforge.libs.terminal_engine

import android.content.Context
import com.codeforge.core.domain.model.AptPackage
import com.codeforge.core.domain.model.PackageEvent
import com.codeforge.core.domain.repository.PackageRepository
import com.codeforge.core.domain.runtime.IdeActivityRegistry.trackedAs
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Paketverwaltung über das Skript `codeforge-env` (APT-Repo samt `large-packages.txt`). */
@Singleton
class TermuxScriptPackageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : PackageRepository {

    /** dpkg/apt sperren ihre Datenbank: Aktionen laufen nacheinander. */
    private val lock = Mutex()

    private fun prepare(): Result<Unit> = runCatching {
        check(TermuxEnvironment.isBootstrapInstalled()) { Res.string(R.string.common_termux_umgebung_ist_noch_nicht) }
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

    override suspend fun listPackages(): Result<List<AptPackage>> = withContext(Dispatchers.IO) {
        runCatching {
            prepare().getOrThrow()
            val process = start("pkglist")
            val items = mutableListOf<AptPackage>()
            var failure: String? = null
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { raw ->
                    when (val l = SdkScriptProtocol.parse(raw)) {
                        is SdkScriptLine.Pkg -> items += AptPackage(
                            name = l.name, installedVersion = l.installed, availableVersion = l.available, sizeKib = l.sizeKib,
                            isLarge = 'L' in l.flags, hasUpdate = 'U' in l.flags, isProtected = 'P' in l.flags,
                            description = l.description,
                        )
                        is SdkScriptLine.Error -> failure = l.message
                        else -> Unit
                    }
                }
            }
            val exit = process.waitFor()
            if (exit != 0 && items.isEmpty()) error(failure ?: Res.string(R.string.terminal_engine_codeforge_env_list_endete_mit, exit))
            items
        }
    }

    override fun install(name: String): Flow<PackageEvent> =
        action(name, "pkg '$name'", Res.string(R.string.common_installation_wartet))

    override fun remove(name: String): Flow<PackageEvent> =
        action(name, "remove '$name'", Res.string(R.string.pkgmgr_entfernen_wartet))

    override fun refreshIndex(): Flow<PackageEvent> =
        action("update", "update", Res.string(R.string.pkgmgr_listen_wartet), validate = false)

    override fun upgradeAll(): Flow<PackageEvent> =
        action("upgrade", "upgrade", Res.string(R.string.pkgmgr_upgrade_wartet), validate = false)

    private fun action(name: String, args: String, waiting: String, validate: Boolean = true): Flow<PackageEvent> = flow {
        if (validate && !SdkScriptProtocol.isSafePackageName(name)) {
            emit(PackageEvent.Error(IllegalArgumentException(Res.string(R.string.terminal_engine_ungueltige_paket_id, name))))
            return@flow
        }
        prepare().onFailure { emit(PackageEvent.Error(it)); return@flow }
        emit(PackageEvent.Progress(0, waiting))
        lock.withLock { run(name, args) }
    }.flowOn(Dispatchers.IO).trackedAs("apt $name")

    private suspend fun FlowCollector<PackageEvent>.run(name: String, args: String) {
        var failure: String? = null
        var succeeded = false
        try {
            val process = start(args)
            process.inputStream.bufferedReader().useLines { lines ->
                for (raw in lines) {
                    when (val l = SdkScriptProtocol.parse(raw)) {
                        is SdkScriptLine.Progress -> emit(PackageEvent.Progress(l.percent, l.message))
                        is SdkScriptLine.Ok -> succeeded = true
                        is SdkScriptLine.Error -> failure = l.message
                        else -> Unit
                    }
                }
            }
            val exit = process.waitFor()
            if (exit == 0 && succeeded) emit(PackageEvent.Success(name))
            else emit(PackageEvent.Error(RuntimeException(failure ?: Res.string(R.string.terminal_engine_codeforge_env_endete_mit_code, exit))))
        } catch (e: Exception) {
            emit(PackageEvent.Error(e))
        }
    }
}
