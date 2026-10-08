/**
 * Modul: :feature:editor
 * @author Thomas Schmid
 *
 * Compose-Overlay über dem sora-[CodeEditor]: Farbkästchen (Farbliterale) und Update-Chips
 * („4.0.1 -> 4.0.3“) rechts neben der jeweiligen Zeile.
 *
 * Warum kein sora-InlayHint: InlayHints (Text/Farbe) gibt es erst ab sora-editor 0.24.x, das Projekt
 * ist auf 0.23.4 gepinnt. Die Positionen kommen aus der in 0.23.4 verifizierten Public-API:
 *  - `getCharOffsetX(line, column)`: x auf der View (inkl. Zeilennummern-Gutter, abzgl. Scroll-X)
 *  - `getCharOffsetY(line, column)`: UNTERKANTE der Zeile/Visual-Row auf der View (abzgl. Scroll-Y)
 *  - `getRowHeight()`; Updates bei `ScrollEvent` und `ContentChangeEvent`.
 */
package com.codeforge.feature.editor.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import io.github.rosemoe.sora.widget.CodeEditor
import kotlin.math.roundToInt

/** Ein Overlay-Element rechts neben einer Zeile. */
sealed interface OverlayItem {
    @Immutable data class Swatch(val argb: Int) : OverlayItem
    @Immutable data class UpdateChip(val update: DependencyUpdate) : OverlayItem
}

@Immutable
data class LineOverlay(val line: Int, val items: List<OverlayItem>)

/** Gruppiert Farben und Update-Hinweise je Zeile (Chips zuerst, danach Farbkästchen). */
fun buildLineOverlays(colors: List<ColorMatch>, updates: List<Pair<Int, DependencyUpdate>>): List<LineOverlay> {
    val byLine = sortedMapOf<Int, MutableList<OverlayItem>>()
    updates.forEach { (line, u) -> byLine.getOrPut(line) { ArrayList() } += OverlayItem.UpdateChip(u) }
    colors.forEach { c -> byLine.getOrPut(c.line) { ArrayList() } += OverlayItem.Swatch(c.argb) }
    return byLine.map { (line, items) -> LineOverlay(line, items) }
}

/**
 * Positionsermittlung — bewusst in einer Klasse gekapselt, damit bei einem sora-Update nur hier
 * angepasst werden muss. Gibt `null` zurück, wenn die Zeile (noch) nicht im Layout existiert.
 */
internal class EditorOverlayGeometry(private val editor: CodeEditor) {
    val rowHeightPx: Int get() = editor.rowHeight

    /** Obere Kante der letzten Visual-Row der Zeile + x direkt hinter dem Zeilenende (View-Koordinaten). */
    fun lineEnd(line: Int): Pair<Float, Float>? = runCatching {
        if (line < 0 || line >= editor.lineCount) return null
        val endColumn = editor.text.getColumnCount(line)
        val bottom = editor.getCharOffsetY(line, endColumn)
        val x = editor.getCharOffsetX(line, endColumn)
        x to (bottom - editor.rowHeight)
    }.getOrNull()
}

@Composable
internal fun EditorOverlayLayer(
    modifier: Modifier = Modifier,
    editor: CodeEditor?,
    /** Wird bei Scroll/Textänderung erhöht, um die Positionen neu zu berechnen. */
    tick: Int,
    overlays: List<LineOverlay>,
    onUpdateChipClick: (DependencyUpdate) -> Unit
) {
    if (editor == null || overlays.isEmpty()) return
    val geometry = remember(editor) { EditorOverlayGeometry(editor) }
    val density = LocalDensity.current
    val viewHeight = editor.height
    val rowHeight = geometry.rowHeightPx
    val swatchSizeDp = with(density) { (rowHeight * 0.7f).toDp() }.coerceIn(10.dp, 28.dp)
    val gapPx = with(density) { 8.dp.toPx() }

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        // tick wird nur gelesen, damit Änderungen eine Neuberechnung auslösen
        @Suppress("UNUSED_VARIABLE") val unused = tick
        for (overlay in overlays) {
            val (x, top) = geometry.lineEnd(overlay.line) ?: continue
            if (top + rowHeight < 0 || top > viewHeight) continue // außerhalb des sichtbaren Bereichs
            Row(
                modifier = Modifier
                    .offset { IntOffset((x + gapPx).roundToInt(), top.roundToInt()) }
                    .height(with(density) { rowHeight.toDp() }),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (item in overlay.items) {
                    when (item) {
                        is OverlayItem.UpdateChip -> UpdateChipView(item.update, onUpdateChipClick)
                        is OverlayItem.Swatch -> ColorSwatch(item.argb, swatchSizeDp)
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateChipView(update: DependencyUpdate, onClick: (DependencyUpdate) -> Unit) {
    Text(
        text = update.label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onTertiaryContainer,
        maxLines = 1,
        modifier = Modifier
            .padding(end = 6.dp)
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(8.dp))
            .clickable { onClick(update) }
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

/** Quadratisches Kästchen in exakt der Literal-Farbe (inkl. Alpha, bei Transparenz über Schachbrett). */
@Composable
private fun ColorSwatch(argb: Int, size: androidx.compose.ui.unit.Dp) {
    val shape = RoundedCornerShape(3.dp)
    val color = Color(argb)
    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(size)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .padding(1.dp)
            .clipToBounds()
    ) {
        if (color.alpha < 1f) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = this.size.width / 2f
                drawRect(Color.White)
                drawRect(Color(0xFFBDBDBD), Offset(cell, 0f), Size(cell, cell))
                drawRect(Color(0xFFBDBDBD), Offset(0f, cell), Size(cell, cell))
            }
        }
        Box(Modifier.fillMaxSize().background(color))
    }
}
