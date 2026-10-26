package com.codeforge.feature.x2c.build

import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.domain.model.X2cBuildProblem
import com.codeforge.core.domain.model.X2cBuildStepResult
import com.codeforge.core.domain.model.X2cBuildStepStatus
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.domain.repository.X2cBuildChecker
import com.codeforge.libs.x2c.verify.SecretScrubber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Task 52.11: verbindet die Build-Prüfung der Migration mit der Gradle-Infrastruktur aus Task 28
 * ([GradleBuildRepository]: Umgebung, Foreground-Service, Ausgabe-Parser), statt `gradlew` selbst zu starten.
 *
 * Alle Aufgaben laufen in einem Gradle-Aufruf (das Repository trennt den Namen an Leerzeichen).
 */
class GradleRepositoryX2cBuildChecker @Inject constructor(
    private val gradle: GradleBuildRepository
) : X2cBuildChecker {

    override suspend fun runTasks(
        projectPath: String,
        tasks: List<String>,
        onOutput: (String) -> Unit,
        isCancelled: () -> Boolean
    ): X2cBuildStepResult {
        if (tasks.isEmpty()) {
            return X2cBuildStepResult(tasks, X2cBuildStepStatus.SKIPPED, message = "Keine Aufgaben angegeben")
        }
        gradle.connect(projectPath).onFailure { e ->
            return X2cBuildStepResult(
                tasks, X2cBuildStepStatus.FAILED,
                message = SecretScrubber.scrub("Gradle nicht verfügbar: ${e.message}")
            )
        }

        val problems = mutableListOf<X2cBuildProblem>()
        val tail = ArrayDeque<String>()
        var tailChars = 0
        var success: Boolean? = null
        var failure: String? = null

        try {
            coroutineScope {
                // Abbruch durch den Aufrufer abfragen; das Repository beendet dann den Prozess und schließt den Fluss.
                val watcher = launch {
                    while (isActive) {
                        if (isCancelled()) {
                            gradle.cancelBuild()
                            return@launch
                        }
                        delay(CANCEL_POLL_MILLIS)
                    }
                }
                try {
                    gradle.runTask(tasks.joinToString(" ")).collect { event ->
                        when (event) {
                            is BuildEvent.Output -> {
                                val line = SecretScrubber.scrub(event.line)
                                tail.addLast(line)
                                tailChars += line.length + 1
                                while (tailChars > MAX_TAIL_CHARS && tail.size > 1) tailChars -= tail.removeFirst().length + 1
                                runCatching { onOutput(line) }
                            }
                            is BuildEvent.Problem -> {
                                val p = event.problem
                                if (p.severity != ProblemSeverity.INFO) {
                                    problems += X2cBuildProblem(
                                        severity = if (p.severity == ProblemSeverity.ERROR) "error" else "warning",
                                        path = p.path,
                                        line = p.line,
                                        column = p.column,
                                        message = SecretScrubber.scrub(p.message),
                                        task = p.taskName
                                    )
                                }
                            }
                            is BuildEvent.BuildFinished -> success = event.success
                            is BuildEvent.BuildFailed -> failure = SecretScrubber.scrub(event.message)
                            is BuildEvent.Progress, is BuildEvent.TaskStarted, is BuildEvent.TaskFinished -> Unit
                        }
                    }
                } finally {
                    watcher.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = SecretScrubber.scrub(e.message.orEmpty())
        }

        val status = when {
            isCancelled() -> X2cBuildStepStatus.FAILED
            failure != null -> X2cBuildStepStatus.FAILED
            success == true -> X2cBuildStepStatus.SUCCESS
            else -> X2cBuildStepStatus.FAILED
        }
        return X2cBuildStepResult(
            tasks = tasks,
            status = status,
            problems = problems,
            outputTail = tail.joinToString("\n"),
            message = if (isCancelled()) "Abgebrochen" else failure
        )
    }

    private companion object {
        const val CANCEL_POLL_MILLIS = 500L
        const val MAX_TAIL_CHARS = 16_000
    }
}
