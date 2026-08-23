/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ComposableCandidate
import com.codeforge.core.domain.model.PreviewRenderResult

/**
 * Reine, plattformunabhängige Kotlin-Quelltext-Analyse (kein I/O). Liegt bewusst in
 * :core:domain mit Implementierung in :core:data, damit sowohl :feature:editor
 * (Erkennung beim Öffnen/Ändern einer Datei) als auch :feature:composepreview
 * (Liste der Vorschau-Kandidaten) sie nutzen können, ohne gegen die Dependency-Regel
 * zu verstoßen (:feature:* darf niemals direkt von :feature:* abhängen).
 */
interface ComposeSourceAnalyzer {
    fun findComposables(sourceCode: String): List<ComposableCandidate>
}

/**
 * Rendert eine einzelne @Composable-Funktion aus Quelltext in ein Bild. Implementiert
 * in :feature:composepreview (siehe KDoc dort zur aktuell noch fehlenden dynamischen
 * Kompilierungs-Pipeline).
 */
interface ComposePreviewRenderer {
    suspend fun render(filePath: String, sourceCode: String, functionName: String): Result<PreviewRenderResult>
}
