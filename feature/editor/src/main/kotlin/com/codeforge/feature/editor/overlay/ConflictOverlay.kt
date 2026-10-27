// Modul: :feature:editor
package com.codeforge.feature.editor.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import io.github.rosemoe.sora.widget.CodeEditor

private val OursTint = Color(0x2E4CAF50)
private val TheirsTint = Color(0x2E2196F3)
private val MarkerTint = Color(0x55FF9800)

/** Färbt Konfliktbereiche: eigene Seite grün, fremde Seite blau, Markerzeilen orange. */
@Composable
fun ConflictOverlayLayer(
    editor: CodeEditor?,
    tick: Int,
    conflicts: List<ConflictBlock>,
    modifier: Modifier = Modifier
) {
    if (editor == null || conflicts.isEmpty()) return
    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val unused = tick
        val rowHeight = editor.rowHeight.toFloat()
        val maxLine = (editor.lineCount - 1).coerceAtLeast(0)

        fun band(fromLine: Int, toLine: Int, color: Color) {
            if (toLine < fromLine) return
            val first = fromLine.coerceIn(0, maxLine)
            val last = toLine.coerceIn(first, maxLine)
            val top = runCatching { editor.getCharOffsetY(first, 0) }.getOrNull()?.minus(rowHeight) ?: return
            val bottom = runCatching { editor.getCharOffsetY(last, 0) }.getOrNull() ?: return
            if (bottom < 0f || top > size.height) return
            drawRect(color, Offset(0f, top), Size(size.width, bottom - top))
        }

        for (c in conflicts) {
            val oursEnd = c.baseLine ?: c.separatorLine
            band(c.startLine + 1, oursEnd - 1, OursTint)
            band(c.separatorLine + 1, c.endLine - 1, TheirsTint)
            band(c.startLine, c.startLine, MarkerTint)
            c.baseLine?.let { band(it, it, MarkerTint) }
            band(c.separatorLine, c.separatorLine, MarkerTint)
            band(c.endLine, c.endLine, MarkerTint)
        }
    }
}
