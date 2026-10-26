package com.codeforge.libs.x2c.report

import com.codeforge.core.domain.model.X2cBuildStepStatus
import com.codeforge.core.domain.model.X2cFileStatus
import com.codeforge.core.domain.model.X2cReport
import com.codeforge.libs.x2c.verify.SecretScrubber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Task 52.10: Bericht als Markdown (`Backup/x2c-report-<ts>.md`). Pur, ohne Geheimnisse. */
object X2cReportWriter {

    fun fileName(timestampEpochMillis: Long): String =
        "x2c-report-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(timestampEpochMillis)) + ".md"

    fun render(r: X2cReport): String {
        val sb = StringBuilder()
        fun h(t: String) { sb.appendLine().appendLine("## $t").appendLine() }
        sb.appendLine("# x2c-Bericht: XML-Layouts nach Compose")
        sb.appendLine()
        sb.appendLine("Projekt: `${r.projectPath}`  ")
        sb.appendLine("Zeitpunkt: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(r.timestampEpochMillis))}")
        sb.appendLine()
        sb.appendLine("> Layout automatisch übertragen, **Verhalten bitte prüfen** (Listener, Adapter, Datenbindung, Animationen werden nicht umgeschrieben).")

        h("Zusammenfassung")
        sb.appendLine("| Ergebnis | Anzahl |")
        sb.appendLine("|---|---|")
        sb.appendLine("| Erzeugt | ${r.generatedCount} |")
        sb.appendLine("| Erzeugt mit Hinweisen | ${r.generatedWithNotesCount} |")
        sb.appendLine("| Übersprungen | ${r.skippedCount} |")
        sb.appendLine("| Fehlgeschlagen | ${r.failedCount} |")
        sb.appendLine("| `TODO(x2c)` gesamt | ${r.totalTodos} |")
        sb.appendLine("| Theme erzeugt | ${if (r.themeGenerated) "ja" else "nein"} |")

        h("Layouts")
        if (r.fileResults.isEmpty()) sb.appendLine("Keine Layouts.")
        for (f in r.fileResults) {
            val status = when (f.status) {
                X2cFileStatus.GENERATED -> "erzeugt"
                X2cFileStatus.GENERATED_WITH_NOTES -> "erzeugt mit Hinweisen"
                X2cFileStatus.SKIPPED -> "übersprungen"
                X2cFileStatus.FAILED -> "fehlgeschlagen"
            }
            sb.append("- `${f.sourceXmlPath}`: $status")
            if (f.todoCount > 0) sb.append(", ${f.todoCount} × `TODO(x2c)`")
            f.outputComposePath?.let { sb.append(" → `$it`") }
            sb.appendLine()
            f.errorMessage?.let { sb.appendLine("  - Fehler: $it") }
            f.notes.filterNot { it.startsWith("Stufe 2:") || it.startsWith("Bitte prüfen") }.forEach { sb.appendLine("  - $it") }
        }

        h("Erzeugte Dateien")
        val created = r.fileResults.mapNotNull { it.outputComposePath }.distinct()
        if (created.isEmpty()) sb.appendLine("Keine.") else created.forEach { sb.appendLine("- `$it`") }

        h("Compose im Build")
        sb.appendLine("- Kotlin: ${r.kotlinVersion ?: "unverändert/unbekannt"}")
        sb.appendLine("- Compose BOM: ${r.composeBom ?: "unverändert/unbekannt"}")
        if (r.buildChangedFiles.isEmpty()) sb.appendLine("- Geänderte Build-Dateien: keine")
        else { sb.appendLine("- Geänderte Build-Dateien:"); r.buildChangedFiles.forEach { sb.appendLine("  - `$it`") } }
        if (r.addedDependencies.isNotEmpty()) { sb.appendLine("- Ergänzte Abhängigkeiten:"); r.addedDependencies.forEach { sb.appendLine("  - `$it`") } }
        r.buildNotes.forEach { sb.appendLine("- $it") }

        h("Stufe 2: Activities und Fragments")
        sb.appendLine("Umgestellt: ${r.stage2WiredCount}, nicht umgestellt: ${r.stage2SkippedCount}")
        val stage2 = r.fileResults.flatMap { f -> f.notes.filter { it.startsWith("Stufe 2:") }.map { n -> "`${f.sourceXmlPath}`: ${n.removePrefix("Stufe 2: ")}" } }
        stage2.forEach { sb.appendLine("- $it") }
        val review = r.fileResults.flatMap { it.notes.filter { n -> n.startsWith("Bitte prüfen") } }
        if (review.isNotEmpty()) { sb.appendLine(); sb.appendLine("Bitte prüfen:"); review.forEach { sb.appendLine("- ${it.removePrefix("Bitte prüfen ")}") } }

        h("Code, der noch auf Views zugreift")
        if (r.remainingViewAccesses.isEmpty()) sb.appendLine("Keine gefunden.") else {
            sb.appendLine("Offene Handarbeit (Stufe 3, nicht Teil von x2c):")
            r.remainingViewAccesses.forEach { sb.appendLine("- $it") }
        }

        h("Entfernte XML-Dateien")
        sb.appendLine("Anzahl: ${r.removedXmlCount}")
        r.fileResults.filter { f -> f.notes.any { it.startsWith("XML entfernt") } }.forEach { sb.appendLine("- `${it.sourceXmlPath}`") }

        h("Backup und Journal")
        sb.appendLine("- Backup: ${if (r.backupPath.isEmpty()) "keines (nichts zu ändern)" else "`${r.backupPath}`"}")
        sb.appendLine("- Journal: ${if (r.journalPath.isEmpty()) "keines" else "`${r.journalPath}`"}")
        if (r.journalPath.isNotEmpty()) sb.appendLine("- Rückgängig: über das Projektmenü oder das Ergebnis, solange das Journal existiert.")

        h("Build-Prüfung")
        when {
            r.buildSteps.isEmpty() -> sb.appendLine("Nicht ausgeführt.")
            else -> {
                sb.appendLine("Ergebnis: " + when (r.buildVerificationSuccess) { true -> "**erfolgreich**"; false -> "**fehlgeschlagen**"; null -> "nicht möglich" })
                for (s in r.buildSteps) {
                    val st = when (s.status) { X2cBuildStepStatus.SUCCESS -> "ok"; X2cBuildStepStatus.FAILED -> "fehlgeschlagen"; X2cBuildStepStatus.SKIPPED -> "übersprungen" }
                    sb.appendLine("- `${s.tasks.joinToString(" ")}`: $st${s.message?.let { " ($it)" } ?: ""}")
                    s.problems.filter { it.severity == "error" }.forEach { p ->
                        val where = p.path?.let { "`$it${p.line?.let { l -> ":$l" } ?: ""}`: " } ?: ""
                        sb.appendLine("  - ${where}${p.message}${if (p.preExisting) " _(bestand schon vorher)_" else ""}")
                    }
                }
            }
        }

        if (r.notes.isNotEmpty()) { h("Hinweise"); r.notes.forEach { sb.appendLine("- $it") } }
        return SecretScrubber.scrub(sb.toString())
    }
}
