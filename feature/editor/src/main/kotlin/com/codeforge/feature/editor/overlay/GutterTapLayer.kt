package com.codeforge.feature.editor.overlay

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.rosemoe.sora.util.IntPair
import io.github.rosemoe.sora.widget.CodeEditor

/** Was ein Tipp in die Randspalte (Zeilennummern, Faltmarken) auslöst. */
enum class GutterAction { FOLD, BREAKPOINT, CURSOR }

/**
 * Entscheidet, was ein Tipp an [x] (Pixel ab linkem Rand der Randspalte) bewirkt.
 *
 * Ohne Debugger toggelt jeder Tipp auf eine faltbare Zeile die Faltung – die ganze Zeilenbreite der Randspalte ist
 * Trefferfläche, nicht nur das kleine Symbol. Mit Debugger gehört die linke Hälfte den Haltepunkten, die rechte
 * Hälfte (mit den Faltmarken) der Faltung.
 */
object GutterTapResolver {
    /** Aktion für einen Tipp an [x] in einer Randspalte der Breite [gutterWidth]. */
    fun resolve(x: Float, gutterWidth: Float, foldable: Boolean, breakpointsEnabled: Boolean): GutterAction = when {
        foldable && (!breakpointsEnabled || x >= gutterWidth / 2f) -> GutterAction.FOLD
        breakpointsEnabled -> GutterAction.BREAKPOINT
        else -> GutterAction.CURSOR
    }
}

/**
 * Trefferfläche über der Randspalte des Editors: Faltung umschalten, Haltepunkt setzen (nur mit Debugger-Plugin)
 * oder Cursor an den Zeilenanfang setzen. Fängt nur die schmale Randspalte ab, Scrollen und Text bleiben beim Editor.
 */
@Composable
fun GutterTapLayer(
    editor: CodeEditor?,
    tick: Int,
    breakpointsEnabled: Boolean,
    onToggleBreakpoint: (Int) -> Unit,
    onEditBreakpoint: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (editor == null) return
    if (!breakpointsEnabled && !editor.isCodeFoldingEnabled) return
    val density = LocalDensity.current
    val toggle by rememberUpdatedState(onToggleBreakpoint)
    val edit by rememberUpdatedState(onEditBreakpoint)
    @Suppress("UNUSED_VARIABLE") val unused = tick
    val gutterPx = visibleGutterWidth(editor)
    if (gutterPx <= 0f) return
    val minPx = with(density) { MIN_HIT_DP.dp.toPx() }
    val widthDp = with(density) { gutterPx.coerceAtLeast(minPx).toDp() }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(widthDp)
            .pointerInput(editor, breakpointsEnabled) {
                detectTapGestures(
                    onTap = { offset ->
                        val line = lineAt(editor, offset.x, offset.y) ?: return@detectTapGestures
                        val fm = editor.foldingManager
                        val foldable = editor.isCodeFoldingEnabled &&
                            (fm.collapsedRegionFor(line) != null || fm.regionAt(line) != null)
                        when (GutterTapResolver.resolve(offset.x, gutterPx, foldable, breakpointsEnabled)) {
                            GutterAction.FOLD -> fm.toggle(line)
                            GutterAction.BREAKPOINT -> toggle(line + 1)
                            GutterAction.CURSOR -> runCatching { editor.setSelection(line, 0) }
                        }
                    },
                    onLongPress = { offset ->
                        if (breakpointsEnabled) lineAt(editor, offset.x, offset.y)?.let { edit(it + 1) }
                    }
                )
            }
    )
}

private const val MIN_HIT_DP = 24

// Sichtbare Breite der Randspalte: angeheftet bleibt sie stehen, sonst wandert sie beim seitlichen Scrollen mit.
private fun visibleGutterWidth(editor: CodeEditor): Float {
    if (!editor.isLineNumberEnabled && !editor.isCodeFoldingEnabled) return 0f
    val width = editor.measureTextRegionOffset()
    val pinned = editor.isLineNumberPinned && !editor.isWordwrap
    return if (pinned) width else (width - editor.offsetX).coerceAtLeast(0f)
}

// Zeile (0-basiert) unter der Bildschirmposition; berücksichtigt eingeklappte Bereiche und Zeilenumbruch.
private fun lineAt(editor: CodeEditor, x: Float, y: Float): Int? = runCatching {
    val pos = editor.getPointPositionOnScreen(x.coerceAtLeast(editor.measureTextRegionOffset()), y)
    IntPair.getFirst(pos).takeIf { it in 0 until editor.lineCount }
}.getOrNull()
