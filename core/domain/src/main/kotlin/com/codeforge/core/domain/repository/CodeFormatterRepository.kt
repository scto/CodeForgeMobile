package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.FormattedCode
import com.codeforge.core.domain.model.LspRange
import com.codeforge.core.domain.model.UnifiedFormatOptions

/**
 * Zentraler Repository-Vertrag für einheitliches Formatieren im Editor.
 *
 * Reihenfolge der Strategien je Sprache:
 * 1. LSP-Formatierung (documentFormatting bzw. rangeFormatting), wenn verfügbar.
 * 2. Termux-Prozess: Kotlin -> `ktlint --format`, Java -> `google-java-format` (mit 10s Timeout).
 * 3. Eingebauter CodeFormatter als robuster Fallback mit Kennzeichnung.
 */
interface CodeFormatterRepository {

    suspend fun formatFile(
        path: String,
        content: String,
        projectRoot: String? = null,
        fallbackOptions: UnifiedFormatOptions = UnifiedFormatOptions()
    ): Result<FormattedCode>

    suspend fun formatRange(
        path: String,
        content: String,
        range: LspRange,
        projectRoot: String? = null,
        fallbackOptions: UnifiedFormatOptions = UnifiedFormatOptions()
    ): Result<FormattedCode>
}
