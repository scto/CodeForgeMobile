/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * „Beim Einfügen formatieren“: passt die Einrückung mehrzeilig eingefügten Codes an die Zielzeile an
 * und behält die relative Einrückung der Zeilen untereinander. Als sora-[Formatter] eingehängt, den
 * sora nach `pasteText()` mit dem eingefügten Bereich aufruft (`props.formatPastedText`).
 */
package com.codeforge.feature.editor

import io.github.rosemoe.sora.lang.format.Formatter
import io.github.rosemoe.sora.lang.format.FormatterProvider
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.TextRange
import io.github.rosemoe.sora.widget.CodeEditor
import java.lang.ref.WeakReference

/** Reine Textlogik der Neu-Einrückung. */
object PasteReindent {

    /**
     * Neu eingerückter Einfügetext.
     *
     * @param pasted eingefügter Text
     * @param linePrefix Text der Zielzeile vor der Einfügeposition
     * @param tabSize Breite eines Tabs beim Messen der Einrückung
     * @return angepasster Text; unverändert bei einzeiligem Text
     */
    fun reindent(pasted: String, linePrefix: String, tabSize: Int): String {
        val lines = pasted.split('\n')
        if (lines.size < 2) return pasted
        val prefixBlank = linePrefix.isBlank()
        val target = linePrefix.takeWhile { it == ' ' || it == '\t' }
        val measured = lines.withIndex()
            .filter { (i, line) -> line.isNotBlank() && (i > 0 || prefixBlank) }
            .map { width(leading(it.value), tabSize) }
        val common = measured.minOrNull() ?: 0
        return lines.mapIndexed { i, line ->
            when {
                i == 0 -> if (prefixBlank) line.trimStart(' ', '\t') else line
                line.isBlank() -> ""
                else -> target + " ".repeat((width(leading(line), tabSize) - common).coerceAtLeast(0)) + line.trimStart(' ', '\t')
            }
        }.joinToString("\n")
    }

    private fun leading(line: String) = line.takeWhile { it == ' ' || it == '\t' }

    private fun width(indent: String, tabSize: Int): Int =
        indent.fold(0) { acc, c -> if (c == '\t') (acc / tabSize + 1) * tabSize else acc + 1 }
}

/**
 * sora-Formatter, der nur Bereiche (eingefügten Text) neu einrückt und das direkt im Editor ersetzt.
 * Ganze Dokumente formatiert weiterhin der IDE-Formatierer (Befehl „Code formatieren“).
 */
internal class PasteReindentFormatter(editor: CodeEditor, private val tabSize: () -> Int) : Formatter {

    private val editorRef = WeakReference(editor)

    override fun format(text: Content, cursorRange: TextRange) = Unit

    override fun formatRegion(text: Content, rangeToFormat: TextRange, cursorRange: TextRange) {
        val start = rangeToFormat.start
        val end = rangeToFormat.end
        if (start.line == end.line) return
        val pasted = text.subContent(start.line, start.column, end.line, end.column).toString()
        val prefix = text.getLine(start.line).substring(0, start.column)
        val result = PasteReindent.reindent(pasted, prefix, tabSize().coerceAtLeast(1))
        if (result == pasted) return
        val editor = editorRef.get() ?: return
        editor.post {
            val live = editor.text
            // Nur ersetzen, wenn der eingefügte Text noch unverändert dort steht.
            if (end.line >= live.lineCount || end.column > live.getColumnCount(end.line)) return@post
            if (live.subContent(start.line, start.column, end.line, end.column).toString() != pasted) return@post
            live.replace(start.line, start.column, end.line, end.column, result)
        }
    }

    override fun setReceiver(receiver: Formatter.FormatResultReceiver?) = Unit

    override fun isRunning(): Boolean = false

    override fun destroy() = Unit

    /** Provider zum Einhängen per `CodeEditor.setFormatterProvider`. */
    companion object {
        fun provider(tabSize: () -> Int): FormatterProvider = FormatterProvider { editor -> PasteReindentFormatter(editor, tabSize) }
    }
}
