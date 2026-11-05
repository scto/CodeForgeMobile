/**
 * Modul: :feature:agent
 * @author Thomas Schmid
 */
package com.codeforge.feature.agent

import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.libs.agent.AgentBuildRunner
import com.codeforge.libs.agent.AgentDiagnostic
import com.codeforge.libs.agent.BuildOutcome
import com.codeforge.libs.agent.DiagnosticSeverity
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Führt Gradle-Tasks des Agenten über die vorhandene Build-Anbindung aus, sammelt Probleme und das Ausgabeende.
 *
 * @param timeoutMillis Obergrenze je Lauf; danach wird der Build abgebrochen.
 */
class GradleAgentBuildRunner(
    private val gradle: GradleBuildRepository,
    private val root: File,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
) : AgentBuildRunner {

    override suspend fun run(task: String): BuildOutcome {
        gradle.connect(root.path).onFailure { return BuildOutcome(false, failureMessage = it.message ?: it.toString()) }

        val problems = ArrayList<AgentDiagnostic>()
        val tail = ArrayDeque<String>()
        var success: Boolean? = null
        var failure: String? = null
        try {
            withTimeout(timeoutMillis) {
                gradle.runTask(task).collect { event ->
                    when (event) {
                        is BuildEvent.Output -> {
                            tail.addLast(event.line)
                            if (tail.size > TAIL_LINES) tail.removeFirst()
                        }
                        is BuildEvent.Problem -> event.problem.takeIf { it.severity != ProblemSeverity.INFO }?.let {
                            problems += AgentDiagnostic(
                                path = it.path?.let(::relative) ?: "(Gradle)",
                                line = it.line ?: 0,
                                severity = if (it.severity == ProblemSeverity.ERROR) DiagnosticSeverity.ERROR else DiagnosticSeverity.WARNING,
                                message = it.message,
                            )
                        }
                        is BuildEvent.BuildFinished -> success = event.success
                        is BuildEvent.BuildFailed -> failure = event.message
                        else -> Unit
                    }
                }
            }
        } catch (e: TimeoutCancellationException) {
            withContext(NonCancellable) { gradle.cancelBuild() }
            return BuildOutcome(false, problems, tail.toList(), failureMessage = "Zeitüberschreitung nach ${timeoutMillis / 1000} s – Build abgebrochen")
        } catch (e: CancellationException) {
            // Nutzer bricht den Agenten ab: auch den Gradle-Prozess beenden.
            withContext(NonCancellable) { gradle.cancelBuild() }
            throw e
        }
        return BuildOutcome(success == true && failure == null, problems, tail.toList(), failure)
    }

    private fun relative(path: String): String {
        val base = root.path.trimEnd('/', File.separatorChar) + File.separator
        return if (path.startsWith(base)) path.removePrefix(base).replace(File.separatorChar, '/') else path
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 20 * 60 * 1000L
        const val TAIL_LINES = 60
    }
}
