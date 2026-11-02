/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Completion-Fenster, das die Bildschirmtastatur berücksichtigt.
 *
 * sora begrenzt das Fenster auf `editor.getHeight()` und kennt die Tastatur nicht. Ist der Editor (noch) nicht
 * um die Tastaturhöhe verkleinert, etwa während der Einblend-Animation oder bei Floating-/Split-Tastaturen,
 * ragt das Fenster unter die Tastatur. Hier wird die sichtbare Höhe aus den IME-Insets ermittelt; reicht
 * der Platz unter dem Cursor nicht aus, erscheint die Liste über der Zeile.
 */
package com.codeforge.feature.editor.completion

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion

class ImeAwareAutoCompletion(private val codeEditor: CodeEditor) : EditorAutoCompletion(codeEditor) {

    private val location = IntArray(2)

    /** Sichtbare Höhe des Editors über der Tastatur (in Pixeln, höchstens `editor.height`). */
    private fun visibleEditorHeight(): Int {
        val full = codeEditor.height
        val ime = ViewCompat.getRootWindowInsets(codeEditor)
            ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0
        if (ime <= 0) return full
        codeEditor.getLocationInWindow(location)
        val keyboardTop = codeEditor.rootView.height - ime
        return (keyboardTop - location[1]).coerceIn(0, full)
    }

    override fun updateCompletionWindowPosition(scrollEditor: Boolean) {
        val dp = codeEditor.dpUnit
        val cursor = codeEditor.cursor
        var panelX = codeEditor.updateCursorAnchor() + dp * 20
        val rowHeight = codeEditor.rowHeight
        val offset = codeEditor.layout.getCharLayoutOffset(cursor.rightLine, cursor.rightColumn)
        var panelY = offset[0] - codeEditor.offsetY + rowHeight / 2f
        val visible = visibleEditorHeight()
        var restY = visible - panelY

        if (restY > dp * 200) {
            restY = dp * 200
        } else if (restY < dp * 100 && scrollEditor) {
            var scrolled = 0f
            while (restY < dp * 100 && codeEditor.offsetY + scrolled + rowHeight <= codeEditor.scrollMaxY) {
                restY += rowHeight
                panelY -= rowHeight
                scrolled += rowHeight
            }
            codeEditor.scroller.startScroll(codeEditor.offsetX, codeEditor.offsetY, 0, scrolled.toInt(), 0)
        }

        val width: Int
        if ((codeEditor.width < 500 * dp && completionWndPositionMode == WINDOW_POS_MODE_AUTO) ||
            completionWndPositionMode == WINDOW_POS_MODE_FULL_WIDTH_ALWAYS
        ) {
            width = codeEditor.width * 7 / 8
            panelX = codeEditor.width / 8f / 2f
        } else {
            width = minOf(300 * dp, codeEditor.width / 2f).toInt()
        }

        // Selbst nach dem Scrollen zu wenig Platz unter dem Cursor: Liste über der Zeile anzeigen.
        val above = panelY - rowHeight
        val placeAbove = restY < dp * 100 && above > restY
        val available = if (placeAbove) above else restY
        setMaxHeight(available.toInt().coerceAtLeast(0))
        val height = minOf(getHeight(), available.toInt()).coerceAtLeast(0)
        val y = if (placeAbove) above - height else panelY
        setLocation(panelX.toInt() + codeEditor.offsetX, y.toInt() + codeEditor.offsetY)
        setSize(width, height)
    }
}
