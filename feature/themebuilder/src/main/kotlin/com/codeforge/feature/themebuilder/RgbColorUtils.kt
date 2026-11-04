/**
 * Modul: :feature:themebuilder
 * @author Thomas Schmid
 */
package com.codeforge.feature.themebuilder

import java.util.Locale

/** Hilfen für Farben im Hex-/RGB-Format. */
object RgbColorUtils {

    /** Schnellauswahl-Farben. */
    val QUICK_PRESETS: List<String> = listOf(
        "#F44336", // Material Red
        "#E91E63", // Material Pink
        "#9C27B0", // Material Purple
        "#673AB7", // Material Deep Purple
        "#3F51B5", // Material Indigo
        "#2196F3", // Material Blue
        "#009688", // Material Teal
        "#4CAF50", // Material Green
        "#FFC107", // Material Amber
        "#FF5722"  // Material Deep Orange
    )

    /** Wandelt `#RRGGBB` in RGB; `null` bei ungültiger Eingabe. */
    fun hexToRgb(hex: String): Triple<Int, Int, Int>? {
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6) return null
        return runCatching {
            val r = clean.substring(0, 2).toInt(16)
            val g = clean.substring(2, 4).toInt(16)
            val b = clean.substring(4, 6).toInt(16)
            Triple(r, g, b)
        }.getOrNull()
    }

    /** Wandelt RGB in `#RRGGBB`. */
    fun rgbToHex(r: Int, g: Int, b: Int): String {
        val cr = r.coerceIn(0, 255)
        val cg = g.coerceIn(0, 255)
        val cb = b.coerceIn(0, 255)
        return String.format(Locale.ROOT, "#%02X%02X%02X", cr, cg, cb)
    }

    /** Ob der Text ein gültiger Hex-Farbwert ist. */
    fun isValidHex(hex: String): Boolean {
        return hexToRgb(hex) != null
    }
}
