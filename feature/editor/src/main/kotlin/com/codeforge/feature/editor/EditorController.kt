/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Imperativer Zugriff auf eine lebende [CodeEditor]-Instanz für Aktionen, die nicht über
 * den MVI-UiState modelliert werden (Cursor-/Selektions-/Clipboard-Operationen sind
 * View-lokaler, transienter UI-Zustand — kein Domain-/App-State). Wird aus [EditorScreen]
 * heraus an Toolbar-Buttons gebunden; [SoraCodeEditor] registriert die Instanz beim
 * `AndroidView`-factory-Callback und deregistriert sie in `onRelease`.
 */
package com.codeforge.feature.editor

import com.codeforge.core.domain.model.LspPosition
import com.codeforge.core.domain.model.LspRange
import com.codeforge.feature.editor.ui.HoverPalette
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import com.codeforge.libs.code_tools.edit.TextEdit
import com.codeforge.libs.code_tools.imports.KotlinImportOrganizer
import io.github.rosemoe.sora.widget.CodeEditor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.ref.WeakReference

/** Steuert das Sora-`CodeEditor`-Widget von außen (Rückgängig, Zwischenablage, Sprünge, Auswahl). */
class EditorController {

    private var editorRef: WeakReference<CodeEditor>? = null

    /** Compose-beobachtbar, damit die Toolbar-Buttons nach jeder Änderung neu bewertet werden. */
    var canUndoState by mutableStateOf(false)
        private set
    /** Zustand für „Wiederholen“ (Compose-beobachtbar). */
    var canRedoState by mutableStateOf(false)
        private set

    /** Verbindet den Controller mit dem Editor. */
    internal fun attach(editor: CodeEditor) {
        editorRef = WeakReference(editor)
        refreshUndoRedo()
    }

    /** Nach Text-/Undo-/Redo-Änderungen aufrufen. */
    internal fun refreshUndoRedo() {
        val editor = editorRef?.get()
        canUndoState = editor?.canUndo() ?: false
        canRedoState = editor?.canRedo() ?: false
    }

    /** Löst die Verbindung. */
    internal fun detach() {
        editorRef?.clear()
        editorRef = null
    }

    private inline fun withEditor(block: (CodeEditor) -> Unit) {
        editorRef?.get()?.let(block)
    }

    // --- Undo / Redo (sora-editors Content-Klasse führt den Undo-Stack intern unbegrenzt) ---
    /** Macht die letzte Änderung rückgängig. */
    fun undo() { withEditor { if (it.canUndo()) it.undo() }; refreshUndoRedo() }
    /** Stellt die letzte rückgängig gemachte Änderung wieder her. */
    fun redo() { withEditor { if (it.canRedo()) it.redo() }; refreshUndoRedo() }
    /** Ob Rückgängig möglich ist. */
    fun canUndo(): Boolean = editorRef?.get()?.canUndo() ?: false
    /** Ob Wiederholen möglich ist. */
    fun canRedo(): Boolean = editorRef?.get()?.canRedo() ?: false

    // --- Selektion / Clipboard ---
    /** Wählt den gesamten Text. */
    fun selectAll() = withEditor { it.selectAll() }
    /** Schneidet die Auswahl aus. */
    fun cut() = withEditor { it.copyText(); it.deleteText() }
    /** Kopiert die Auswahl. */
    fun copy() = withEditor { it.copyText() }
    /** Fügt die Zwischenablage ein. */
    fun paste() = withEditor { it.pasteText() }

    /** Fügt [text] an der aktuellen Cursor-Position ein (bzw. ersetzt die aktive Auswahl). */
    fun insertText(text: String) = withEditor { editor ->
        val cursor = editor.cursor
        val s = cursor.left
        val e = cursor.right
        editor.text.beginBatchEdit()
        try {
            replaceRange(editor, s, e, text)
        } finally {
            editor.text.endBatchEdit()
        }
        val newPos = s + text.length
        selectRange(newPos, newPos)
    }

    // --- Navigation ---
    /** Springt zur Zeile. */
    fun jumpToLine(line: Int) = withEditor { editor ->
        val target = line.coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
        editor.setSelection(target, 0)
        editor.ensurePositionVisible(target, 0)
    }

    /** Springt zu Zeile und Spalte. */
    fun jumpToPosition(line: Int, column: Int) = withEditor { editor ->
        val targetLine = line.coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
        val lineLength = editor.text.getColumnCount(targetLine)
        val targetColumn = column.coerceIn(0, lineLength)
        editor.setSelection(targetLine, targetColumn)
        editor.ensurePositionVisible(targetLine, targetColumn)
    }

    /** Liefert 0-basierte (Zeile, Spalte) der aktuellen Cursorposition. */
    fun getCursorLineAndColumn(): Pair<Int, Int>? = editorRef?.get()?.let { editor ->
        val pos = editor.text.indexer.getCharPosition(editor.cursor.left)
        pos.line to pos.column
    }

    /** Liefert das Wort unter dem Cursor (für Rename/Definition/References). */
    fun getWordAtCursor(): String? = editorRef?.get()?.let { editor ->
        val text = editor.text.toString()
        val cursor = editor.cursor.left
        if (cursor !in 0..text.length) return null
        var start = cursor
        while (start > 0 && (text[start - 1].isLetterOrDigit() || text[start - 1] == '_')) {
            start--
        }
        var end = cursor
        while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) {
            end++
        }
        if (start < end) text.substring(start, end) else null
    }

    /** Springt zu 0-basierter [line]/[column] und markiert [length] Zeichen (Projektsuche-Treffer). */
    fun jumpToMatch(line: Int, column: Int, length: Int) = withEditor { editor ->
        val l = line.coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
        val c = column.coerceIn(0, editor.text.getColumnCount(l))
        val end = (c + length).coerceAtMost(editor.text.getColumnCount(l))
        editor.setSelectionRegion(l, c, l, end)
        editor.ensurePositionVisible(l, c)
    }

    // --- Text-Zugriff / Selektion über Zeichen-Offsets (Grundlage der eigenen Such-/Format-Engine
    // aus :libs:code-tools; sora-Spalten/Zeilen werden über den Indexer umgerechnet) ---
    /** Aktueller Text oder `null`. */
    fun currentText(): String? = editorRef?.get()?.text?.toString()

    /** `(start, end)` der Auswahl als Offsets; bei reinem Cursor `start == end`. */
    fun selection(): Pair<Int, Int>? = editorRef?.get()?.cursor?.let { it.left to it.right }

    /** Ob Text ausgewählt ist. */
    fun hasSelection(): Boolean = editorRef?.get()?.cursor?.let { it.left != it.right } ?: false

    /** Liefert die aktive Auswahl als [LspRange] (0-basiert) oder null, wenn keine Auswahl vorliegt. */
    fun getSelectionLspRange(): LspRange? = editorRef?.get()?.let { editor ->
        val cursor = editor.cursor
        if (cursor.left == cursor.right) return null
        val startOffset = minOf(cursor.left, cursor.right)
        val endOffset = maxOf(cursor.left, cursor.right)
        val startPos = editor.text.indexer.getCharPosition(startOffset)
        val endPos = editor.text.indexer.getCharPosition(endOffset)
        LspRange(
            start = LspPosition(startPos.line, startPos.column),
            end = LspPosition(endPos.line, endPos.column)
        )
    }

    /** Wählt den Bereich [start, end). */
    fun selectRange(start: Int, end: Int) = withEditor { editor ->
        val len = editor.text.length
        val s = start.coerceIn(0, len)
        val e = end.coerceIn(s, len)
        val a = editor.text.indexer.getCharPosition(s)
        val b = editor.text.indexer.getCharPosition(e)
        editor.setSelectionRegion(a.line, a.column, b.line, b.column)
        editor.ensurePositionVisible(a.line, a.column)
    }

    /** Ersetzt `[start, end)` als EIN Undo-Schritt. */
    private fun replaceRange(editor: CodeEditor, start: Int, end: Int, replacement: String) {
        val a = editor.text.indexer.getCharPosition(start)
        val b = editor.text.indexer.getCharPosition(end)
        editor.text.replace(a.line, a.column, b.line, b.column, replacement)
    }

    /** Wendet einen [TextEdit] aus :libs:code-tools an (Undo-fähig) und setzt danach die Auswahl. */
    fun applyEdit(edit: TextEdit) = withEditor { editor ->
        editor.text.beginBatchEdit()
        try {
            replaceRange(editor, edit.replaceStart, edit.replaceEnd, edit.replacement)
        } finally {
            editor.text.endBatchEdit()
        }
        selectRange(edit.selStart, edit.selEnd)
    }

    /**
     * Ersetzt den gesamten Text durch [newText], tastet aber nur den geänderten Mittelteil an
     * (gemeinsames Präfix/Suffix bleibt) — Cursor, Scrollposition und Undo-Stack bleiben sinnvoll erhalten.
     */
    fun applyTextChange(newText: String) = withEditor { editor ->
        val old = editor.text.toString()
        if (old == newText) return@withEditor
        val (cursorStart, cursorEnd) = editor.cursor.left to editor.cursor.right
        var prefix = 0
        val maxPrefix = minOf(old.length, newText.length)
        while (prefix < maxPrefix && old[prefix] == newText[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(old.length, newText.length) - prefix
        while (suffix < maxSuffix && old[old.length - 1 - suffix] == newText[newText.length - 1 - suffix]) suffix++
        editor.text.beginBatchEdit()
        try {
            replaceRange(editor, prefix, old.length - suffix, newText.substring(prefix, newText.length - suffix))
        } finally {
            editor.text.endBatchEdit()
        }
        val newCursorStart = mapCursorOffset(cursorStart, old.length, prefix, suffix, newText.length)
        val newCursorEnd = mapCursorOffset(cursorEnd, old.length, prefix, suffix, newText.length)
        selectRange(newCursorStart, newCursorEnd)
    }

    /**
     * Sortiert/dedupliziert (und entfernt optional unbenutzte) Kotlin-Imports sofort im Editor.
     * @return `true`, wenn sich der Text geändert hat.
     */
    fun organizeImports(removeUnused: Boolean): Boolean {
        val text = currentText() ?: return false
        val organized = KotlinImportOrganizer.organize(text, removeUnused) ?: return false
        applyTextChange(organized)
        return true
    }

    /** Farben des aktiven Editor-Themes für Hover-Anzeigen. */
    fun hoverPalette(): HoverPalette? = editorRef?.get()?.colorScheme?.let { cs ->
        fun c(id: Int) = androidx.compose.ui.graphics.Color(cs.getColor(id))
        HoverPalette(
            background = c(EditorColorScheme.WHOLE_BACKGROUND),
            text = c(EditorColorScheme.TEXT_NORMAL),
            keyword = c(EditorColorScheme.KEYWORD),
            function = c(EditorColorScheme.FUNCTION_NAME),
            type = c(EditorColorScheme.IDENTIFIER_NAME),
            literal = c(EditorColorScheme.LITERAL),
            comment = c(EditorColorScheme.COMMENT),
        )
    }

    /** Führt einen reinen Zeilenbefehl (Kommentar, Einrücken, Duplizieren …) auf der aktuellen Auswahl aus. */
    fun runLineCommand(command: (text: String, selStart: Int, selEnd: Int) -> TextEdit?) {
        val text = currentText() ?: return
        val (s, e) = selection() ?: return
        command(text, s, e)?.let(::applyEdit)
    }

    // --- Darstellung (zusätzlich zu den persistierten EditorConfig-Settings: spontane
    // Toggles für die laufende Sitzung, ohne DataStore-Roundtrip) ---
    /** Schaltet den Zeilenumbruch um. */
    fun toggleWordWrap() = withEditor { it.isWordwrap = !it.isWordwrap }

    /** Textgröße in sp — Persistenz erfolgt separat über EditorConfig (:feature:settings). */
    fun setTextSizeSp(sizeSp: Float) = withEditor { it.setTextSize(sizeSp.coerceIn(6f, 48f)) }
    /** Aktuelle Schriftgröße in sp. */
    fun currentTextSizeSp(): Float = editorRef?.get()?.textSizePx?.let { px ->
        px / (editorRef?.get()?.resources?.displayMetrics?.scaledDensity ?: 1f)
    } ?: 14f

}

/**
 * Berechnet die neue Cursorposition nach einer Differenz-Anwendung ([prefix], [suffix]).
 * - Cursor vor der Änderung: Position bleibt identisch.
 * - Cursor nach der Änderung: Position verschiebt sich um die Textlängen-Differenz.
 * - Cursor innerhalb der Änderung: Lineare Interpolation im geänderten Mittelbereich.
 */
internal fun mapCursorOffset(
    oldCursor: Int,
    oldLen: Int,
    prefix: Int,
    suffix: Int,
    newLen: Int
): Int {
    if (oldCursor <= prefix) return oldCursor
    val oldMiddleEnd = oldLen - suffix
    if (oldCursor >= oldMiddleEnd) {
        val delta = newLen - oldLen
        return (oldCursor + delta).coerceIn(0, newLen)
    }
    val oldMiddleLen = oldMiddleEnd - prefix
    val newMiddleLen = (newLen - suffix) - prefix
    if (oldMiddleLen <= 0) return (prefix + newMiddleLen).coerceIn(0, newLen)
    val ratio = (oldCursor - prefix).toDouble() / oldMiddleLen
    val mapped = prefix + (ratio * newMiddleLen).toInt()
    return mapped.coerceIn(0, newLen)
}
