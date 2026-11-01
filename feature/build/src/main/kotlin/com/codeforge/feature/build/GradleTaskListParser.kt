/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

/** Liest die Task-Namen aus der Ausgabe von `gradlew tasks --all`. */
internal object GradleTaskListParser {

    private val taskLine = Regex("""^([A-Za-z:][\w:.\-]*)(?: - .*)?$""")

    fun parse(lines: List<String>): List<String> =
        lines.asSequence()
            .map { it.trim() }
            .mapNotNull { taskLine.matchEntire(it)?.groupValues?.get(1) }
            .filterNot { it.startsWith("---") || it.equals("BUILD", ignoreCase = true) }
            .distinct()
            .sorted()
            .toList()
}
