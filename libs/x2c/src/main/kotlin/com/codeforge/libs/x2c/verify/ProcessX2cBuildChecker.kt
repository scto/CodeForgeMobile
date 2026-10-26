package com.codeforge.libs.x2c.verify

import com.codeforge.core.domain.model.X2cBuildStepResult
import com.codeforge.core.domain.model.X2cBuildStepStatus
import com.codeforge.core.domain.repository.X2cBuildChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Startet `./gradlew` (sonst `gradle` aus dem PATH) mit `--console=plain`. Zeitlimit, Abbruch, Ausgabe auf [maxTailChars]
 * gekürzt und von Geheimnissen bereinigt. Führt Projekt-Skripte aus, daher nur mit Zustimmung aufrufen.
 */
class ProcessX2cBuildChecker(
    private val timeoutMinutes: Long = 30,
    private val maxTailChars: Int = 16_000,
    private val extraArgs: List<String> = emptyList()
) : X2cBuildChecker {

    override suspend fun runTasks(
        projectPath: String,
        tasks: List<String>,
        onOutput: (String) -> Unit,
        isCancelled: () -> Boolean
    ): X2cBuildStepResult = withContext(Dispatchers.IO) {
        val dir = File(projectPath).canonicalFile
        val wrapper = File(dir, if (isWindows()) "gradlew.bat" else "gradlew")
        val cmd = mutableListOf<String>()
        if (!wrapper.isFile) {
            return@withContext X2cBuildStepResult(tasks, X2cBuildStepStatus.SKIPPED, message = "Kein gradlew im Projekt gefunden; Build-Prüfung übersprungen")
        }
        if (isWindows()) cmd += wrapper.path else { cmd += "sh"; cmd += wrapper.path }
        cmd += listOf("--console=plain") + extraArgs + tasks
        val pb = ProcessBuilder(cmd).directory(dir).redirectErrorStream(true)
        pb.environment()["TERM"] = "dumb"
        val sb = StringBuilder()
        val process = try { pb.start() } catch (t: Throwable) {
            return@withContext X2cBuildStepResult(tasks, X2cBuildStepStatus.FAILED, message = "Gradle konnte nicht gestartet werden: ${t.message}")
        }
        val reader = Thread {
            process.inputStream.bufferedReader().useLines { lines -> lines.forEach { l ->
                val clean = SecretScrubber.scrub(l)
                synchronized(sb) { sb.appendLine(clean) }
                runCatching { onOutput(clean) }
            } }
        }.apply { isDaemon = true; start() }

        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(timeoutMinutes)
        var reason: String? = null
        try {
            while (process.isAlive) {
                if (!coroutineContext.isActive || isCancelled()) { reason = "Abgebrochen"; break }
                if (System.nanoTime() > deadline) { reason = "Zeitüberschreitung nach $timeoutMinutes Minuten"; break }
                delay(100)
            }
        } finally {
            if (process.isAlive) { process.descendants().forEach { it.destroyForcibly() }; process.destroyForcibly() }
            reader.join(2000)
        }
        val text = synchronized(sb) { sb.toString() }
        val tail = if (text.length > maxTailChars) "…\n" + text.takeLast(maxTailChars) else text
        val problems = GradleLogParser.parse(text, dir)
        when {
            reason != null -> X2cBuildStepResult(tasks, X2cBuildStepStatus.FAILED, problems, tail, reason)
            process.exitValue() == 0 -> X2cBuildStepResult(tasks, X2cBuildStepStatus.SUCCESS, problems.filter { it.severity != "error" }, tail)
            else -> X2cBuildStepResult(tasks, X2cBuildStepStatus.FAILED, problems, tail, "Gradle endete mit Code ${process.exitValue()}")
        }
    }

    private fun isWindows() = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
}
