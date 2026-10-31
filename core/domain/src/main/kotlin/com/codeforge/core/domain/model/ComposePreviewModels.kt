/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

data class ComposableCandidate(
    val functionName: String,
    val hasPreviewAnnotation: Boolean,
    val startLine: Int,
    /** `true`, wenn die Funktion Parameter deklariert (die Vorschau kann nur parameterlose Funktionen rendern). */
    val hasParameters: Boolean = false
)

data class PreviewRenderResult(
    val functionName: String,
    val imageBytes: ByteArray?,
    val compiledFiles: List<String> = emptyList(),
    val isFallbackTheme: Boolean = false,
    val fallbackReason: String? = null
)
