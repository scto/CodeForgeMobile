/**
 * Modul: :core:data
 * @author Thomas Schmid
 */
package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.ComposableCandidate
import com.codeforge.core.domain.repository.ComposeSourceAnalyzer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Heuristischer, zeilenbasierter Scanner — kein vollständiger Kotlin-Parser. Erkennt
 * `@Composable`-annotierte Funktionsdeklarationen anhand vorangehender Annotationszeilen.
 * Ausreichend für Editor-Live-Erkennung (Abschnitt 4 des Skills), aber nicht robust
 * gegen mehrzeilige Annotationsparameter oder stark verschachtelte KDoc-Blöcke.
 */
@Singleton
class ComposeSourceAnalyzerImpl @Inject constructor() : ComposeSourceAnalyzer {

    private val functionRegex = Regex("""^(?:public\s+|private\s+|internal\s+)?fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
    private val annotationRegex = Regex("""^@(\w+)""")

    override fun findComposables(sourceCode: String): List<ComposableCandidate> {
        val pendingAnnotations = mutableSetOf<String>()
        val results = mutableListOf<ComposableCandidate>()

        sourceCode.lines().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed

            val annotationMatch = annotationRegex.find(line)
            if (annotationMatch != null) {
                pendingAnnotations.add(annotationMatch.groupValues[1])
                return@forEachIndexed
            }

            val functionMatch = functionRegex.find(line)
            if (functionMatch != null) {
                if ("Composable" in pendingAnnotations) {
                    results.add(
                        ComposableCandidate(
                            functionName = functionMatch.groupValues[1],
                            hasPreviewAnnotation = "Preview" in pendingAnnotations,
                            startLine = index
                        )
                    )
                }
                pendingAnnotations.clear()
            }
        }

        return results
    }
}
