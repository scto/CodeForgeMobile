/**
 * Plugin: Markdown-Vorschau
 *
 * Kleiner Markdown-Parser (CommonMark-Teilmenge plus GFM-Tabellen und -Aufgaben) ohne Abhängigkeiten.
 */
package com.codeforge.plugin.markdown

/** Block eines Markdown-Dokuments. */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Code(val language: String, val text: String) : MdBlock
    data class Quote(val blocks: List<MdBlock>) : MdBlock
    data class ListItem(val text: String, val checked: Boolean?)
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<ListItem>) : MdBlock
    data class Table(val header: List<String>, val rows: List<List<String>>) : MdBlock
    data object Rule : MdBlock
}

object MarkdownParser {
    private val HEADING = Regex("^(#{1,6})\\s+(.*?)\\s*#*\\s*$")
    private val FENCE = Regex("^(```|~~~)\\s*([\\w+#.-]*)\\s*$")
    private val BULLET = Regex("^\\s{0,3}[-*+]\\s+(.*)$")
    private val ORDERED = Regex("^\\s{0,3}(\\d{1,9})[.)]\\s+(.*)$")
    private val TASK = Regex("^\\[([ xX])]\\s+(.*)$")
    private val RULE = Regex("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$")
    private val TABLE_SEP = Regex("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$")

    fun parse(text: String): List<MdBlock> = parseLines(text.replace("\r\n", "\n").split('\n'))

    private fun parseLines(lines: List<String>): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            when {
                line.isBlank() -> i++
                FENCE.matches(line.trim()) -> {
                    val m = FENCE.find(line.trim())!!
                    val fence = m.groupValues[1]
                    val body = ArrayList<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(fence)) body += lines[i++]
                    i++ // schließender Zaun (falls vorhanden)
                    out += MdBlock.Code(m.groupValues[2], body.joinToString("\n"))
                }
                HEADING.matches(line) -> {
                    val m = HEADING.find(line)!!
                    out += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2]); i++
                }
                RULE.matches(line) -> { out += MdBlock.Rule; i++ }
                line.trimStart().startsWith(">") -> {
                    val inner = ArrayList<String>()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        inner += lines[i].trimStart().removePrefix(">").removePrefix(" "); i++
                    }
                    out += MdBlock.Quote(parseLines(inner))
                }
                BULLET.matches(line) || ORDERED.matches(line) -> {
                    val ordered = ORDERED.matches(line)
                    val start = if (ordered) ORDERED.find(line)!!.groupValues[1].toInt() else 1
                    val items = ArrayList<MdBlock.ListItem>()
                    while (i < lines.size) {
                        val l = lines[i]
                        val m = (if (ordered) ORDERED.find(l)?.groupValues?.get(2) else BULLET.find(l)?.groupValues?.get(1))
                        when {
                            m != null -> { items += item(m); i++ }
                            l.isNotBlank() && l.startsWith("  ") && items.isNotEmpty() -> {
                                val last = items.removeAt(items.lastIndex)
                                items += last.copy(text = last.text + " " + l.trim()); i++
                            }
                            else -> break
                        }
                    }
                    out += MdBlock.ListBlock(ordered, start, items)
                }
                line.contains('|') && i + 1 < lines.size && TABLE_SEP.matches(lines[i + 1]) -> {
                    val header = cells(line)
                    i += 2
                    val rows = ArrayList<List<String>>()
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) rows += cells(lines[i++])
                    out += MdBlock.Table(header, rows.map { r -> List(header.size) { r.getOrElse(it) { "" } } })
                }
                else -> {
                    val para = ArrayList<String>()
                    while (i < lines.size && lines[i].isNotBlank() && !startsBlock(lines, i)) para += lines[i++].trim()
                    if (para.isEmpty()) { para += line.trim(); i++ }
                    out += MdBlock.Paragraph(para.joinToString(" "))
                }
            }
        }
        return out
    }

    private fun startsBlock(lines: List<String>, i: Int): Boolean {
        val l = lines[i]
        return HEADING.matches(l) || FENCE.matches(l.trim()) || l.trimStart().startsWith(">") || RULE.matches(l) ||
            BULLET.matches(l) || ORDERED.matches(l) || (l.contains('|') && i + 1 < lines.size && TABLE_SEP.matches(lines[i + 1]))
    }

    private fun item(text: String): MdBlock.ListItem {
        val task = TASK.find(text) ?: return MdBlock.ListItem(text, null)
        return MdBlock.ListItem(task.groupValues[2], task.groupValues[1] != " ")
    }

    private fun cells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }
}

/** Teil einer Zeile mit Auszeichnung. */
data class MdSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val strike: Boolean = false,
    val link: String? = null
)

/** Inline-Auszeichnungen: `**fett**`, `*kursiv*`/`_kursiv_`, `` `code` ``, `~~durch~~`, `[text](url)`, `<url>`. */
object InlineMarkdown {
    fun parse(text: String): List<MdSpan> {
        val out = ArrayList<MdSpan>()
        val buf = StringBuilder()
        var bold = false
        var italic = false
        var strike = false
        fun flush() { if (buf.isNotEmpty()) { out += MdSpan(buf.toString(), bold, italic, strike = strike); buf.clear() } }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length -> { buf.append(text[i + 1]); i += 2 }
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end < 0) { buf.append(c); i++ } else {
                        flush(); out += MdSpan(text.substring(i + 1, end), code = true); i = end + 1
                    }
                }
                text.startsWith("**", i) || text.startsWith("__", i) -> { flush(); bold = !bold; i += 2 }
                text.startsWith("~~", i) -> { flush(); strike = !strike; i += 2 }
                (c == '*' || c == '_') && !(c == '_' && i > 0 && text[i - 1].isLetterOrDigit()) -> { flush(); italic = !italic; i++ }
                c == '[' -> {
                    val close = text.indexOf("](", i)
                    val end = if (close > 0) text.indexOf(')', close) else -1
                    if (close > 0 && end > 0) {
                        flush()
                        out += MdSpan(text.substring(i + 1, close), bold, italic, link = text.substring(close + 2, end).trim())
                        i = end + 1
                    } else { buf.append(c); i++ }
                }
                c == '<' && (text.startsWith("<http://", i) || text.startsWith("<https://", i)) -> {
                    val end = text.indexOf('>', i)
                    if (end < 0) { buf.append(c); i++ } else {
                        flush(); val url = text.substring(i + 1, end); out += MdSpan(url, link = url); i = end + 1
                    }
                }
                else -> { buf.append(c); i++ }
            }
        }
        flush()
        return out
    }
}
