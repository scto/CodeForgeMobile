package com.codeforge.libs.code_tools.format

/**
 * XML-Pretty-Printer, konservativ: Elemente mit Textinhalt (auch gemischt) bleiben exakt wie sie sind,
 * damit z. B. `strings.xml`-Werte nie verändert werden. Mehrzeilige Start-Tags (Android-Attribute je Zeile)
 * werden neu eingerückt, Kommentare/CDATA/PIs unverändert übernommen.
 */
internal object XmlFormatter {

    private sealed interface Node
    private class Text(val raw: String) : Node { val blank get() = raw.isBlank() }
    private class Leaf(val raw: String) : Node // Kommentar, CDATA, PI, Doctype, selbstschließendes Tag
    private class Element(val open: String, val children: MutableList<Node> = ArrayList(), var close: String = "", val start: Int, var end: Int = 0) : Node

    fun format(text: String, options: FormatOptions): FormatResult? {
        val root = parse(text) ?: return null
        val out = ArrayList<String>()
        render(text, root.children, 0, options, out)
        val result = CodeFormatter.finish(out.map { if (options.trimTrailingWhitespace) it.trimEnd() else it }, options)
        return FormatResult(result)
    }

    private fun parse(text: String): Element? {
        val root = Element("", start = 0)
        val stack = arrayListOf(root)
        var i = 0
        while (i < text.length) {
            if (text[i] != '<') {
                val end = text.indexOf('<', i).let { if (it < 0) text.length else it }
                stack.last().children += Text(text.substring(i, end))
                i = end
                continue
            }
            when {
                text.startsWith("<!--", i) -> { val e = text.indexOf("-->", i); if (e < 0) return null; stack.last().children += Leaf(text.substring(i, e + 3)); i = e + 3 }
                text.startsWith("<![CDATA[", i) -> { val e = text.indexOf("]]>", i); if (e < 0) return null; stack.last().children += Leaf(text.substring(i, e + 3)); i = e + 3 }
                text.startsWith("<?", i) -> { val e = text.indexOf("?>", i); if (e < 0) return null; stack.last().children += Leaf(text.substring(i, e + 2)); i = e + 2 }
                text.startsWith("<!", i) -> { val e = tagEnd(text, i); if (e < 0) return null; stack.last().children += Leaf(text.substring(i, e + 1)); i = e + 1 }
                else -> {
                    val e = tagEnd(text, i)
                    if (e < 0) return null
                    val tag = text.substring(i, e + 1)
                    when {
                        tag.startsWith("</") -> {
                            if (stack.size == 1) return null
                            val el = stack.removeAt(stack.size - 1)
                            val name = tag.substring(2, tag.length - 1).trim()
                            if (!el.open.removePrefix("<").trimStart().startsWith(name)) return null
                            el.close = tag
                            el.end = e + 1
                            stack.last().children += el
                        }
                        tag.endsWith("/>") -> stack.last().children += Leaf(tag)
                        else -> stack += Element(tag, start = i)
                    }
                    i = e + 1
                }
            }
        }
        return if (stack.size == 1) root else null
    }

    /** Index des schließenden `>` eines Tags, Anführungszeichen in Attributwerten beachtend. */
    private fun tagEnd(text: String, start: Int): Int {
        var quote = 0.toChar()
        var i = start + 1
        while (i < text.length) {
            val c = text[i]
            if (quote != 0.toChar()) { if (c == quote) quote = 0.toChar() }
            else if (c == '"' || c == '\'') quote = c
            else if (c == '>') return i
            i++
        }
        return -1
    }

    private fun render(src: String, nodes: List<Node>, depth: Int, options: FormatOptions, out: MutableList<String>) {
        val indent = CodeFormatter.indentString(depth, options)
        var pendingBlank = false
        var first = true
        for (node in nodes) {
            when (node) {
                is Text -> if (node.blank) { if (node.raw.count { it == '\n' } >= 2 && !first) pendingBlank = true }
                is Leaf -> { emitBlank(out, pendingBlank); pendingBlank = false; first = false; multiline(node.raw, indent, depth, options, out) }
                is Element -> {
                    emitBlank(out, pendingBlank); pendingBlank = false; first = false
                    val hasText = node.children.any { it is Text && !it.blank }
                    if (hasText) {
                        // Textinhalt: Originalausschnitt unverändert (nur erste Zeile einrücken)
                        val raw = src.substring(node.start, node.end)
                        out += indent + raw.lines().first().trimStart()
                        raw.lines().drop(1).forEach { out += it }
                    } else if (node.children.all { it is Text }) {
                        multiline(node.open, indent, depth, options, out)
                        // leeres Element: Schluss-Tag auf eigene Zeile vermeiden → zusammenziehen
                        val last = out.removeAt(out.size - 1)
                        out += last + node.close
                    } else {
                        multiline(node.open, indent, depth, options, out)
                        render(src, node.children, depth + 1, options, out)
                        out += indent + node.close
                    }
                }
            }
        }
    }

    private fun emitBlank(out: MutableList<String>, pending: Boolean) { if (pending && out.isNotEmpty() && out.last().isNotEmpty()) out += "" }

    private fun multiline(raw: String, indent: String, depth: Int, options: FormatOptions, out: MutableList<String>) {
        val lines = raw.lines()
        out += indent + lines.first().trimStart()
        val inner = CodeFormatter.indentString(depth + 1, options)
        // Mehrzeilige Kommentare/CDATA nicht anfassen; Tags: Attributzeilen neu einrücken
        val isTag = raw.startsWith("<") && !raw.startsWith("<!--") && !raw.startsWith("<![CDATA[")
        lines.drop(1).forEach { out += if (isTag) inner + it.trim() else it }
    }
}
