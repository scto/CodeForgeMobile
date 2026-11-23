// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

import com.codeforge.libs.color_utilities.quantize.QuantizerCelebi
import com.codeforge.libs.color_utilities.score.Score

/** Wählt aus den Pixeln eines Bildes die für ein Theme geeignetsten Grundfarben (wie Android-Hintergrundfarben). */
object SeedExtractor {
    private const val MAX_COLORS = 128
    private const val ALPHA_SHIFT = 24
    private const val OPAQUE = 0xFF
    private const val FALLBACK = 0xFF4285F4.toInt()

    /**
     * @param pixels ARGB-Pixel (am besten vorher auf ≤ 128×128 verkleinert).
     * @param count Höchstzahl der Vorschläge.
     * @return Vorschläge, bester zuerst; nie leer (Rückfall: Google-Blau).
     */
    fun extract(pixels: IntArray, count: Int = 4): List<Int> {
        val opaque = pixels.filter { it ushr ALPHA_SHIFT == OPAQUE }.toIntArray()
        if (opaque.isEmpty()) return listOf(FALLBACK)
        return Score.score(QuantizerCelebi.quantize(opaque, MAX_COLORS), count, FALLBACK, true)
    }
}
