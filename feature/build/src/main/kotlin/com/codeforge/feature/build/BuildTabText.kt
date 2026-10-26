/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.TestCaseResult

/** Größte Textmenge für Zwischenablage und Teilen (Binder-Grenze rund 1 MB); Speichern schreibt immer alles. */
const val MAX_TRANSFER_CHARS = 400_000

/** Textfassung des Inhalts eines Reiters, wie er gerade gefiltert angezeigt wird. Leer = nichts vorhanden. */
fun BuildUiState.textFor(tab: BuildTab): String = when (tab) {
    BuildTab.OUTPUT -> filteredLines.joinToString("\n")
    BuildTab.PROBLEMS -> filteredProblems.joinToString("\n") { it.asLine() }
    BuildTab.LOGCAT -> filteredLogcatEntries.joinToString("\n") { it.fullFormattedText }
    BuildTab.TESTS -> filteredModuleSuites
        .flatMap { module -> module.classes.flatMap { it.cases } }
        .joinToString("\n\n") { it.asBlock() }
}

fun BuildProblem.asLine(): String {
    val where = if (path != null) {
        buildString {
            append(path)
            if (line != null) append(':').append(line)
            if (line != null && column != null) append(':').append(column)
        } + ": "
    } else {
        ""
    }
    return "${severity.name} $where$message" + (taskName?.let { " [$it]" } ?: "")
}

fun TestCaseResult.asBlock(): String = buildString {
    append(status.name).append(' ').append(className).append('.').append(name)
    append(" (").append(durationSeconds).append(" s)")
    failureDetail?.let { detail ->
        detail.message?.let { append('\n').append(it) }
        if (detail.stackTrace.isNotBlank()) append('\n').append(detail.stackTrace)
    }
}

/** Dateiname für „Speichern“, z. B. `build-output-20261026-145300.txt`. */
fun suggestedFileName(tab: BuildTab, timestamp: String): String {
    val base = when (tab) {
        BuildTab.OUTPUT -> "build-output"
        BuildTab.PROBLEMS -> "build-problems"
        BuildTab.LOGCAT -> "logcat"
        BuildTab.TESTS -> "test-results"
    }
    return "$base-$timestamp.txt"
}

/** Kürzt auf [max] Zeichen und behält das **Ende** (bei Logs steht dort das Ergebnis). */
fun String.keepTail(max: Int = MAX_TRANSFER_CHARS): TrimResult {
    if (length <= max) return TrimResult(this, false)
    val tail = substring(length - max)
    val cut = tail.indexOf('\n').let { if (it in 0..200) it + 1 else 0 }
    return TrimResult(tail.substring(cut), true)
}

data class TrimResult(val text: String, val truncated: Boolean)
