/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Übersetzt LSP-Diagnostics (publishDiagnostics-Notifications, modelliert als
 * Flow<Pair<path, List<LspDiagnostic>>> in [LspClientRepository]) in sora-editors
 * [DiagnosticsContainer], welcher als rote/gelbe Unterstreichungen inkl. Tooltip-Popup
 * im Editor dargestellt wird ("Diagnostic Markers and Tooltip Window").
 */
package com.codeforge.feature.editor.lsp

import com.codeforge.core.domain.model.DiagnosticSeverity
import com.codeforge.core.domain.model.LspDiagnostic
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import io.github.rosemoe.sora.text.Content

object LspDiagnosticsBridge {

    /**
     * Baut die [DiagnosticsContainer] für eine einzelne Datei neu auf. sora-editor erwartet
     * Diagnostics als Zeichen-Offsets (nicht Zeile/Spalte), daher die Umrechnung über
     * [Content.getCharIndex].
     */
    fun apply(container: DiagnosticsContainer, content: Content, diagnostics: List<LspDiagnostic>) {
        container.reset()
        diagnostics.forEach { diagnostic ->
            val startLine = diagnostic.range.start.line.coerceIn(0, (content.lineCount - 1).coerceAtLeast(0))
            val endLine = diagnostic.range.end.line.coerceIn(0, (content.lineCount - 1).coerceAtLeast(0))
            val startColumn = diagnostic.range.start.character.coerceAtLeast(0)
            val endColumn = diagnostic.range.end.character.coerceAtLeast(0)

            val startIndex = runCatching { content.getCharIndex(startLine, startColumn) }.getOrDefault(0)
            val endIndex = runCatching { content.getCharIndex(endLine, endColumn) }
                .getOrDefault(startIndex + 1)
                .coerceAtLeast(startIndex + 1)

            container.addDiagnostic(
                DiagnosticRegion(
                    startIndex,
                    endIndex,
                    diagnostic.severity.toSoraLevel(),
                    0L,
                    DiagnosticDetail(
                        diagnostic.source ?: "lsp",
                        diagnostic.message,
                        null,
                        null
                    )
                )
            )
        }
    }

    private fun DiagnosticSeverity.toSoraLevel(): Short = when (this) {
        DiagnosticSeverity.ERROR -> DiagnosticRegion.SEVERITY_ERROR
        DiagnosticSeverity.WARNING -> DiagnosticRegion.SEVERITY_WARNING
        DiagnosticSeverity.INFORMATION -> DiagnosticRegion.SEVERITY_TYPO
        DiagnosticSeverity.HINT -> DiagnosticRegion.SEVERITY_TYPO
    }
}
