// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.PreviewRenderResult

interface ComposePreviewRenderer {
    suspend fun render(filePath: String, sourceCode: String, functionName: String): Result<PreviewRenderResult>
}
