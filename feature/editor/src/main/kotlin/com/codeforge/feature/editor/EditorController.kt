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

import com.codeforge.libs.code_tools.edit.TextEdit
import io.github.rosemoe.sora.widget.CodeEditor
import java.lang.ref.WeakReference

class EditorController {

    private var editorRef: WeakReference<CodeEditor>? = null

    internal fun attach(editor: CodeEditor) {
        editorRef = WeakReference(editor)
    }

    internal fun detach() {
        editorRef?.clear()
        editorRef = null
    }

    private inline fun withEditor(block: (CodeEditor) -> Unit) {
        editorRef?.get()?.let(block)
    }

    // --- Undo / Redo (sora-editors Content-Klasse führt den Undo-Stack intern unbegrenzt) ---
    fun undo() = withEditor { if (it.canUndo()) it.undo() }
    fun redo() = withEditor { if (it.canRedo()) it.redo() }
    fun canUndo(): Boolean = editorRef?.get()?.canUndo() ?: false
    fun canRedo(): Boolean = editorRef?.get()?.canRedo() ?: false

    // --- Selektion / Clipboard ---
    fun selectAll() = withEditor { it.selectAll() }
    fun cut() = withEditor { it.copyText(); it.deleteText() }
    fun copy() = withEditor { it.copyText() }
    fun paste() = withEditor { it.pasteText() }

    // --- Navigation ---
    fun jumpToLine(line: Int) = withEditor { editor ->
        val target = line.coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
        editor.setSelection(target, 0)
        editor.ensurePositionVisible(target, 0)
    }

    fun jumpToPosition(line: Int, column: Int) = withEditor { editor ->
        val targetLine = line.coerceIn(0, (editor.text.lineCount - 1).coerceAtLeast(0))
        val lineLength = editor.text.getColumnCount(targetLine)
        val targetColumn = column.coerceIn(0, lineLength)
        editor.setSelection(targetLine, targetColumn)
        editor.ensurePositionVisible(targetLine, targetColumn)
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
    fun currentText(): String? = editorRef?.get()?.text?.toString()

    /** `(start, end)` der Auswahl als Offsets; bei reinem Cursor `start == end`. */
    fun selection(): Pair<Int, Int>? = editorRef?.get()?.cursor?.let { it.left to it.right }

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
     * (gemeinsames Präfix/Suffix bleibt) — Cursor, Scrollposition und Undo-Stack bleiben sinnvoll.
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
        selectRange(cursorStart.coerceAtMost(newText.length), cursorEnd.coerceAtMost(newText.length))
    }

    /** Führt einen reinen Zeilenbefehl (Kommentar, Einrücken, Duplizieren …) auf der aktuellen Auswahl aus. */
    fun runLineCommand(command: (text: String, selStart: Int, selEnd: Int) -> TextEdit?) {
        val text = currentText() ?: return
        val (s, e) = selection() ?: return
        command(text, s, e)?.let(::applyEdit)
    }

    // --- Darstellung (zusätzlich zu den persistierten EditorConfig-Settings: spontane
    // Toggles für die laufende Sitzung, ohne DataStore-Roundtrip) ---
    fun toggleWordWrap() = withEditor { it.isWordwrap = !it.isWordwrap }

    /** Textgröße in sp — Persistenz erfolgt separat über EditorConfig (:feature:settings). */
    fun setTextSizeSp(sizeSp: Float) = withEditor { it.setTextSize(sizeSp.coerceIn(6f, 48f)) }
    fun currentTextSizeSp(): Float = editorRef?.get()?.textSizePx?.let { px ->
        px / (editorRef?.get()?.resources?.displayMetrics?.scaledDensity ?: 1f)
    } ?: 14f

}
