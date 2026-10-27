// Modul: :feature:editor
package com.codeforge.feature.editor.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.GitLineChange
import com.codeforge.core.domain.model.GitLineChangeKind
import io.github.rosemoe.sora.widget.CodeEditor

private val AddedColor = Color(0xFF4CAF50)
private val ModifiedColor = Color(0xFF2196F3)
private val DeletedColor = Color(0xFFF44336)

/** Zeichnet Git-Änderungsbalken (hinzugefügt/geändert) und Löschmarken am linken Rand des Editors. */
@Composable
fun GitGutterOverlayLayer(
    editor: CodeEditor?,
    tick: Int,
    changes: List<GitLineChange>,
    modifier: Modifier = Modifier
) {
    if (editor == null || changes.isEmpty()) return
    val density = LocalDensity.current
    val barWidth = with(density) { 3.dp.toPx() }
    val triangle = with(density) { 6.dp.toPx() }

    Canvas(modifier = modifier.fillMaxSize()) {
        @Suppress("UNUSED_VARIABLE") val unused = tick
        val rowHeight = editor.rowHeight.toFloat()
        val lineCount = editor.lineCount
        for (change in changes) {
            when (change.kind) {
                GitLineChangeKind.ADDED, GitLineChangeKind.MODIFIED -> {
                    val color = if (change.kind == GitLineChangeKind.ADDED) AddedColor else ModifiedColor
                    val first = change.startLine.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
                    val last = (change.startLine + change.lineCount - 1).coerceIn(first, (lineCount - 1).coerceAtLeast(0))
                    val topY = runCatching { editor.getCharOffsetY(first, 0) }.getOrNull()?.minus(rowHeight) ?: continue
                    val bottomY = runCatching { editor.getCharOffsetY(last, 0) }.getOrNull() ?: continue
                    if (bottomY < 0f || topY > size.height) continue
                    drawRect(color = color, topLeft = Offset(0f, topY), size = Size(barWidth, bottomY - topY))
                }
                GitLineChangeKind.DELETED -> {
                    val line = change.startLine.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
                    val y = runCatching { editor.getCharOffsetY(line, 0) }.getOrNull()?.minus(rowHeight) ?: continue
                    if (y < -triangle || y > size.height) continue
                    drawPath(
                        Path().apply {
                            moveTo(0f, y - triangle / 2f)
                            lineTo(triangle, y)
                            lineTo(0f, y + triangle / 2f)
                            close()
                        },
                        color = DeletedColor
                    )
                }
            }
        }
    }
}
