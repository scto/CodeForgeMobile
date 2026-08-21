// Modul: :core:domain
package com.codeforge.core.domain.model

data class LspPosition(val line: Int, val character: Int)

data class LspRange(val start: LspPosition, val end: LspPosition)

enum class DiagnosticSeverity { ERROR, WARNING, INFORMATION, HINT }

data class LspDiagnostic(
    val range: LspRange,
    val severity: DiagnosticSeverity,
    val message: String,
    val source: String? = null
)

data class LspCompletionItem(
    val label: String,
    val detail: String? = null,
    val insertText: String = label
)

data class LspHoverInfo(val contents: String, val range: LspRange? = null)

enum class LspServerState { STOPPED, STARTING, RUNNING, FAILED }
