/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Parser für Android-Attributwerte (Dimensionen, Farben), rein JVM.
 */
package com.codeforge.feature.layoutdesigner.model

private val dimensionRegex = Regex("""^(-?\d+(?:\.\d+)?)\s*(dp|dip|sp|px|pt|mm|in)?$""")

/** `16dp`, `14sp`, `8` → Zahlenwert (px/pt/mm/in werden grob als dp interpretiert). `null` bei Referenzen. */
fun parseDimension(value: String?): Float? {
    val m = dimensionRegex.matchEntire(value?.trim().orEmpty()) ?: return null
    return m.groupValues[1].toFloatOrNull()
}

/** `#RGB`, `#ARGB`, `#RRGGBB`, `#AARRGGBB` → ARGB als Long (0xAARRGGBB); sonst `null`. */
fun parseColorArgb(value: String?): Long? {
    val v = value?.trim().orEmpty()
    if (!v.startsWith("#")) return null
    val hex = v.drop(1)
    if (hex.any { !it.isDigit() && it.lowercaseChar() !in 'a'..'f' }) return null
    return when (hex.length) {
        3 -> hex.map { "$it$it" }.joinToString("").toLong(16) or 0xFF000000L
        4 -> hex.map { "$it$it" }.joinToString("").toLong(16)
        6 -> hex.toLong(16) or 0xFF000000L
        8 -> hex.toLong(16)
        else -> null
    }
}

enum class SizeMode { MATCH_PARENT, WRAP_CONTENT, FIXED }

data class SizeSpec(val mode: SizeMode, val dp: Float = 0f)

fun parseSize(value: String?): SizeSpec = when (value?.trim()) {
    "match_parent", "fill_parent" -> SizeSpec(SizeMode.MATCH_PARENT)
    "wrap_content", null, "" -> SizeSpec(SizeMode.WRAP_CONTENT)
    else -> parseDimension(value)?.let { SizeSpec(SizeMode.FIXED, it) } ?: SizeSpec(SizeMode.WRAP_CONTENT)
}
