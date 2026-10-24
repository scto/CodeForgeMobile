/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Echte Darstellung von RelativeLayout mittels Custom-Layout-Messung
 * und topologischer Sortierung der Kind-Abhängigkeiten.
 */
package com.codeforge.feature.layoutdesigner.preview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.dp
import com.codeforge.feature.layoutdesigner.model.LayoutNode
import com.codeforge.feature.layoutdesigner.model.RelativeNodeSpec
import com.codeforge.feature.layoutdesigner.model.RelativeSolver
import com.codeforge.feature.layoutdesigner.model.parseDimension
import kotlin.math.roundToInt

private data class PlacedBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

@Composable
internal fun RelativeView(
    container: LayoutNode,
    ctx: PreviewContext,
    modifier: Modifier = Modifier,
) {
    val solveResult = remember(container) { RelativeSolver.solve(container) }

    Box(modifier = modifier) {
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                for (spec in solveResult.orderedNodes) {
                    NodeView(spec.node, ctx, Modifier)
                }
            },
        ) { measurables, constraints ->
            val parentW = constraints.maxWidth
            val parentH = constraints.maxHeight

            val resolved = mutableMapOf<String, PlacedBox>()
            val placements = mutableListOf<Pair<Placeable, Pair<Int, Int>>>()

            for (i in measurables.indices) {
                val measurable = measurables[i]
                val spec = solveResult.orderedNodes[i]
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))

                val mStart = (parseDimension(spec.node.attr("android:layout_marginStart") ?: spec.node.attr("android:layout_marginLeft")) ?: 0f).dp.roundToPx()
                val mEnd = (parseDimension(spec.node.attr("android:layout_marginEnd") ?: spec.node.attr("android:layout_marginRight")) ?: 0f).dp.roundToPx()
                val mTop = (parseDimension(spec.node.attr("android:layout_marginTop")) ?: 0f).dp.roundToPx()
                val mBottom = (parseDimension(spec.node.attr("android:layout_marginBottom")) ?: 0f).dp.roundToPx()

                // X-Koordinate berechnen
                val x = when {
                    spec.alignParentStart -> mStart
                    spec.alignParentEnd -> (parentW - placeable.width - mEnd).coerceAtLeast(0)
                    spec.centerHorizontal || spec.centerInParent -> (parentW - placeable.width) / 2
                    spec.toEndOfId != null && resolved.containsKey(spec.toEndOfId) -> {
                        val s = resolved[spec.toEndOfId]!!
                        s.x + s.width + mStart
                    }
                    spec.toStartOfId != null && resolved.containsKey(spec.toStartOfId) -> {
                        val s = resolved[spec.toStartOfId]!!
                        s.x - placeable.width - mEnd
                    }
                    spec.alignStartId != null && resolved.containsKey(spec.alignStartId) -> {
                        val s = resolved[spec.alignStartId]!!
                        s.x + mStart
                    }
                    spec.alignEndId != null && resolved.containsKey(spec.alignEndId) -> {
                        val s = resolved[spec.alignEndId]!!
                        s.x + s.width - placeable.width - mEnd
                    }
                    else -> mStart
                }

                // Y-Koordinate berechnen
                val y = when {
                    spec.alignParentTop -> mTop
                    spec.alignParentBottom -> (parentH - placeable.height - mBottom).coerceAtLeast(0)
                    spec.centerVertical || spec.centerInParent -> (parentH - placeable.height) / 2
                    spec.belowId != null && resolved.containsKey(spec.belowId) -> {
                        val s = resolved[spec.belowId]!!
                        s.y + s.height + mTop
                    }
                    spec.aboveId != null && resolved.containsKey(spec.aboveId) -> {
                        val s = resolved[spec.aboveId]!!
                        s.y - placeable.height - mBottom
                    }
                    spec.alignTopId != null && resolved.containsKey(spec.alignTopId) -> {
                        val s = resolved[spec.alignTopId]!!
                        s.y + mTop
                    }
                    spec.alignBottomId != null && resolved.containsKey(spec.alignBottomId) -> {
                        val s = resolved[spec.alignBottomId]!!
                        s.y + s.height - placeable.height - mBottom
                    }
                    else -> mTop
                }

                if (spec.id != null) {
                    resolved[spec.id] = PlacedBox(x, y, placeable.width, placeable.height)
                }
                placements.add(Pair(placeable, Pair(x, y)))
            }

            layout(parentW, parentH) {
                for ((placeable, pos) in placements) {
                    placeable.place(pos.first, pos.second)
                }
            }
        }

        if (solveResult.hasCycle) {
            Text(
                text = "Zyklus in RelativeLayout (${solveResult.cycleNodes.joinToString()})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        }
    }
}
