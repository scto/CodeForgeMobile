package com.codeforge.libs.x2c.generator

/** Umrechnung von Android-Attributwerten in Kotlin-Ausdrücke. Rein, ohne Zustand. */
internal object LayoutValues {

    fun pascal(name: String): String =
        name.split('_', '-', '.').filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar { c -> c.uppercaseChar() } }

    fun camel(name: String): String = pascal(name).replaceFirstChar { it.lowercaseChar() }

    /** `@+id/btn_submit` / `@id/btn_submit` -> `btn_submit`; sonst null. */
    fun idName(value: String?): String? {
        if (value == null) return null
        val v = value.trim()
        return when {
            v.startsWith("@+id/") -> v.removePrefix("@+id/")
            v.startsWith("@id/") -> v.removePrefix("@id/")
            else -> null
        }?.takeIf { it.isNotBlank() }
    }

    /** `16dp` -> (16, "dp"); `@dimen/x` und anderes liefern null. */
    fun number(value: String): Pair<Double, String>? {
        val m = Regex("""^(-?\d+(?:\.\d+)?)(dp|dip|sp|px|pt|mm|in)?$""").matchEntire(value.trim()) ?: return null
        return m.groupValues[1].toDouble() to m.groupValues[2].ifEmpty { "" }
    }

    fun numberLiteral(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()

    fun floatLiteral(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite()) "${d.toLong()}f" else "${d}f"

    /** Farbe als Kotlin-Ausdruck oder null, wenn nicht abbildbar. */
    fun colorHex(value: String): String? {
        val v = value.trim()
        if (!v.startsWith("#")) return null
        val hex = v.removePrefix("#")
        if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
        val full = when (hex.length) {
            3 -> "FF" + hex.map { "$it$it" }.joinToString("")
            4 -> hex.map { "$it$it" }.joinToString("")
            6 -> "FF$hex"
            8 -> hex
            else -> return null
        }
        return "Color(0x${full.uppercase()})"
    }

    val ANDROID_COLORS = mapOf(
        "white" to "Color.White",
        "black" to "Color.Black",
        "transparent" to "Color.Transparent",
        "darker_gray" to "Color.DarkGray",
        "holo_red_dark" to "Color.Red"
    )

    /** Flags wie `center_vertical|end` als Menge. */
    fun flags(value: String?): Set<String> =
        value?.split('|')?.map { it.trim().lowercase() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    /** Kotlin-String-Literal; `\@`/`\?` aus Android-Ressourcen werden entschärft, `$` und `"` maskiert. */
    fun stringLiteral(raw: String): String {
        val s = raw.replace("\\@", "@").replace("\\?", "?")
            .replace("\"", "\\\"").replace("$", "\\$")
        return "\"$s\""
    }
}
