package com.codeforge.core.data.repository

import com.codeforge.core.domain.extension.ContributionRepository
import com.codeforge.core.domain.extension.PluginToolRunner
import com.codeforge.core.domain.extension.RequirementContribution
import com.codeforge.core.domain.extension.RequirementManager
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
class PluginToolRunnerImpl @Inject constructor(
    private val contributionRepository: ContributionRepository
) : PluginToolRunner {

    /** Dispatcher für Ein-/Ausgabe; in Tests ersetzbar. */
    internal var ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override suspend fun missingRequirements(pluginId: String): List<RequirementContribution> =
        withContext(ioDispatcher) {
            contributionRepository.contributions.value.requires
                .filter { it.pluginId == pluginId }
                .map { it.item }
                .filter { !isAvailable(it.check) }
        }

    override fun install(requirements: List<RequirementContribution>): Flow<ToolRunEvent> = channelFlow {
        for (r in requirements) {
            send(ToolRunEvent.Line("> ${r.manager.id} ${r.name}"))
            val res = stream(installCommand(r), File(TERMUX_HOME), 900, extraEnv = mapOf("DEBIAN_FRONTEND" to "noninteractive"))
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

    private fun installCommand(r: RequirementContribution): List<String> = when (r.manager) {
        // Wie im Terminal: `codeforge-env pkg` aktualisiert die Paketlisten, kennt die „großen Pakete“ des
        // CodeForge-Repos und nutzt dessen apt-Einstellungen. Ein nackter `apt-get install` kennt das alles nicht.
        RequirementManager.PKG ->
            if (resolve(ENV_SCRIPT) != null) listOf(ENV_SCRIPT, "pkg", r.name)
            else listOf("apt-get", "install", "-y", r.name)
        RequirementManager.NPM -> listOf("npm", "install", "-g", r.name)
        RequirementManager.PIP -> listOf("python3", "-m", "pip", "install", r.name)
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
