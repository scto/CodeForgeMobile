/**
 * Modul: :libs:agent
 * @author Thomas Schmid
 *
 * Werkzeug `run_gradle`: Build und Tests als Rückmeldung für den Agenten.
 */
package com.codeforge.libs.agent

import kotlinx.serialization.json.JsonObject

/**
 * Ergebnis eines Gradle-Laufs.
 *
 * @param problems erkannte Compiler-/Build-Probleme (Pfade relativ zum Projektstamm).
 * @param outputTail letzte Zeilen der Konsolenausgabe, falls keine strukturierten Probleme vorliegen.
 * @param failureMessage Grund, wenn der Lauf gar nicht erst ausgeführt werden konnte (kein `gradlew`, Zeitüberschreitung).
 */
data class BuildOutcome(
    val success: Boolean,
    val problems: List<AgentDiagnostic> = emptyList(),
    val outputTail: List<String> = emptyList(),
    val failureMessage: String? = null,
)

/** Führt einen Gradle-Task im Projekt aus (in der App über die Gradle-Anbindung). */
fun interface AgentBuildRunner {
    /** Startet `./gradlew [task]` und wartet auf das Ende. */
    suspend fun run(task: String): BuildOutcome
}

/** Prüfung und Aufbereitung für `run_gradle`. */
object GradleTaskSupport {
    private val allowed = Regex("^[A-Za-z0-9:_.=,\\- ]+$")
    private const val MAX_TASK_CHARS = 200
    private const val MAX_PROBLEMS = 30
    private const val MAX_TAIL = 30
    private const val MAX_LINE = 300

    /** Wirft [ToolArgumentException], wenn [task] Shell-Zeichen oder Quatsch enthält; sonst die bereinigte Fassung. */
    fun validate(task: String): String {
        val t = task.trim()
        if (t.isEmpty()) throw ToolArgumentException("task darf nicht leer sein")
        if (t.length > MAX_TASK_CHARS) throw ToolArgumentException("task ist zu lang")
        if (!allowed.matches(t)) throw ToolArgumentException("task enthält unzulässige Zeichen (erlaubt: Buchstaben, Ziffern, : _ . = , -)")
        return t.replace(Regex("\\s+"), " ")
    }

    /** Formatiert [outcome] für das Modell: Erfolg/Misserfolg, Fehler zuerst, sonst Ausgabeende. */
    fun format(task: String, outcome: BuildOutcome): String = buildString {
        outcome.failureMessage?.let { append("Build nicht ausgeführt: ").append(it); return@buildString }
        append(if (outcome.success) "BUILD SUCCESSFUL: " else "BUILD FAILED: ").append(task)
        val sorted = outcome.problems.sortedBy { it.severity != DiagnosticSeverity.ERROR }
        val shown = if (outcome.success) sorted.filter { it.severity != DiagnosticSeverity.ERROR }.take(MAX_PROBLEMS / 3) else sorted.take(MAX_PROBLEMS)
        shown.forEach {
            append('\n').append(it.path).append(':').append(it.line).append(": ")
                .append(it.severity.name.lowercase()).append(": ").append(it.message.take(MAX_LINE))
        }
        if (outcome.problems.size > shown.size && !outcome.success) append("\n[").append(outcome.problems.size - shown.size).append(" weitere]")
        if (!outcome.success && outcome.problems.none { it.severity == DiagnosticSeverity.ERROR }) {
            outcome.outputTail.takeLast(MAX_TAIL).forEach { append('\n').append(it.take(MAX_LINE)) }
        }
    }
}

/** `run_gradle` – langsam; der Agent soll gezielte Tasks wählen (z. B. `:feature:x:compileDebugKotlin`). */
class RunGradleTool(private val runner: AgentBuildRunner) : AgentTool {
    override val name = "run_gradle"
    override val kind = ToolKind.EXECUTE
    override val description =
        "Runs a Gradle task in the project (e.g. ':app:compileDebugKotlin', ':libs:x:test'). Slow on a phone: prefer targeted " +
            "compile/test tasks over full builds. Returns success or the compiler errors."
    override val parameters = objectSchema(
        listOf("task"),
        "task" to stringProp("Gradle task with optional arguments, e.g. ':app:compileDebugKotlin' or ':core:domain:test --tests *Foo*'"),
    )

    override suspend fun execute(arguments: JsonObject): ToolResult {
        val task = GradleTaskSupport.validate(arguments.requireString("task"))
        val outcome = runner.run(task)
        val text = GradleTaskSupport.format(task, outcome)
        return ToolResult(text, isError = outcome.failureMessage != null || !outcome.success)
    }

    override fun describeCall(arguments: JsonObject) = "Gradle: ${arguments.optString("task")}"
}
