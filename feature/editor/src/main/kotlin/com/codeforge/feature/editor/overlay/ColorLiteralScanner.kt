/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 */
package com.codeforge.feature.editor.overlay

/** Farbliteral in [line] (0-basiert); [argb] im Android-Format 0xAARRGGBB. */
data class ColorMatch(val line: Int, val argb: Int)

/**
 * Findet Farbwerte im Text: `#RGB`, `#ARGB`, `#RRGGBB`, `#AARRGGBB` (Android/XML/JSON/sora-
 * Schemes) sowie Kotlin-Literale `0xAARRGGBB` (z. B. `Color(0xFF6200EE)`). In CSS-artigen
 * Dateien ([alphaLast]) wird 8-stelliges Hex als `#RRGGBBAA` gelesen.
 * Reine Textverarbeitung ohne Android-Abhängigkeit.
 */
object ColorLiteralScanner {

    private val hashRegex = Regex("""(?<![\w&#])#([0-9a-fA-F]{8}|[0-9a-fA-F]{6}|[0-9a-fA-F]{4}|[0-9a-fA-F]{3})(?![0-9a-zA-Z])""")
    private val kotlinHexRegex = Regex("""(?<![\w])0[xX]([0-9a-fA-F]{8})(?![0-9a-zA-Z_])""")

    /** Dateiendungen, in denen Farben gesucht werden (vermeidet Fehltreffer wie `#add` in Fließtext). */
    val SUPPORTED_EXTENSIONS: Set<String> =
        setOf("xml", "json", "kt", "kts", "java", "gradle", "css", "scss", "svg", "yml", "yaml", "toml", "properties")

    private val ALPHA_LAST_EXTENSIONS = setOf("css", "scss", "svg")

    const val MAX_TEXT_LENGTH = 400_000
    const val MAX_MATCHES = 2_000

    fun supports(path: String): Boolean = path.substringAfterLast('.', "").lowercase() in SUPPORTED_EXTENSIONS

    fun scanForPath(path: String, text: String): List<ColorMatch> =
        if (!supports(path)) emptyList()
        else scan(text, alphaLast = path.substringAfterLast('.', "").lowercase() in ALPHA_LAST_EXTENSIONS)

    fun scan(text: String, alphaLast: Boolean = false): List<ColorMatch> {
        if (text.length > MAX_TEXT_LENGTH) return emptyList()
        val found = ArrayList<Pair<Int, ColorMatch>>() // Offset für stabile Reihenfolge je Zeile
        val lineStarts = lineStarts(text)

        for (m in hashRegex.findAll(text)) {
            val argb = parseHash(m.groupValues[1], alphaLast) ?: continue
            found += m.range.first to ColorMatch(lineOf(lineStarts, m.range.first), argb)
            if (found.size >= MAX_MATCHES) break
        }
        if (found.size < MAX_MATCHES) for (m in kotlinHexRegex.findAll(text)) {
            val argb = m.groupValues[1].toLong(16).toInt()
            found += m.range.first to ColorMatch(lineOf(lineStarts, m.range.first), argb)
            if (found.size >= MAX_MATCHES) break
        }
        return found.sortedBy { it.first }.map { it.second }
    }

    internal fun parseHash(hex: String, alphaLast: Boolean): Int? = when (hex.length) {
        3 -> expand(hex).let { 0xFF000000.toInt() or it.toInt(16) }
        4 -> {
            val e = expand(hex) // 8 Stellen: ARGB bzw. RGBA
            toArgb(e, alphaLast)
        }
        6 -> 0xFF000000.toInt() or hex.toLong(16).toInt()
        8 -> toArgb(hex, alphaLast)
        else -> null
    }

    private fun expand(hex: String): String = buildString { hex.forEach { append(it).append(it) } }

    private fun toArgb(eightDigits: String, alphaLast: Boolean): Int {
        val v = eightDigits.toLong(16)
        return if (alphaLast) (((v and 0xFF) shl 24) or (v ushr 8)).toInt() else v.toInt()
    }

    private fun lineStarts(text: String): IntArray {
        val starts = ArrayList<Int>()
        starts += 0
        for (i in text.indices) if (text[i] == '\n') starts += i + 1
        return starts.toIntArray()
    }

    private fun lineOf(starts: IntArray, offset: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (starts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }
}
