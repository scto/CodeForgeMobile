// Modul: :libs:theme-kit
package com.codeforge.libs.theme_kit

/** Umwandlung zwischen `#RRGGBB` und ARGB-Int. */
object ColorHex {
    private const val HEX_RADIX = 16
    private const val RGB_MASK = 0xFFFFFF
    private val HEX6 = Regex("^#?[0-9A-Fa-f]{6}$")

    /** `#RRGGBB` (Raute optional) → ARGB mit voller Deckkraft; sonst `null`. */
    fun parse(value: String?): Int? {
        val v = value?.trim() ?: return null
        if (!HEX6.matches(v)) return null
        return (0xFF000000L or v.removePrefix("#").toLong(HEX_RADIX)).toInt()
    }

    /** ARGB → `#RRGGBB` (Großbuchstaben, Deckkraft wird verworfen). */
    fun format(argb: Int): String = "#%06X".format(argb and RGB_MASK)

    /** ARGB → `0xFFRRGGBB` für Kotlin-Quelltext. */
    fun kotlinLiteral(argb: Int): String = "0xFF%06X".format(argb and RGB_MASK)
}
