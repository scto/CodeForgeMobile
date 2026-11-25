// Modul: :core:designsystem
package com.codeforge.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Darstellung für die Editor-Vorschau in den Einstellungen (Werte wie in den Editor-Einstellungen gespeichert).
 *
 * @param theme Editor-Theme-ID (`auto`, `dark_plus`, `ext:<plugin>/<theme>` …).
 * @param fontSizeSp Schriftgröße in sp.
 * @param fontFamily gespeicherte Schrift (Asset-Pfad oder leer = Monospace).
 * @param showLineNumbers Zeilennummern zeigen.
 * @param wordWrap Zeilen umbrechen.
 * @param lineSpacingPercent Zeilenabstand in Prozent.
 * @param fontLigatures Ligaturen der Schrift zeichnen.
 * @param showNonPrintableChars Leerzeichen, Tabs und Zeilenenden zeigen.
 * @param tabSize Tab-Breite.
 * @param useTreeSitter Tree-sitter statt TextMate für die Farben.
 */
@Immutable
data class EditorPreviewSpec(
    val theme: String = "auto",
    val fontSizeSp: Int = 14,
    val fontFamily: String = "",
    val showLineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
    val lineSpacingPercent: Int = 100,
    val fontLigatures: Boolean = true,
    val showNonPrintableChars: Boolean = false,
    val tabSize: Int = 4,
    val useTreeSitter: Boolean = true,
)

/**
 * Zeichnet eine Editor-Vorschau. Die App stellt die Umsetzung aus `:feature:editor` bereit, damit Einstellungen
 * und Editor als Module getrennt bleiben; `null` = keine Vorschau verfügbar (z. B. in Tests).
 */
val LocalEditorPreview = staticCompositionLocalOf<(@Composable (EditorPreviewSpec, Modifier) -> Unit)?> { null }
