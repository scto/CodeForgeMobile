package com.codeforge.libs.x2c.verify

import com.codeforge.core.domain.model.X2cBuildProblem
import java.io.File

/** Liest Kotlin-, Java- und AAPT-Meldungen aus Gradle-Ausgaben; Pfade werden relativ zum Projekt. */
internal object GradleLogParser {
    private val ANSI = Regex("\u001B\\[[;?0-9]*[a-zA-Z]")
    private val TASK = Regex("""^> Task\s+(:?[\w:.-]+)""")
    private val KOTLIN = Regex("""^(e|w):\s+(?:file://)?([^\s:][^:\r\n]*?):(?:\s*\((\d+)(?:,\s*(\d+))?\)|(\d+)(?::(\d+))?):?\s*(.*)$""")
    private val JAVA = Regex("""^((?:[A-Za-z]:)?[\\/][^:\r\n]+\.java):(\d+):\s*(error|warning):\s*(.*)$""")
    private val AAPT = Regex("""^(?:ERROR:\s+)?((?:[A-Za-z]:)?[\\/][^:\r\n]+):(\d+)(?::(\d+))?:\s*(?:AAPT:\s*)?(error|warning)?:?\s*(.*)$""")
    private val WENT_WRONG = Regex("""^\* What went wrong:""")

    fun parse(log: String, projectDir: File): List<X2cBuildProblem> {
        val out = mutableListOf<X2cBuildProblem>()
        var task: String? = null
        var wrong = false
        for (raw in log.lineSequence()) {
            val line = ANSI.replace(raw, "").trimEnd()
            val t = line.trim()
            TASK.find(t)?.let { task = it.groupValues[1] }
            if (WENT_WRONG.containsMatchIn(t)) { wrong = true; continue }
            if (wrong) {
                if (t.isNotEmpty()) {
                    if (!t.startsWith("*") && !t.startsWith("> Task")) out += X2cBuildProblem("error", null, null, null, t.removePrefix("> "), task)
                    wrong = false
                }
                continue
            }
            KOTLIN.find(t)?.let { m ->
                out += X2cBuildProblem(
                    if (m.groupValues[1] == "e") "error" else "warning", rel(m.groupValues[2], projectDir),
                    (m.groupValues[3].ifEmpty { m.groupValues[5] }).toIntOrNull(), (m.groupValues[4].ifEmpty { m.groupValues[6] }).toIntOrNull(),
                    m.groupValues[7], task
                )
                return@let
            } ?: JAVA.find(t)?.let { m ->
                out += X2cBuildProblem(m.groupValues[3], rel(m.groupValues[1], projectDir), m.groupValues[2].toIntOrNull(), null, m.groupValues[4], task)
            } ?: AAPT.find(t)?.let { m ->
                out += X2cBuildProblem(m.groupValues[4].ifEmpty { "error" }, rel(m.groupValues[1], projectDir), m.groupValues[2].toIntOrNull(), m.groupValues[3].toIntOrNull(), m.groupValues[5], task)
            }
        }
        return out.distinct()
    }

    private fun rel(path: String, projectDir: File): String {
        val f = File(path)
        val base = runCatching { projectDir.canonicalFile }.getOrDefault(projectDir)
        val abs = runCatching { f.canonicalFile }.getOrDefault(f)
        return if (abs.path.startsWith(base.path + File.separator)) abs.relativeTo(base).path.replace('\\', '/') else path
    }
}
