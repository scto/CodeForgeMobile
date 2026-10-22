package com.codeforge.feature.editor.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.Breakpoint
import io.github.rosemoe.sora.widget.CodeEditor

@Composable
fun BreakpointOverlayLayer(
    editor: CodeEditor?,
    tick: Int,
    breakpoints: List<Breakpoint>,
    pausedLine: Int?,
    onToggleBreakpoint: (Int) -> Unit,
    onEditBreakpoint: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (editor == null) return

    val density = LocalDensity.current
    val rowHeight = editor.rowHeight
    val dotRadiusPx = with(density) { 5.dp.toPx() }
    val strokeWidthPx = with(density) { 2.dp.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(editor, breakpoints) {
                detectTapGestures(
                    onTap = { offset ->
                        val line = findLineAtY(editor, offset.y)
                        if (line != null) {
                            val gutterWidth = runCatching { editor.getCharOffsetX(line, 0) }.getOrDefault(0f)
                            if (offset.x <= gutterWidth + with(density) { 16.dp.toPx() }) {
                                onToggleBreakpoint(line + 1)
                            }
                        }
                    },
                    onLongPress = { offset ->
                        val line = findLineAtY(editor, offset.y)
                        if (line != null) {
                            val gutterWidth = runCatching { editor.getCharOffsetX(line, 0) }.getOrDefault(0f)
                            if (offset.x <= gutterWidth + with(density) { 16.dp.toPx() }) {
                                onEditBreakpoint(line + 1)
                            }
                        }
                    }
                )
            }
    ) {
        @Suppress("UNUSED_VARIABLE") val unused = tick
        val viewHeight = size.height
        val viewWidth = size.width

        // 1. Pausierte Zeile hervorheben
        if (pausedLine != null && pausedLine > 0 && pausedLine <= editor.lineCount) {
            val soraLine = pausedLine - 1
            val bottom = runCatching { editor.getCharOffsetY(soraLine, 0) }.getOrNull()
            if (bottom != null) {
                val top = bottom - rowHeight
                if (top + rowHeight >= 0 && top <= viewHeight) {
                    // Gelber Hintergrund für angehaltene Zeile
                    drawRect(
                        color = Color(0x33FFEB3B),
                        topLeft = Offset(0f, top),
                        size = Size(viewWidth, rowHeight.toFloat())
                    )

                    // Grüner Ausführungspfeil im Gutter
                    val arrowPath = Path().apply {
                        val arrowX = with(density) { 4.dp.toPx() }
                        val arrowY = top + (rowHeight - with(density) { 12.dp.toPx() }) / 2f
                        val arrowSize = with(density) { 12.dp.toPx() }
                        moveTo(arrowX, arrowY)
                        lineTo(arrowX + arrowSize, arrowY + arrowSize / 2f)
                        lineTo(arrowX, arrowY + arrowSize)
                        close()
                    }
                    drawPath(arrowPath, color = Color(0xFF4CAF50))
                }
            }
        }

        // 2. Breakpoint-Markierungen im Gutter
        for (bp in breakpoints) {
            if (bp.line <= 0 || bp.line > editor.lineCount) continue
            val soraLine = bp.line - 1
            val bottom = runCatching { editor.getCharOffsetY(soraLine, 0) }.getOrNull() ?: continue
            val top = bottom - rowHeight
            if (top + rowHeight < 0 || top > viewHeight) continue

            val centerX = with(density) { 10.dp.toPx() }
            val centerY = top + rowHeight / 2f

            val baseColor = if (bp.enabled) Color(0xFFE53935) else Color(0xFF9E9E9E)

            if (bp.verified || !bp.enabled) {
                // Gefüllter Punkt
                drawCircle(
                    color = baseColor,
                    radius = dotRadiusPx,
                    center = Offset(centerX, centerY),
                    style = Fill
                )
            } else {
                // Unverifizierter Breakpoint: Hohlkreis
                drawCircle(
                    color = baseColor,
                    radius = dotRadiusPx,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = strokeWidthPx)
                )
            }

            // Falls Bedingung vorhanden: kleiner weißer Punkt in der Mitte
            if (bp.condition != null && bp.enabled) {
                drawCircle(
                    color = Color.White,
                    radius = dotRadiusPx * 0.4f,
                    center = Offset(centerX, centerY),
                    style = Fill
                )
            }
        }
    }
}

private fun findLineAtY(editor: CodeEditor, y: Float): Int? {
    val count = editor.lineCount
    for (i in 0 until count) {
        val bottom = runCatching { editor.getCharOffsetY(i, 0) }.getOrNull() ?: continue
        val top = bottom - editor.rowHeight
        if (y in top..bottom) {
            return i
        }
    }
    return null
}
