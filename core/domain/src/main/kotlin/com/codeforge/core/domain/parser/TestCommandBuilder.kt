// Modul: :core:domain
package com.codeforge.core.domain.parser

import com.codeforge.core.domain.model.TestCaseResult

object TestCommandBuilder {

    /**
     * Baut das Gradle-Kommandozeilen-Argument für Testläufe.
     *
     * @param moduleGradlePath Gradle-Pfad des Moduls (z. B. ":core:domain" oder ":" bzw. null für alle Module)
     * @param variant Build-Variante (Standard: "Debug")
     * @param testFilter Optionaler Filter (Klasse oder Klasse.Methode)
     * @param failedCases Optionale Liste fehlgeschlagener Tests für "Nur fehlgeschlagene wiederholen"
     */
    fun buildTestTaskCommand(
        moduleGradlePath: String? = null,
        variant: String = "Debug",
        testFilter: String? = null,
        failedCases: List<TestCaseResult> = emptyList()
    ): String {
        val baseTask = if (moduleGradlePath.isNullOrBlank() || moduleGradlePath == ":") {
            "test${variant}UnitTest"
        } else {
            val normalizedPath = if (moduleGradlePath.startsWith(":")) moduleGradlePath else ":$moduleGradlePath"
            "$normalizedPath:test${variant}UnitTest"
        }

        val filterArgs = mutableListOf<String>()

        if (!testFilter.isNullOrBlank()) {
            filterArgs.add(formatTestsFilter(testFilter.trim()))
        } else if (failedCases.isNotEmpty()) {
            val distinctCases = failedCases.map { "${it.className}.${it.name}" }.distinct()
            for (caseTarget in distinctCases) {
                filterArgs.add(formatTestsFilter(caseTarget))
            }
        }

        return if (filterArgs.isEmpty()) {
            baseTask
        } else {
            "$baseTask ${filterArgs.joinToString(" ")}"
        }
    }

    /**
     * Formatiert einen einzelnen `--tests` Filter für die Gradle-CLI.
     * Maskiert Anführungszeichen und Leerzeichen/Sonderzeichen sicher.
     */
    fun formatTestsFilter(filter: String): String {
        val sanitized = filter.replace("\"", "\\\"").trim()
        return "--tests \"$sanitized\""
    }
}
