// Modul: :feature:editor
package com.codeforge.feature.editor.folding

/**
 * Faltbereiche aus reinem Text (ohne Android, testbar). Ein Bereich `a..b` blendet die Zeilen `a+1..b` aus;
 * die erste Zeile bleibt sichtbar. Alle Zeilennummern 0-basiert.
 */
object FoldingRules {
    private const val MIN_COMMENT_RUN = 3
    private const val MAX_HEADING = 6

    /** Kommentar- und String-Syntax einer Sprachfamilie für die Klammersuche. */
    data class Syntax(
        val lineComment: String? = "//",
        val blockComment: Pair<String, String>? = "/*" to "*/",
        val tripleQuotes: Boolean = false,
    )

    /**
     * `{…}`- und `[…]`-Blöcke über mehrere Zeilen. Steht die schließende Klammer am Zeilenanfang, bleibt ihre Zeile
     * sichtbar. Klammern in Strings und Kommentaren zählen nicht.
     */
    fun brackets(lines: List<CharSequence>, syntax: Syntax = Syntax()): List<IntRange> {
        val out = ArrayList<IntRange>()
        val stack = ArrayDeque<Int>()
        val lexer = CodeLexer(syntax)
        lines.forEachIndexed { lineNo, line ->
            lexer.scan(line) { index, c ->
                if (c == '{' || c == '[') {
                    stack.addLast(lineNo)
                } else if (c == '}' || c == ']') {
                    stack.removeLastOrNull()?.let { start -> addBlock(out, start, lineNo, line, index) }
                }
            }
        }
        return out.distinct()
    }

    /** Mehrzeilige Blockkommentare (`/* … */`, `<!-- … -->`). */
    fun blockComments(lines: List<CharSequence>, open: String, close: String): List<IntRange> {
        val out = ArrayList<IntRange>()
        val text = lines.joinToString("\n")
        val index = LineIndex(lines)
        var from = text.indexOf(open)
        while (from >= 0) {
            val end = text.indexOf(close, from + open.length)
            val endOffset = if (end < 0) text.length else end
            val range = index.lineOf(from)..index.lineOf(endOffset)
            if (end >= 0 && range.last > range.first) out += range
            from = if (end < 0) -1 else text.indexOf(open, end + close.length)
        }
        return out
    }

    /** Mindestens [minRun] aufeinanderfolgende Zeilenkommentare (z. B. `//` oder `#`). */
    fun commentRuns(lines: List<CharSequence>, prefixes: List<String>, minRun: Int = MIN_COMMENT_RUN): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = -1
        for (i in 0..lines.size) {
            val isComment = i < lines.size && prefixes.any { lines[i].trimStart().startsWith(it) }
            if (isComment && start < 0) start = i
            if (!isComment && start >= 0) {
                if (i - start >= minRun) out += start..<i
                start = -1
            }
        }
        return out
    }

    /** Aufeinanderfolgende Import-Zeilen (Leerzeilen dazwischen erlaubt). */
    fun imports(lines: List<CharSequence>, prefixes: List<String>): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = -1
        var last = -1
        for (i in 0..lines.size) {
            val line = lines.getOrNull(i)?.trimStart()
            val isImport = line != null && prefixes.any { line.startsWith(it) }
            val endsRun = line == null || (!isImport && line.isNotBlank())
            if (isImport) {
                if (start < 0) start = i
                last = i
            } else if (endsRun && start >= 0) {
                if (last > start) out += start..last
                start = -1
            }
        }
        return out
    }

    /** Einrückungsblöcke (Python, YAML …): Zeile plus alle folgenden, tiefer eingerückten Zeilen. */
    fun indentation(lines: List<CharSequence>, tabSize: Int = 4): List<IntRange> {
        val indents = lines.map { if (it.isBlank()) -1 else indentOf(it, tabSize) }
        val out = ArrayList<IntRange>()
        for (i in indents.indices) {
            val base = indents[i]
            if (base < 0) continue
            var end = i
            var j = i + 1
            while (j < indents.size && (indents[j] < 0 || indents[j] > base)) {
                if (indents[j] >= 0) end = j
                j++
            }
            if (end > i) out += i..end
        }
        return out
    }

    /** XML/HTML: Elemente über mehrere Zeilen und mehrzeilige Start-Tags (viele Attribute). */
    fun xmlElements(lines: List<CharSequence>): List<IntRange> = XmlFolding(lines).regions()

    /** Markdown: Abschnitte von einer Überschrift bis vor die nächste gleicher oder höherer Ebene. */
    fun markdownSections(lines: List<CharSequence>): List<IntRange> {
        val headings = ArrayList<Pair<Int, Int>>() // Zeile, Ebene
        var inFence = false
        lines.forEachIndexed { i, raw ->
            val line = raw.toString()
            if (line.trimStart().startsWith("```")) inFence = !inFence
            val level = line.takeWhile { it == '#' }.length
            if (!inFence && level in 1..MAX_HEADING && line.getOrNull(level) == ' ') headings += i to level
        }
        val out = ArrayList<IntRange>()
        headings.forEachIndexed { k, (line, level) ->
            val next = headings.drop(k + 1).firstOrNull { it.second <= level }?.first ?: lines.size
            var end = next - 1
            while (end > line && lines[end].isBlank()) end--
            if (end > line) out += line..end
        }
        return out
    }

    /** Bereich `start..` bis zur Zeile der schließenden Klammer; steht sie am Zeilenanfang, bleibt die Zeile sichtbar. */
    internal fun addBlock(out: MutableList<IntRange>, start: Int, closeLine: Int, line: CharSequence, closeIndex: Int) {
        val end = if (firstNonBlank(line) == closeIndex) closeLine - 1 else closeLine
        if (end > start) out += start..end
    }

    internal fun firstNonBlank(line: CharSequence): Int = line.indexOfFirst { !it.isWhitespace() }

    private fun indentOf(line: CharSequence, tabSize: Int): Int {
        var n = 0
        for (c in line) {
            if (c != ' ' && c != '\t') return n
            n += if (c == '\t') tabSize else 1
        }
        return n
    }
}

/** Zeilennummer zu einem Zeichen-Offset im mit `\n` verbundenen Text. */
internal class LineIndex(lines: List<CharSequence>) {
    private val starts = IntArray(lines.size).also { starts ->
        var pos = 0
        lines.forEachIndexed { i, l ->
            starts[i] = pos
            pos += l.length + 1
        }
    }

    /** Zeile des Offsets [offset]. */
    fun lineOf(offset: Int): Int = starts.binarySearch(offset).let { if (it >= 0) it else -it - 2 }

    /** Spalte des Offsets [offset] in Zeile [line]. */
    fun columnOf(offset: Int, line: Int): Int = offset - starts[line]
}

/** Zeichenweise Suche außerhalb von Strings und Kommentaren; Zustand bleibt über Zeilen erhalten. */
internal class CodeLexer(private val syntax: FoldingRules.Syntax) {
    private var inBlock = false
    private var inTriple = false
    private var quote: Char? = null

    /** Ruft [onCode] für jedes Zeichen außerhalb von Strings und Kommentaren auf. */
    fun scan(line: CharSequence, onCode: (Int, Char) -> Unit) {
        quote = null
        var i = 0
        while (i < line.length) i = step(line, i, onCode)
    }

    // Verarbeitet die Stelle [i] und liefert die nächste.
    private fun step(line: CharSequence, i: Int, onCode: (Int, Char) -> Unit): Int {
        val c = line[i]
        val block = syntax.blockComment
        val lineComment = syntax.lineComment
        return when {
            inBlock -> if (block != null && line.startsWith(block.second, i)) end(block.second.length, i) { inBlock = false } else i + 1
            inTriple -> if (line.startsWith(TRIPLE, i)) end(TRIPLE.length, i) { inTriple = false } else i + 1
            quote != null -> inQuote(c, i)
            lineComment != null && line.startsWith(lineComment, i) -> line.length
            block != null && line.startsWith(block.first, i) -> end(block.first.length, i) { inBlock = true }
            syntax.tripleQuotes && line.startsWith(TRIPLE, i) -> end(TRIPLE.length, i) { inTriple = true }
            c == '"' || (c == '\'' && isCharLiteral(line, i)) -> end(1, i) { quote = c }
            else -> end(1, i) { onCode(i, c) }
        }
    }

    private fun inQuote(c: Char, i: Int): Int = when (c) {
        '\\' -> i + 2
        quote -> end(1, i) { quote = null }
        else -> i + 1
    }

    private inline fun end(length: Int, i: Int, action: () -> Unit): Int {
        action()
        return i + length
    }

    // Zeichenliteral nur, wenn es kurz darauf endet (sonst z. B. Rust-Lifetime 'a).
    private fun isCharLiteral(line: CharSequence, i: Int): Boolean =
        line.indexOf('\'', i + 1) in i + 2..i + CHAR_LITERAL_MAX

    private companion object {
        const val TRIPLE = "\"\"\""
        const val CHAR_LITERAL_MAX = 8
    }
}

/** XML/HTML-Faltung: Elemente (Start- bis End-Tag) und mehrzeilige Tags. */
internal class XmlFolding(private val lines: List<CharSequence>) {
    private val text = lines.joinToString("\n")
    private val index = LineIndex(lines)
    private val out = ArrayList<IntRange>()
    private val stack = ArrayDeque<Pair<String, Int>>()

    /** Alle Bereiche. */
    fun regions(): List<IntRange> {
        var i = text.indexOf('<')
        while (i >= 0) {
            val next = skipSpecial(i) ?: handleTag(i)
            i = if (next < 0 || next >= text.length) -1 else text.indexOf('<', next)
        }
        return out.distinct()
    }

    // Kommentare, CDATA und Deklarationen überspringen; `null`, wenn an [lt] ein normales Tag steht.
    private fun skipSpecial(lt: Int): Int? {
        val (open, close) = SPECIAL.firstOrNull { text.startsWith(it.first, lt) } ?: return null
        val end = text.indexOf(close, lt + open.length)
        return if (end < 0) -1 else end + close.length
    }

    private fun handleTag(lt: Int): Int {
        val gt = tagEnd(lt)
        if (gt < 0) return -1
        val startLine = index.lineOf(lt)
        val endLine = index.lineOf(gt)
        val closing = text.getOrNull(lt + 1) == '/'
        val name = text.substring(if (closing) lt + 2 else lt + 1, gt).takeWhile { !it.isWhitespace() && it != '/' }
        when {
            closing -> closeElement(name, lt, startLine)
            text[gt - 1] == '/' -> if (endLine > startLine) out += startLine..endLine
            else -> openElement(name, startLine, endLine)
        }
        return gt + 1
    }

    private fun openElement(name: String, startLine: Int, endLine: Int) {
        // Mehrzeiliges Start-Tag: Attribute einklappbar, die Zeile mit `>` bleibt sichtbar.
        if (endLine - 1 > startLine) out += startLine..<endLine
        if (name.isNotEmpty()) stack.addLast(name to startLine)
    }

    private fun closeElement(name: String, lt: Int, line: Int) {
        val idx = stack.indexOfLast { it.first == name }
        if (idx < 0) return
        val open = stack[idx]
        while (stack.size > idx) stack.removeLast()
        FoldingRules.addBlock(out, open.second, line, lines[line], index.columnOf(lt, line))
    }

    private fun tagEnd(from: Int): Int {
        var quote: Char? = null
        for (k in from + 1 until text.length) {
            val c = text[k]
            if (quote != null) {
                if (c == quote) quote = null
            } else if (c == '"' || c == '\'') {
                quote = c
            } else if (c == '>') {
                return k
            }
        }
        return -1
    }

    private companion object {
        val SPECIAL = listOf("<!--" to "-->", "<![CDATA[" to "]]>", "<?" to ">", "<!" to ">")
    }
}
