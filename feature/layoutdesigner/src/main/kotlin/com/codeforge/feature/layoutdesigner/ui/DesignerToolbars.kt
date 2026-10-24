/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Werkzeuge und Overlays für die Designer-Leinwand:
 * - Breadcrumbs (Wurzel › … › Auswahl)
 * - Zoom- und Pan-Steuerelemente
 * - Konfigurationsleiste (Querformat, Dunkel, Schriftgröße, RTL, Overlays)
 * - Größengriffe für ausgewählte Elemente
 */
package com.codeforge.feature.layoutdesigner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.layoutdesigner.LayoutDesignerUiEvent
import com.codeforge.feature.layoutdesigner.LayoutDesignerUiState
import com.codeforge.feature.layoutdesigner.OverlayType
import com.codeforge.feature.layoutdesigner.dnd.DesignerRect
import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.LayoutNode
import com.codeforge.feature.layoutdesigner.model.find
import com.codeforge.feature.layoutdesigner.model.parentOf
import kotlin.math.roundToInt

/** Breadcrumb-Leiste: Wurzel › … › Aktuelle Auswahl */
@Composable
fun BreadcrumbBar(
    document: LayoutDocument,
    selectedUid: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chain = remember(document, selectedUid) {
        val list = ArrayList<LayoutNode>()
        var curr = selectedUid?.let { document.find(it) } ?: document.root
        list.add(curr)
        while (curr.uid != document.root.uid) {
            val parent = document.parentOf(curr.uid) ?: break
            list.add(0, parent)
            curr = parent
        }
        list
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        chain.forEachIndexed { index, node ->
            val isCurrent = node.uid == (selectedUid ?: document.root.uid)
            val label = node.attr("android:id")?.removePrefix("@+id/")?.removePrefix("@id/")
                ?: node.tag.substringAfterLast('.')

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clickable { onSelect(node.uid) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
            if (index < chain.lastIndex) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/** Schwebende Zoom-Steuerung (+, -, 100%, Reset) */
@Composable
fun ZoomControls(
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 4.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            IconButton(
                onClick = { onZoomChange((zoom - 0.25f).coerceAtLeast(0.25f)) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom out", modifier = Modifier.size(18.dp))
            }
            Text(
                text = "${(zoom * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clickable { onZoomChange(1.0f) }
                    .padding(horizontal = 6.dp),
            )
            IconButton(
                onClick = { onZoomChange((zoom + 0.25f).coerceAtMost(4.0f)) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom in", modifier = Modifier.size(18.dp))
            }
            IconButton(
                onClick = onReset,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = "Reset Zoom/Pan", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Schnellauswahl für Konfigurationen und Overlays */
@Composable
fun CanvasConfigBar(
    state: LayoutDesignerUiState,
    onEvent: (LayoutDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.isLandscape,
            onClick = { onEvent(LayoutDesignerUiEvent.SetLandscape(!state.isLandscape)) },
            leadingIcon = { Icon(Icons.Filled.ScreenRotation, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text(stringRes(if (state.isLandscape) R.string.layout_cfg_landscape else R.string.layout_cfg_portrait)) },
        )

        FilterChip(
            selected = state.isDarkTheme,
            onClick = { onEvent(LayoutDesignerUiEvent.SetDarkTheme(!state.isDarkTheme)) },
            leadingIcon = {
                Icon(
                    if (state.isDarkTheme) Icons.Filled.Brightness4 else Icons.Filled.Brightness7,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            },
            label = { Text(stringRes(if (state.isDarkTheme) R.string.layout_cfg_dark else R.string.layout_cfg_light)) },
        )

        FilterChip(
            selected = state.fontScale > 1.0f,
            onClick = {
                val nextScale = when (state.fontScale) {
                    1.0f -> 1.3f
                    1.3f -> 2.0f
                    else -> 1.0f
                }
                onEvent(LayoutDesignerUiEvent.SetFontScale(nextScale))
            },
            leadingIcon = { Icon(Icons.Filled.FormatSize, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text(stringRes(R.string.layout_cfg_font_scale, state.fontScale)) },
        )

        FilterChip(
            selected = state.blueprintMode,
            onClick = { onEvent(LayoutDesignerUiEvent.ToggleOverlay(OverlayType.BLUEPRINT)) },
            leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(16.dp)) },
            label = { Text(stringRes(R.string.layout_cfg_blueprint)) },
        )

        FilterChip(
            selected = state.showMarginsPaddingsOverlay,
            onClick = { onEvent(LayoutDesignerUiEvent.ToggleOverlay(OverlayType.MARGINS_PADDINGS)) },
            label = { Text(stringRes(R.string.layout_cfg_margins)) },
        )
    }
}

/** 4 Größengriffe an den Rändern des selektierten Elements */
@Composable
fun ResizeHandles(
    rect: DesignerRect,
    onResizeDelta: (dx: Float, dy: Float) -> Unit,
    onToggleWidth: () -> Unit,
    onToggleHeight: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val handleColor = MaterialTheme.colorScheme.primary
    val handleSize = 12.dp

    // Rechter Griff (Breite)
    Box(
        modifier = modifier
            .offset { IntOffset(rect.right.roundToInt() - 6, rect.centerY.roundToInt() - 6) }
            .size(handleSize)
            .background(handleColor, CircleShape)
            .border(1.5.dp, Color.White, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onToggleWidth() })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onResizeDelta(dragAmount.x, 0f)
                }
            },
    )

    // Unterer Griff (Höhe)
    Box(
        modifier = modifier
            .offset { IntOffset(rect.centerX.roundToInt() - 6, rect.bottom.roundToInt() - 6) }
            .size(handleSize)
            .background(handleColor, CircleShape)
            .border(1.5.dp, Color.White, CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { onToggleHeight() })
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onResizeDelta(0f, dragAmount.y)
                }
            },
    )

    // Unterer Rechter Griff (Breite & Höhe)
    Box(
        modifier = modifier
            .offset { IntOffset(rect.right.roundToInt() - 6, rect.bottom.roundToInt() - 6) }
            .size(handleSize)
            .background(handleColor, CircleShape)
            .border(1.5.dp, Color.White, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onResizeDelta(dragAmount.x, dragAmount.y)
                }
            },
    )
}
