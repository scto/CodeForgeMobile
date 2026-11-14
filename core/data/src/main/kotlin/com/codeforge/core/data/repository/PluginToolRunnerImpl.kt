package com.codeforge.core.data.repository

import com.codeforge.core.common.io.ArchiveUtils
import com.codeforge.core.common.io.FileDownloader
import com.codeforge.core.common.io.HttpFileDownloader
import com.codeforge.core.common.io.Sha256Utils
import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.DownloadArchive
import com.codeforge.core.domain.extension.PluginToolRunner
import com.codeforge.core.domain.extension.RequirementContribution
import com.codeforge.core.domain.extension.RequirementManager
import com.codeforge.core.domain.extension.RequirementPolicy
import com.codeforge.core.domain.extension.ToolRunEvent
import com.codeforge.core.domain.extension.expandCommand
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Startet Plugin-Werkzeuge und Installationen ohne Shell (Argumentliste) mit dem Termux-Environment.
 * Ausgabe (stdout+stderr) wird zeilenweise gestreamt; Abbruch des Flows beendet den Prozess.
 */
@Singleton
class PluginToolRunnerImpl internal constructor(
    private val contributionRepository: ContributionRepository,
    private val downloader: FileDownloader,
    /** Ordner der Download-Voraussetzungen (`${'$'}{deps}`). */
    private val depsDir: File
) : PluginToolRunner {

    /** Dispatcher für Ein-/Ausgabe; in Tests ersetzbar. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    @Inject
    constructor(contributionRepository: ContributionRepository, downloader: HttpFileDownloader) :
        this(contributionRepository, downloader, File(TERMUX_PREFIX, RequirementPolicy.DEPS_SUBDIR))

    override suspend fun missingRequirements(pluginId: String): List<RequirementContribution> =
        withContext(ioDispatcher) {
            contributionRepository.contributions.value.requires
                .filter { it.pluginId == pluginId }
                .map { it.item }
                .filter { !isPresent(it) }
        }

    /** Ob die Voraussetzung erfüllt ist; Downloads zählen nur in der deklarierten Version (SHA-256). */
    internal fun isPresent(r: RequirementContribution): Boolean {
        if (r.manager != RequirementManager.DOWNLOAD) return isAvailable(r.check)
        val dir = File(depsDir, r.name)
        return File(dir, r.check).exists() &&
            runCatching { File(dir, RequirementPolicy.SHA_MARKER).readText().trim() }.getOrNull() == r.sha256
    }

    override suspend fun releaseDownloads(pluginId: String) = withContext(ioDispatcher) {
        val downloads = contributionRepository.contributions.value.requires.filter { it.item.manager == RequirementManager.DOWNLOAD }
        val stillUsed = downloads.filter { it.pluginId != pluginId }.map { it.item.name }.toSet()
        downloads.filter { it.pluginId == pluginId && it.item.name !in stillUsed }
            .forEach { File(depsDir, it.item.name).deleteRecursively() }
    }

    override fun install(requirements: List<RequirementContribution>): Flow<ToolRunEvent> = channelFlow {
        for (r in requirements) {
            send(ToolRunEvent.Line("> ${r.manager.id} ${r.name}"))
            val res = if (r.manager == RequirementManager.DOWNLOAD) {
                StreamResult(downloadAndExtract(r))
            } else {
                stream(installCommand(r), File(TERMUX_HOME), 900, extraEnv = mapOf("DEBIAN_FRONTEND" to "noninteractive"))
            }
            if (res.exitCode != 0) {
                send(ToolRunEvent.Finished(res.exitCode, res.timedOut))
                return@channelFlow
            }
        }
        send(ToolRunEvent.Finished(0))
    }.flowOn(ioDispatcher)

    override fun run(pluginId: String, toolId: String, projectDir: String, input: String): Flow<ToolRunEvent> = channelFlow {
        val active = contributionRepository.contributions.value
        val hit = active.tools.firstOrNull { it.pluginId == pluginId && it.item.id == toolId }
            ?: active.agentTools.firstOrNull { it.pluginId == pluginId && it.item.id == toolId }
                ?.let { com.codeforge.core.domain.extension.Resolved(it.pluginId, it.pluginDir, it.item.asTool()) }
        if (hit == null) {
            send(ToolRunEvent.Line("Werkzeug nicht gefunden: $pluginId/$toolId"))
            send(ToolRunEvent.Finished(-1))
            return@channelFlow
        }
        val dir = File(projectDir)
        if (!dir.isDirectory) {
            send(ToolRunEvent.Line("Projektverzeichnis nicht gefunden: $projectDir"))
            send(ToolRunEvent.Finished(-1))
            return@channelFlow
        }
        val command = hit.item.expandCommand(projectDir, hit.pluginDir, TERMUX_HOME, input)
        send(ToolRunEvent.Line("$ " + command.joinToString(" ")))
        val res = stream(command, dir, hit.item.timeoutSeconds.toLong())
        send(ToolRunEvent.Finished(res.exitCode, res.timedOut))
    }.flowOn(ioDispatcher)

    private data class StreamResult(val exitCode: Int, val timedOut: Boolean = false)

    /** Läuft [command] bis zum Ende/Timeout und sendet Zeilen; liefert den Exit-Code (-1 bei Timeout/Startfehler). */
    private suspend fun kotlinx.coroutines.channels.ProducerScope<ToolRunEvent>.stream(
        command: List<String>,
        workDir: File,
        timeoutSeconds: Long,
        extraEnv: Map<String, String> = emptyMap()
    ): StreamResult {
        var timedOut = false
        val program = command.first()
        val executable = resolve(program)
        if (executable == null) {
            send(ToolRunEvent.Line("Programm nicht gefunden: $program"))
            return StreamResult(127)
        }
        val process = try {
            ProcessBuilder(listOf(executable) + command.drop(1))
                .directory(workDir)
                .redirectErrorStream(true)
                // stdin bleibt ein gültiger Deskriptor (/dev/null): ein geschlossener fd 0 verschiebt
                // die Nummern der von apt geöffneten Deskriptoren (fdsan-Abbruch, Exit 134).
                .redirectInput(File("/dev/null"))
                .also { it.environment().putAll(termuxEnvironment() + extraEnv) }
                .start()
        } catch (e: Exception) {
            send(ToolRunEvent.Line("Start fehlgeschlagen: ${e.message}"))
            return StreamResult(-1)
        }
        val reader = launch {
            val br = process.inputStream.bufferedReader()
            while (true) {
                val line = br.readLine() ?: break
                send(ToolRunEvent.Line(line))
            }
        }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
            while (isActive && process.isAlive && System.nanoTime() < deadline) {
                process.waitFor(200, TimeUnit.MILLISECONDS)
            }
            if (process.isAlive) {
                timedOut = isActive
                process.destroyForcibly()
                process.waitFor(2, TimeUnit.SECONDS)
            }
            reader.join()
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
        return if (timedOut) StreamResult(-1, true) else StreamResult(runCatching { process.exitValue() }.getOrDefault(-1))
    }

    // Lädt, prüft die SHA-256 und entpackt nach ${deps}/<name> – erst in einen Nachbarordner, damit eine funktionierende
    // ältere Version bei Fehlern erhalten bleibt. Liefert den Exit-Code (0 = ok).
    private suspend fun kotlinx.coroutines.channels.ProducerScope<ToolRunEvent>.downloadAndExtract(r: RequirementContribution): Int {
        val url = r.url ?: return 2
        val sha = r.sha256 ?: return 2
        depsDir.mkdirs()
        val archive = File(depsDir, ".${r.name}.download")
        val staging = File(depsDir, ".${r.name}.new")
        val target = File(depsDir, r.name)
        var lastPercent = -1
        send(ToolRunEvent.Line(url))
        val result = runCatching {
            downloader.download(url, archive) { done, total ->
                val percent = if (total > 0) (done * 100 / total).toInt() else -1
                if (percent >= 0 && percent / 10 != lastPercent / 10) {
                    lastPercent = percent
                    trySend(ToolRunEvent.Line("$percent %"))
                }
            }.getOrThrow()
            check(Sha256Utils.verifySha256(archive, sha)) { "SHA-256 stimmt nicht: ${r.name}" }
            staging.deleteRecursively()
            staging.mkdirs()
            when (r.archive ?: DownloadArchive.FILE) {
                DownloadArchive.ZIP -> ArchiveUtils.extractZip(archive, staging).getOrThrow()
                DownloadArchive.TAR_GZ -> ArchiveUtils.extractTarGz(archive, staging).getOrThrow()
                DownloadArchive.FILE -> archive.copyTo(File(staging, r.check), overwrite = true)
            }
            val main = File(staging, r.check)
            check(main.exists()) { "Nach dem Entpacken fehlt ${r.check}" }
            main.setExecutable(true, false)
            File(staging, RequirementPolicy.SHA_MARKER).writeText(sha)
            target.deleteRecursively()
            check(staging.renameTo(target)) { "Ordner ${target.name} konnte nicht ersetzt werden" }
        }
        archive.delete()
        staging.deleteRecursively()
        return result.fold(
            onSuccess = { send(ToolRunEvent.Line("✓ ${r.name}")); 0 },
            onFailure = { send(ToolRunEvent.Line("Download fehlgeschlagen: ${it.message}")); 1 }
        )
    }

    private fun installCommand(r: RequirementContribution): List<String> = when (r.manager) {
        // Wie im Terminal: `codeforge-env pkg` aktualisiert die Paketlisten, kennt die „großen Pakete“ des
        // CodeForge-Repos und nutzt dessen apt-Einstellungen. Ein nackter `apt-get install` kennt das alles nicht.
        RequirementManager.PKG ->
            if (resolve(ENV_SCRIPT) != null) listOf(ENV_SCRIPT, "pkg", r.name)
            else listOf("apt-get", "install", "-y", r.name)
        RequirementManager.NPM -> listOf("npm", "install", "-g", r.name)
        RequirementManager.PIP -> listOf("python3", "-m", "pip", "install", r.name)
        RequirementManager.DOWNLOAD -> error("Downloads laufen nicht über einen Paketmanager")
    }

    private fun resolve(program: String): String? =
        if (program.startsWith("/")) program.takeIf { File(it).canExecute() }
        else listOf("$TERMUX_PREFIX/bin", *System.getenv("PATH").orEmpty().split(':').toTypedArray())
            .filter { it.isNotEmpty() }
            .map { File(it, program) }
            .firstOrNull { it.isFile && it.canExecute() }?.absolutePath

    /** Ob das Programm im Termux-`bin` oder im `PATH` ausführbar ist. */
    internal fun isAvailable(program: String): Boolean = resolve(program) != null

    private fun termuxEnvironment(): Map<String, String> = mapOf(
        "PREFIX" to TERMUX_PREFIX,
        "HOME" to TERMUX_HOME,
        "PATH" to listOf("$TERMUX_PREFIX/bin", System.getenv("PATH").orEmpty()).filter { it.isNotEmpty() }.joinToString(":"),
        "LD_LIBRARY_PATH" to "$TERMUX_PREFIX/lib",
        "TMPDIR" to "$TERMUX_PREFIX/tmp",
        "LANG" to "en_US.UTF-8"
    )

    private companion object {
        const val ENV_SCRIPT = "codeforge-env"
        const val TERMUX_PREFIX = "/data/data/com.codeforge.app/files/usr"
        const val TERMUX_HOME = "/data/data/com.codeforge.app/files/home"
    }
}
