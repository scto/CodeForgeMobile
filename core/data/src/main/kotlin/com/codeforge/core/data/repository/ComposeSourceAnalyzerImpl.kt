/**
 * Modul: :core:data
 * @author Thomas Schmid
 */
package com.codeforge.core.data.repository

import com.codeforge.core.domain.model.ComposableCandidate
import com.codeforge.core.domain.model.PreviewOptions
import com.codeforge.core.domain.repository.ComposeSourceAnalyzer
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
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
        var pendingPreviewArgs = ""
        val results = mutableListOf<ComposableCandidate>()
        val lines = sourceCode.lines()

        var index = 0
        while (index < lines.size) {
            val line = lines[index].trim()
            if (line.isEmpty()) { index++; continue }

            val annotationMatch = annotationRegex.find(line)
            if (annotationMatch != null) {
                val name = annotationMatch.groupValues[1]
                pendingAnnotations.add(name)
                if (name == "Preview") {
                    // Argumente können über mehrere Zeilen gehen: bis zur schließenden Klammer sammeln
                    val sb = StringBuilder(line)
                    var depth = line.count { it == '(' } - line.count { it == ')' }
                    while (depth > 0 && index + 1 < lines.size) {
                        index++
                        val next = lines[index].trim()
                        sb.append(' ').append(next)
                        depth += next.count { it == '(' } - next.count { it == ')' }
                    }
                    pendingPreviewArgs = sb.toString()
                }
                index++
                continue
            }

            val functionMatch = functionRegex.find(line)
            if (functionMatch != null) {
                if ("Composable" in pendingAnnotations) {
                    results.add(
                        ComposableCandidate(
                            functionName = functionMatch.groupValues[1],
                            hasPreviewAnnotation = "Preview" in pendingAnnotations,
                            startLine = index,
                            // Nach "(" folgt direkt ")" → parameterlos; sonst (auch bei Zeilenumbruch) hat sie Parameter.
                            hasParameters = !line.substring(functionMatch.range.last + 1).trimStart().startsWith(")"),
                            options = if ("Preview" in pendingAnnotations) parsePreviewOptions(pendingPreviewArgs) else PreviewOptions()
                        )
                    )
                }
                pendingAnnotations.clear()
                pendingPreviewArgs = ""
            }
            index++
        }

        return results
    }

    internal fun parsePreviewOptions(args: String): PreviewOptions {
        fun int(name: String) = Regex("""\b$name\s*=\s*(\d+)""").find(args)?.groupValues?.get(1)?.toIntOrNull()
        val color = Regex("""\bbackgroundColor\s*=\s*(0[xX][0-9A-Fa-f]+|\d+)L?""").find(args)?.groupValues?.get(1)?.let {
            val v = if (it.startsWith("0x", ignoreCase = true)) it.drop(2).toLongOrNull(16) else it.toLongOrNull()
            // Ohne Alpha-Anteil (z. B. 0xFFFFFF) wäre die Farbe unsichtbar → deckend machen
            v?.let { c -> if (c ushr 24 == 0L) c or 0xFF000000L else c }
        }
        val uiMode = Regex("""\buiMode\s*=\s*([^,)]+)""").find(args)?.groupValues?.get(1)?.trim()
        val dark = when {
            uiMode == null -> null
            "UI_MODE_NIGHT_YES" in uiMode || uiMode == "32" || uiMode.equals("0x20", ignoreCase = true) -> true
            "UI_MODE_NIGHT_NO" in uiMode || uiMode == "16" || uiMode.equals("0x10", ignoreCase = true) -> false
            else -> null
        }
        fun str(name: String) = Regex("""\b$name\s*=\s*"([^"]*)"""").find(args)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val device = Regex("""\bdevice\s*=\s*(?:"([^"]*)"|([\w.]+))""").find(args)?.let { m -> (m.groupValues[1].ifEmpty { m.groupValues[2] }).let(::deviceSize) }
        return PreviewOptions(
            showBackground = Regex("""\bshowBackground\s*=\s*true""").containsMatchIn(args),
            backgroundColor = color,
            // Ausdrücklich angegebene Maße gehen vor `device`
            widthDp = int("widthDp") ?: device?.first,
            heightDp = int("heightDp") ?: device?.second,
            darkMode = dark,
            name = str("name"),
            fontScale = Regex("""\bfontScale\s*=\s*([0-9]*\.?[0-9]+)f?""").find(args)?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it > 0f },
            locale = str("locale")
        )
    }

    /** `Devices.PHONE` usw. und `"spec:width=…dp,height=…dp"` → (Breite, Höhe) in dp; sonst `null`. */
    internal fun deviceSize(expr: String): Pair<Int, Int>? {
        val known = mapOf("PHONE" to (411 to 891), "TABLET" to (1280 to 800), "DESKTOP" to (1920 to 1080), "FOLDABLE" to (674 to 841))
        known.entries.firstOrNull { expr.endsWith("Devices.${it.key}") || expr == it.key }?.let { return it.value }
        val w = Regex("""width\s*=\s*([0-9]*\.?[0-9]+)dp""").find(expr)?.groupValues?.get(1)?.toFloatOrNull()
        val h = Regex("""height\s*=\s*([0-9]*\.?[0-9]+)dp""").find(expr)?.groupValues?.get(1)?.toFloatOrNull()
        return if (w != null && h != null) w.toInt() to h.toInt() else null
    }
}
