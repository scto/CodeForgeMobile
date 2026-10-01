// Modul: :core:domain
package com.codeforge.core.domain.model

enum class LspServerState { STOPPED, STARTING, RUNNING, FAILED }

data class LspPosition(val line: Int, val character: Int)

data class LspCompletionItem(
    val label: String,
    val detail: String? = null,
    val documentation: String? = null,
    val insertText: String? = null
)

data class LspHoverInfo(
    val contents: String
)

data class LspDiagnostic(
    val range: LspRange,
    val message: String,
    val severity: Int = 1
)

data class LspRange(
    val start: LspPosition,
    val end: LspPosition
)
