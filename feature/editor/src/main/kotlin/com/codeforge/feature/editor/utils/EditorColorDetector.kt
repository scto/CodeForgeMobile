package com.codeforge.feature.editor.utils

import android.graphics.Color
import io.github.rosemoe.sora.lang.styling.color.ConstColor
import io.github.rosemoe.sora.lang.styling.inlayHint.ColorInlayHint

/**
 * Recognizes color specifications in any file format (e.g. #ffff00, #ff0, 0xffff00, ffff00, ff0, etc.)
 * and generates ColorInlayHints for visual inline previews in CodeEditor.
 *
 * @author Thomas Schmid
 */
object EditorColorDetector {

    // Regex for color specifications:
    // 1. #RGB, #ARGB, #RRGGBB, #AARRGGBB (e.g. #FF0, #FFFF, #FFFF00, #FFFF00FF)
    // 2. 0xRRGGBB, 0xAARRGGBB (e.g. 0xFFFF00, 0xFFFFFF00)
    // 3. Raw hex words (3, 4, 6, 8 hex digits) preceded by color delimiters (quotes, equals, colon, space, bracket)
    private val COLOR_REGEX = Regex(
        """(?i)(?:#(?:[0-9a-f]{8}|[0-9a-f]{6}|[0-9a-f]{4}|[0-9a-f]{3})\b|0x(?:[0-9a-f]{8}|[0-9a-f]{6})\b|(?<=["':=\s(,#])(?:[0-9a-f]{8}|[0-9a-f]{6}|[0-9a-f]{4}|[0-9a-f]{3})(?=["';,\s)]))"""
    )

    fun parseColorString(raw: String): Int? {
        val str = raw.trim()
        return try {
            when {
                str.startsWith("#") -> {
                    val hex = str.substring(1)
                    when (hex.length) {
                        3 -> { // #RGB -> #FFRRGGBB
                            val r = hex[0].toString().repeat(2)
                            val g = hex[1].toString().repeat(2)
                            val b = hex[2].toString().repeat(2)
                            Color.parseColor("#FF$r$g$b")
                        }
                        4 -> { // #ARGB -> #AARRGGBB
                            val a = hex[0].toString().repeat(2)
                            val r = hex[1].toString().repeat(2)
                            val g = hex[2].toString().repeat(2)
                            val b = hex[3].toString().repeat(2)
                            Color.parseColor("#$a$r$g$b")
                        }
                        6 -> Color.parseColor("#FF$hex")
                        8 -> Color.parseColor("#$hex")
                        else -> null
                    }
                }
                str.startsWith("0x", ignoreCase = true) -> {
                    val hex = str.substring(2)
                    val longVal = hex.toLong(16)
                    if (hex.length <= 6) {
                        (0xFF000000 or longVal).toInt()
                    } else {
                        longVal.toInt()
                    }
                }
                else -> { // Raw hex string without prefix (e.g. ffff00 or ff0)
                    when (str.length) {
                        3 -> {
                            val r = str[0].toString().repeat(2)
                            val g = str[1].toString().repeat(2)
                            val b = str[2].toString().repeat(2)
                            Color.parseColor("#FF$r$g$b")
                        }
                        4 -> {
                            val a = str[0].toString().repeat(2)
                            val r = str[1].toString().repeat(2)
                            val g = str[2].toString().repeat(2)
                            val b = str[3].toString().repeat(2)
                            Color.parseColor("#$a$r$g$b")
                        }
                        6 -> Color.parseColor("#FF$str")
                        8 -> Color.parseColor("#$str")
                        else -> null
                    }
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun detectColorInlayHints(sourceText: String): List<ColorInlayHint> {
        val hints = mutableListOf<ColorInlayHint>()
        sourceText.lines().forEachIndexed { lineIdx, lineStr ->
            COLOR_REGEX.findAll(lineStr).forEach { match ->
                val parsedColor = parseColorString(match.value)
                if (parsedColor != null) {
                    val endCol = match.range.last + 1
                    hints.add(ColorInlayHint(lineIdx, endCol, ConstColor(parsedColor)))
                }
            }
        }
        return hints
    }
}
