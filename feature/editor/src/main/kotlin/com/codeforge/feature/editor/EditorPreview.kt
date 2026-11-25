// Modul: :feature:editor
package com.codeforge.feature.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.codeforge.core.designsystem.EditorPreviewSpec

// Kotlin-Beispiel mit Schlüsselwörtern, Typen, Strings, Zahlen, Kommentaren und Annotationen.
private val PREVIEW_CODE = """
    |// Vorschau des Editor-Themes
    |package com.example.preview
    |
    |@JvmInline
    |value class Meter(val value: Double)
    |
    |/** Berechnet die Summe aller geraden Zahlen. */
    |fun sumEven(numbers: List<Int>): Int {
    |    val result = numbers.filter { it % 2 == 0 }.sum()
    |    println("Summe: ${'$'}result")
    |    return result
    |}
    |
    |class Greeter(private val name: String = "Welt") {
    |    fun greet() = "Hallo, ${'$'}name!"	// Tabulator davor
    |    val pi = 3.14159
    |}
""".trimMargin()

/**
 * Nur lesbarer Editor mit Beispielcode, der Theme, Schrift, Schriftgröße, Zeilenabstand, Zeilennummern,
 * Zeilenumbruch, Ligaturen und unsichtbare Zeichen wie im echten Editor zeigt.
 */
@Composable
fun EditorPreview(spec: EditorPreviewSpec, modifier: Modifier = Modifier) {
    val settings = remember(spec) {
        EditorDisplaySettings(
            tabSize = spec.tabSize.coerceAtLeast(1),
            useTreeSitter = spec.useTreeSitter,
            textmateTheme = spec.theme,
            fontSizeSp = spec.fontSizeSp.toFloat(),
            fontFamilyAssetPath = spec.fontFamily,
            wordWrap = spec.wordWrap,
            showNonPrintableChars = spec.showNonPrintableChars,
            stickyScrollEnabled = false,
            magnifierEnabled = false,
            fontLigatures = spec.fontLigatures,
            minimapEnabled = false,
            pinchToZoomEnabled = false,
            showLineNumbers = spec.showLineNumbers,
            lineSpacingPercent = spec.lineSpacingPercent,
            readOnlyDefault = true,
            colorPreviewsEnabled = false,
        )
    }
    SoraCodeEditor(
        modifier = modifier,
        path = PREVIEW_PATH,
        content = PREVIEW_CODE,
        language = EditorLanguageType.fromPath(PREVIEW_PATH),
        settings = settings,
        onContentChanged = {},
    )
}

private const val PREVIEW_PATH = "/preview/Preview.kt"
