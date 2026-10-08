package com.codeforge.libs.code_tools.edit

/** Ersetzt `[replaceStart, replaceEnd)` durch [replacement]; danach gilt die Auswahl `[selStart, selEnd]` (Offsets im NEUEN Text). */
data class TextEdit(
    val replaceStart: Int,
    val replaceEnd: Int,
    val replacement: String,
    val selStart: Int,
    val selEnd: Int,
) {
    fun applyTo(text: String): String = text.substring(0, replaceStart) + replacement + text.substring(replaceEnd)
}

/** Kommentar-Syntax einer Sprache (Zeilen- oder Blockstil pro Zeile, wie bei XML). */
data class CommentStyle(val prefix: String, val suffix: String = "")

/** Reine Zeilenbefehle (Kommentar umschalten, Ein-/Ausrücken, Duplizieren, Verschieben, Löschen). */
object LineEdits {

    fun commentStyleFor(path: String): CommentStyle? {
        val name = path.substringAfterLast('/')
        return when (name.substringAfterLast('.', "").lowercase()) {
            "kt", "kts", "java", "gradle", "c", "cc", "cpp", "cxx", "h", "hpp", "js", "ts", "json5", "proto", "dart", "swift" -> CommentStyle("//")
            "xml", "html", "md" -> CommentStyle("<!--", "-->")
            "py", "sh", "bash", "toml", "yaml", "yml", "properties", "pro", "cmake" -> CommentStyle("#")
            else -> if (name == "CMakeLists.txt") CommentStyle("#") else if (name == ".gitignore") CommentStyle("#") else null
        }
    }

    private class LineRange(val start: Int, val end: Int, val lines: List<String>)

    private fun range(text: String, selStart: Int, selEnd: Int): LineRange {
        val a = selStart.coerceIn(0, text.length)
        var b = selEnd.coerceIn(a, text.length)
        // Auswahl endet am Zeilenanfang → diese Zeile gehört nicht mehr dazu
        if (b > a && b > 0 && text[b - 1] == '\n') b--
        val start = text.lastIndexOf('\n', a - 1).let { if (a == 0) 0 else it + 1 }
        val end = text.indexOf('\n', b).let { if (it < 0) text.length else it }
        return LineRange(start, end, text.substring(start, end).split('\n'))
    }

    /** Verschiebt [offset] gemäß Zeilenänderungen `(absolute Position, entfernt, eingefügt)`. */
    private fun adjust(offset: Int, changes: List<Triple<Int, Int, Int>>): Int {
        var result = offset
        for ((pos, removed, inserted) in changes) {
            if (pos >= offset) continue
            val within = minOf(removed, offset - pos)
            result += inserted - within
        }
        return result
    }

    fun toggleComment(text: String, selStart: Int, selEnd: Int, style: CommentStyle): TextEdit {
        val r = range(text, selStart, selEnd)
        val nonBlank = r.lines.filter { it.isNotBlank() }
        val prefix = style.prefix
        val suffix = style.suffix
        val allCommented = nonBlank.isNotEmpty() && nonBlank.all {
            val t = it.trim()
            t.startsWith(prefix) && (suffix.isEmpty() || t.endsWith(suffix))
        }
        val minIndent = nonBlank.minOfOrNull { l -> l.length - l.trimStart().length } ?: 0
        val changes = ArrayList<Triple<Int, Int, Int>>()
        val newLines = ArrayList<String>()
        var pos = r.start
        for (line in r.lines) {
            var newLine = line
            if (line.isNotBlank()) {
                if (allCommented) {
                    val ind = line.length - line.trimStart().length
                    var body = line.substring(ind).removePrefix(prefix)
                    var removed = prefix.length
                    if (body.startsWith(" ")) { body = body.substring(1); removed++ }
                    var tail = 0
                    if (suffix.isNotEmpty()) {
                        body = body.removeSuffix(suffix); tail = suffix.length
                        if (body.endsWith(" ")) { body = body.dropLast(1); tail++ }
                    }
                    newLine = line.substring(0, ind) + body
                    changes += Triple(pos + ind, removed, 0)
                    if (tail > 0) changes += Triple(pos + line.length - tail, tail, 0)
                } else {
                    val insert = prefix + " "
                    newLine = line.substring(0, minIndent) + insert + line.substring(minIndent) + if (suffix.isNotEmpty()) " $suffix" else ""
                    changes += Triple(pos + minIndent, 0, insert.length)
                    if (suffix.isNotEmpty()) changes += Triple(pos + line.length, 0, suffix.length + 1)
                }
            }
            newLines += newLine
            pos += line.length + 1
        }
        val replacement = newLines.joinToString("\n")
        return TextEdit(r.start, r.end, replacement, adjust(selStart, changes), adjust(selEnd, changes))
    }

    fun indent(text: String, selStart: Int, selEnd: Int, unit: String = "    "): TextEdit {
        val r = range(text, selStart, selEnd)
        val changes = ArrayList<Triple<Int, Int, Int>>()
        var pos = r.start
        val out = r.lines.map { line ->
            val res = if (line.isBlank()) line else { changes += Triple(pos, 0, unit.length); unit + line }
            pos += line.length + 1
            res
        }
        return TextEdit(r.start, r.end, out.joinToString("\n"), adjust(selStart, changes), adjust(selEnd, changes))
    }

    fun dedent(text: String, selStart: Int, selEnd: Int, unit: String = "    "): TextEdit {
        val r = range(text, selStart, selEnd)
        val changes = ArrayList<Triple<Int, Int, Int>>()
        var pos = r.start
        val out = r.lines.map { line ->
            val remove = when {
                line.startsWith("\t") -> 1
                else -> line.takeWhile { it == ' ' }.length.coerceAtMost(unit.length)
            }
            if (remove > 0) changes += Triple(pos, remove, 0)
            pos += line.length + 1
            line.substring(remove)
        }
        return TextEdit(r.start, r.end, out.joinToString("\n"), adjust(selStart, changes), adjust(selEnd, changes))
    }

    fun duplicateLines(text: String, selStart: Int, selEnd: Int): TextEdit {
        val r = range(text, selStart, selEnd)
        val block = text.substring(r.start, r.end)
        val shift = block.length + 1
        return TextEdit(r.start, r.end, block + "\n" + block, selStart + shift, selEnd + shift)
    }

    fun deleteLines(text: String, selStart: Int, selEnd: Int): TextEdit {
        val r = range(text, selStart, selEnd)
        // Zeilenumbruch mitnehmen: nach der Zeile, sonst (letzte Zeile) davor
        return when {
            r.end < text.length -> TextEdit(r.start, r.end + 1, "", r.start, r.start)
            r.start > 0 -> TextEdit(r.start - 1, r.end, "", r.start - 1, r.start - 1)
            else -> TextEdit(r.start, r.end, "", 0, 0)
        }
    }

    /** `null`, wenn der Block schon am Rand steht. */
    fun moveLines(text: String, selStart: Int, selEnd: Int, up: Boolean): TextEdit? {
        val r = range(text, selStart, selEnd)
        val block = text.substring(r.start, r.end)
        return if (up) {
            if (r.start == 0) return null
            val prevStart = text.lastIndexOf('\n', r.start - 2).let { it + 1 }
            val prev = text.substring(prevStart, r.start - 1)
            val shift = prev.length + 1
            TextEdit(prevStart, r.end, block + "\n" + prev, selStart - shift, selEnd - shift)
        } else {
            if (r.end >= text.length) return null
            val nextEnd = text.indexOf('\n', r.end + 1).let { if (it < 0) text.length else it }
            val next = text.substring(r.end + 1, nextEnd)
            val shift = next.length + 1
            TextEdit(r.start, nextEnd, next + "\n" + block, selStart + shift, selEnd + shift)
        }
    }
}
