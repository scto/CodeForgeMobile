// Modul: :core:domain
package com.codeforge.core.domain.extension

/**
 * Farben des Terminals im Format von Termux `colors.properties`: `foreground`, `background`, `cursor` und die
 * 16 ANSI-Farben `color0`…`color15`, jeweils `#RRGGBB` (ARGB ohne Alpha, Alpha = 0xFF).
 */
data class TerminalPalette(
    val foreground: Int,
    val background: Int,
    val cursor: Int,
    val ansi: List<Int>,
) {
    init {
        require(ansi.size == ANSI_COUNT) { "16 ANSI-Farben erwartet" }
    }

    /** Als `colors.properties` (mit [header] als Kommentarzeilen). */
    fun toProperties(header: List<String> = emptyList()): String = buildString {
        header.forEach { appendLine("# $it") }
        appendLine("foreground=${hex(foreground)}")
        appendLine("background=${hex(background)}")
        appendLine("cursor=${hex(cursor)}")
        ansi.forEachIndexed { i, c -> appendLine("color$i=${hex(c)}") }
    }

    /** Konstanten. */
    companion object {
        /** Anzahl der ANSI-Farben. */
        const val ANSI_COUNT = 16

        private fun hex(c: Int) = "#%06X".format(c and 0xFFFFFF)
    }
}

/** Liest `colors.properties`; fehlende ANSI-Farben werden mit den Termux-Standardfarben ergänzt. */
object TerminalColorsFile {
    private val HEX = Regex("#?([0-9a-fA-F]{6})")

    /** Termux-Standard (xterm-Farben, schwarzer Hintergrund). */
    val TERMUX_DEFAULT = TerminalPalette(
        foreground = 0xFFFFFF,
        background = 0x000000,
        cursor = 0xFFFFFF,
        ansi = listOf(
            0x000000, 0xCD0000, 0x00CD00, 0xCDCD00, 0x6495ED, 0xCD00CD, 0x00CDCD, 0xE5E5E5,
            0x7F7F7F, 0xFF0000, 0x00FF00, 0xFFFF00, 0x5C5CFF, 0xFF00FF, 0x00FFFF, 0xFFFFFF,
        ),
    )

    /** Parst [text]; `null`, wenn weder Vorder- noch Hintergrund noch eine ANSI-Farbe gültig gesetzt ist. */
    fun parse(text: String): TerminalPalette? {
        val values = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") }
            .mapNotNull { line ->
                val sep = line.indexOfFirst { it == '=' || it == ':' }
                if (sep <= 0) null else line.substring(0, sep).trim().lowercase() to line.substring(sep + 1).trim()
            }
            .mapNotNull { (k, v) -> HEX.matchEntire(v)?.let { k to it.groupValues[1].toInt(16) } }
            .toMap()
        if (values.isEmpty()) return null
        val d = TERMUX_DEFAULT
        val fg = values["foreground"] ?: d.foreground
        return TerminalPalette(
            foreground = fg,
            background = values["background"] ?: d.background,
            cursor = values["cursor"] ?: fg,
            ansi = List(TerminalPalette.ANSI_COUNT) { i -> values["color$i"] ?: d.ansi[i] },
        )
    }
}
