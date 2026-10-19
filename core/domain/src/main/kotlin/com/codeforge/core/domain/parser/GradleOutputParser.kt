/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.domain.model.ProblemSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GradleOutputParser @Inject constructor() {

    companion object {
        private val ANSI_REGEX = Regex("\u001B\\[[;?0-9]*[a-zA-Z]|\u001B\\].*?\\u0007")

        private val TASK_REGEX = Regex("""^> Task\s+(:[a-zA-Z0-9_:-]+)""")

        private val KOTLIN_DIAGNOSTIC_REGEX = Regex(
            """^(e|w|i):\s+(?:file:\/\/)?([^:\r\n]+):(?:\s*\(([0-9]+)(?:,\s*([0-9]+))?\)|([0-9]+)(?::([0-9]+))?):?\s*(.*)$"""
        )

        private val JAVA_DIAGNOSTIC_REGEX = Regex(
            """^([a-zA-Z]:[\\/][^:\r\n]+\.java|/[^:\r\n]+\.java):([0-9]+):\s*(error|warning|note):\s*(.*)$"""
        )

        private val AAPT_DIAGNOSTIC_REGEX = Regex(
            """^(?:ERROR:\s+)?(?:AAPT:\s+)?([a-zA-Z]:[\\/][^:\r\n]+|/[^:\r\n]+):([0-9]+)(?::([0-9]+))?:\s*(?:AAPT:\s*)?(error|warning)?:\s*(.*)$"""
        )

        private val GRADLE_EXEC_FAILED_REGEX = Regex(
            """^Execution failed for task '([^']+)'.*"""
        )

        fun stripAnsi(text: String): String = ANSI_REGEX.replace(text, "")
    }

    fun parse(log: String): List<BuildProblem> {
        val session = Session()
        val allProblems = mutableListOf<BuildProblem>()
        log.lineSequence().forEach { line ->
            allProblems.addAll(session.feedLine(line))
        }
        allProblems.addAll(session.finish())
        return allProblems
    }

    fun createSession(): Session = Session()

    class Session {
        private var currentTask: String? = null
        private var pendingJavaProblem: MutableJavaProblem? = null
        private var inWhatWentWrong = false
        private val whatWentWrongLines = mutableListOf<String>()
        private var whatWentWrongTask: String? = null

        fun feedLine(rawLine: String): List<BuildProblem> {
            val line = stripAnsi(rawLine)
            val trimmed = line.trim()
            val emitted = mutableListOf<BuildProblem>()

            // 1. Task tracking
            TASK_REGEX.find(trimmed)?.let { match ->
                currentTask = match.groupValues[1]
            }

            // 2. Check for Java continuation lines
            if (pendingJavaProblem != null) {
                if (line.startsWith(" ") || line.startsWith("\t") || trimmed.startsWith("^")) {
                    pendingJavaProblem?.lines?.add(line.trimEnd())
                    return emitted
                } else {
                    // Java problem finished by next non-indented line
                    pendingJavaProblem?.toBuildProblem()?.let { emitted.add(it) }
                    pendingJavaProblem = null
                }
            }

            // 3. Gradle "What went wrong:" section tracking
            if (inWhatWentWrong) {
                if (trimmed.startsWith("* Try:") || trimmed.startsWith("* Get more help at") || trimmed.startsWith("BUILD FAILED")) {
                    flushWhatWentWrong()?.let { emitted.add(it) }
                } else if (trimmed.isNotEmpty()) {
                    whatWentWrongLines.add(line)
                    GRADLE_EXEC_FAILED_REGEX.find(trimmed)?.let { match ->
                        whatWentWrongTask = match.groupValues[1]
                    }
                    return emitted
                } else {
                    // Empty line inside what went wrong
                    return emitted
                }
            }

            if (trimmed.startsWith("* What went wrong:")) {
                inWhatWentWrong = true
                whatWentWrongLines.clear()
                whatWentWrongTask = currentTask
                return emitted
            }

            // 4. Kotlin diagnostics
            KOTLIN_DIAGNOSTIC_REGEX.find(trimmed)?.let { match ->
                val severity = when (match.groupValues[1].lowercase()) {
                    "e" -> ProblemSeverity.ERROR
                    "w" -> ProblemSeverity.WARNING
                    else -> ProblemSeverity.INFO
                }
                val path = match.groupValues[2].trim()
                val lineNum = match.groupValues[3].toIntOrNull() ?: match.groupValues[5].toIntOrNull()
                val colNum = match.groupValues[4].toIntOrNull() ?: match.groupValues[6].toIntOrNull()
                val message = match.groupValues[7].trim()

                emitted.add(
                    BuildProblem(
                        severity = severity,
                        source = ProblemSource.KOTLIN,
                        path = path,
                        line = lineNum,
                        column = colNum,
                        message = message,
                        taskName = currentTask
                    )
                )
                return emitted
            }

            // 5. Java diagnostics
            JAVA_DIAGNOSTIC_REGEX.find(trimmed)?.let { match ->
                val path = match.groupValues[1].trim()
                val lineNum = match.groupValues[2].toIntOrNull()
                val severity = when (match.groupValues[3].lowercase()) {
                    "error" -> ProblemSeverity.ERROR
                    "warning" -> ProblemSeverity.WARNING
                    else -> ProblemSeverity.INFO
                }
                val initialMsg = match.groupValues[4].trim()

                pendingJavaProblem = MutableJavaProblem(
                    severity = severity,
                    source = ProblemSource.JAVA,
                    path = path,
                    line = lineNum,
                    column = null,
                    lines = mutableListOf(initialMsg),
                    taskName = currentTask
                )
                return emitted
            }

            // 6. AAPT diagnostics
            if (trimmed.contains("AAPT") || trimmed.contains("/res/") || trimmed.endsWith(".xml:") || trimmed.contains(".xml:")) {
                AAPT_DIAGNOSTIC_REGEX.find(trimmed)?.let { match ->
                    val path = match.groupValues[1].trim()
                    val lineNum = match.groupValues[2].toIntOrNull()
                    val colNum = match.groupValues[3].toIntOrNull()
                    val severity = if (match.groupValues[4].equals("warning", ignoreCase = true)) {
                        ProblemSeverity.WARNING
                    } else {
                        ProblemSeverity.ERROR
                    }
                    val msg = match.groupValues[5].trim()

                    emitted.add(
                        BuildProblem(
                            severity = severity,
                            source = ProblemSource.AAPT,
                            path = path,
                            line = lineNum,
                            column = colNum,
                            message = msg,
                            taskName = currentTask
                        )
                    )
                    return emitted
                }
            }

            // 7. Standalone Gradle failures outside "What went wrong"
            GRADLE_EXEC_FAILED_REGEX.find(trimmed)?.let { match ->
                val task = match.groupValues[1]
                emitted.add(
                    BuildProblem(
                        severity = ProblemSeverity.ERROR,
                        source = ProblemSource.GRADLE,
                        path = null,
                        line = null,
                        column = null,
                        message = trimmed,
                        taskName = task
                    )
                )
                return emitted
            }

            if (trimmed.startsWith("Could not resolve ")) {
                emitted.add(
                    BuildProblem(
                        severity = ProblemSeverity.ERROR,
                        source = ProblemSource.GRADLE,
                        path = null,
                        line = null,
                        column = null,
                        message = trimmed,
                        taskName = currentTask
                    )
                )
                return emitted
            }

            // 8. Other general errors
            if (trimmed.startsWith("[ERROR]") || trimmed.startsWith("Error:") || trimmed.startsWith("Exception in thread ")) {
                emitted.add(
                    BuildProblem(
                        severity = ProblemSeverity.ERROR,
                        source = ProblemSource.OTHER,
                        path = null,
                        line = null,
                        column = null,
                        message = trimmed,
                        taskName = currentTask
                    )
                )
                return emitted
            }

            return emitted
        }

        fun finish(): List<BuildProblem> {
            val remaining = mutableListOf<BuildProblem>()
            pendingJavaProblem?.toBuildProblem()?.let { remaining.add(it) }
            pendingJavaProblem = null
            flushWhatWentWrong()?.let { remaining.add(it) }
            return remaining
        }

        private fun flushWhatWentWrong(): BuildProblem? {
            if (!inWhatWentWrong || whatWentWrongLines.isEmpty()) {
                inWhatWentWrong = false
                whatWentWrongLines.clear()
                return null
            }
            val msg = whatWentWrongLines.joinToString("\n").trim()
            val problem = BuildProblem(
                severity = ProblemSeverity.ERROR,
                source = ProblemSource.GRADLE,
                path = null,
                line = null,
                column = null,
                message = msg,
                taskName = whatWentWrongTask ?: currentTask
            )
            inWhatWentWrong = false
            whatWentWrongLines.clear()
            return problem
        }
    }

    private data class MutableJavaProblem(
        val severity: ProblemSeverity,
        val source: ProblemSource,
        val path: String,
        val line: Int?,
        val column: Int?,
        val lines: MutableList<String>,
        val taskName: String?
    ) {
        fun toBuildProblem(): BuildProblem = BuildProblem(
            severity = severity,
            source = source,
            path = path,
            line = line,
            column = column,
            message = lines.joinToString("\n"),
            taskName = taskName
        )
    }
}
