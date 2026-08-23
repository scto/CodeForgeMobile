/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

data class ComposableCandidate(
    val functionName: String,
    val hasPreviewAnnotation: Boolean,
    val startLine: Int
)

data class PreviewRenderResult(
    val functionName: String,
    val imageBytes: ByteArray?
)
