/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Compose-Näherung der Android-View-Hierarchie: LinearLayout (inkl. layout_weight/gravity),
 * FrameLayout (layout_gravity), Scroll-Container und die gängigen Widgets. Constraint-/Relative-
 * und unbekannte Container werden gestapelt (wie FrameLayout) gezeichnet. Jedes Element ist
 * antippbar und wählt den Knoten aus; Auswahl und Container-Umrisse werden hervorgehoben.
 * Unterstützt visuelle Drag & Drop Rückmeldungen und Knoten-Grenzen-Erfassung.
 */
package com.codeforge.feature.layoutdesigner.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeforge.feature.layoutdesigner.DevicePreset
import com.codeforge.feature.layoutdesigner.dnd.DesignerOffset
import com.codeforge.feature.layoutdesigner.dnd.DesignerRect
import com.codeforge.feature.layoutdesigner.dnd.DragController
import com.codeforge.feature.layoutdesigner.dnd.DropIndicator
import com.codeforge.feature.layoutdesigner.dnd.DropTarget
import com.codeforge.feature.layoutdesigner.model.DragPayload
import com.codeforge.feature.layoutdesigner.model.DropVerdict
import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.LayoutNode
import com.codeforge.feature.layoutdesigner.model.SizeMode
import com.codeforge.feature.layoutdesigner.model.find
import com.codeforge.feature.layoutdesigner.model.parseColorArgb
import com.codeforge.feature.layoutdesigner.model.parseDimension
import com.codeforge.feature.layoutdesigner.model.parseSize
import kotlin.math.roundToInt

@Immutable
private class PreviewContext(
    val selectedUid: Int?,
    val onSelect: (Int) -> Unit,
    val dragController: DragController?,
    val onDrop: ((DragPayload, DropTarget) -> Unit)?,
    val document: LayoutDocument,
)

/** Tags, die nur als gestapelte Näherung gezeichnet werden. */
fun isApproximatedTag(tag: String): Boolean = tag == "RelativeLayout" ||
    tag.endsWith("ConstraintLayout") || tag.endsWith("CoordinatorLayout") ||
    (tag !in setOf("LinearLayout", "FrameLayout", "ScrollView", "HorizontalScrollView") && tag.contains('.'))

@Composable
fun LayoutPreview(
    document: LayoutDocument,
    selectedUid: Int?,
    device: DevicePreset,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    dragController: DragController? = null,
    onDrop: ((DragPayload, DropTarget) -> Unit)? = null,
) {
    val context = remember(selectedUid, onSelect, dragController, onDrop, document) {
        PreviewContext(selectedUid, onSelect, dragController, onDrop, document)
    }

    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Surface(
            modifier = Modifier
                .size(device.widthDp.dp, device.heightDp.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(4.dp),
        ) {
            Box(Modifier.fillMaxSize()) {
                NodeView(document.root, context, scopeModifier = Modifier)

                // Drag & Drop Overlays
                if (dragController != null && dragController.isDragging) {
                    val target = dragController.currentTarget
                    val primaryColor = MaterialTheme.colorScheme.primary
                    val errorColor = MaterialTheme.colorScheme.error

                    if (target != null) {
                        val indicatorColor = if (target.verdict is DropVerdict.Rejected) errorColor else primaryColor
                        when (val ind = target.indicator) {
                            is DropIndicator.Line -> {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawLine(
                                        color = indicatorColor,
                                        start = Offset(ind.startX, ind.startY),
                                        end = Offset(ind.endX, ind.endY),
                                        strokeWidth = 4.dp.toPx(),
                                    )
                                }
                            }
                            is DropIndicator.HighlightContainer -> {
                                Canvas(Modifier.fillMaxSize()) {
                                    drawRect(
                                        color = indicatorColor.copy(alpha = 0.15f),
                                        topLeft = Offset(ind.rect.left, ind.rect.top),
                                        size = Size(ind.rect.width, ind.rect.height),
                                    )
                                    drawRect(
                                        color = indicatorColor,
                                        topLeft = Offset(ind.rect.left, ind.rect.top),
                                        size = Size(ind.rect.width, ind.rect.height),
                                        style = Stroke(width = 2.dp.toPx()),
                                    )
                                }
                            }
                        }

                        if (target.verdict is DropVerdict.Rejected) {
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(dragController.dragPosition.x.roundToInt() + 16, dragController.dragPosition.y.roundToInt() + 16) }
                                    .background(errorColor, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = target.verdict.reason,
                                    color = MaterialTheme.colorScheme.onError,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }

                    // Drag Badge under finger
                    val payload = dragController.activePayload
                    if (payload != null) {
                        val label = when (payload) {
                            is DragPayload.NewWidget -> payload.entry.tag.substringAfterLast('.')
                            is DragPayload.ExistingNodes -> {
                                val firstNode = document.find(payload.uids.firstOrNull() ?: -1)
                                firstNode?.tag?.substringAfterLast('.') ?: "View"
                            }
                        }
                        ElevatedCard(
                            modifier = Modifier
                                .offset { IntOffset(dragController.dragPosition.x.roundToInt() - 32, dragController.dragPosition.y.roundToInt() - 48) }
                                .alpha(0.85f),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeView(node: LayoutNode, ctx: PreviewContext, scopeModifier: Modifier) {
    val visibility = node.attr("android:visibility")
    if (visibility == "gone") return

    DisposableEffect(node.uid) {
        onDispose {
            ctx.dragController?.boundsRegistry?.unregister(node.uid)
        }
    }

    val widthSpec = parseSize(node.attr("android:layout_width"))
    val heightSpec = parseSize(node.attr("android:layout_height"))
    val weight = node.attr("android:layout_weight")?.toFloatOrNull() ?: 0f
    val margin = (parseDimension(node.attr("android:layout_margin")) ?: 0f).dp
    val padding = (parseDimension(node.attr("android:padding")) ?: 0f).dp
    val bg = parseColorArgb(node.attr("android:background"))?.let { Color(it.toInt()) }
    val selected = ctx.selectedUid == node.uid
    val tag = node.tag.substringAfterLast('.')
    val isContainer = node.isContainer

    var m: Modifier = scopeModifier.padding(margin)
    if (!(weight > 0f && widthSpec.mode == SizeMode.FIXED && widthSpec.dp == 0f)) {
        m = when (widthSpec.mode) {
            SizeMode.MATCH_PARENT -> m.fillMaxWidth()
            SizeMode.FIXED -> m.width(widthSpec.dp.dp)
            SizeMode.WRAP_CONTENT -> m
        }
    }
    if (!(weight > 0f && heightSpec.mode == SizeMode.FIXED && heightSpec.dp == 0f)) {
        m = when (heightSpec.mode) {
            SizeMode.MATCH_PARENT -> m.fillMaxHeight()
            SizeMode.FIXED -> m.height(heightSpec.dp.dp)
            SizeMode.WRAP_CONTENT -> m
        }
    }
    val outline = MaterialTheme.colorScheme.outlineVariant
    val primary = MaterialTheme.colorScheme.primary
    m = m
        .then(if (visibility == "invisible") Modifier.alpha(0f) else Modifier)
        .then(
            when {
                selected -> Modifier.border(2.dp, primary)
                isContainer || tag == "View" -> Modifier.border(1.dp, outline.copy(alpha = 0.6f))
                else -> Modifier
            },
        )
        .then(if (bg != null) Modifier.background(bg) else Modifier)
        .onGloballyPositioned { coords ->
            val pos = coords.positionInRoot()
            val size = coords.size
            ctx.dragController?.boundsRegistry?.register(
                node.uid,
                DesignerRect(
                    left = pos.x,
                    top = pos.y,
                    right = pos.x + size.width,
                    bottom = pos.y + size.height,
                ),
            )
        }
        .pointerInput(node.uid) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    ctx.dragController?.let { dc ->
                        val rect = dc.boundsRegistry.bounds[node.uid]
                        val startPos = if (rect != null) DesignerOffset(rect.left + offset.x, rect.top + offset.y) else DesignerOffset(offset.x, offset.y)
                        dc.startDrag(DragPayload.ExistingNodes(setOf(node.uid)), startPos, ctx.document)
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    ctx.dragController?.let { dc ->
                        val newPos = DesignerOffset(dc.dragPosition.x + dragAmount.x, dc.dragPosition.y + dragAmount.y)
                        dc.updatePointer(newPos, ctx.document)
                    }
                },
                onDragEnd = {
                    ctx.dragController?.let { dc ->
                        val target = dc.endDrag()
                        if (target != null && target.verdict !is DropVerdict.Rejected) {
                            ctx.onDrop?.invoke(DragPayload.ExistingNodes(setOf(node.uid)), target)
                        }
                    }
                },
                onDragCancel = {
                    ctx.dragController?.cancelDrag()
                },
            )
        }
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { ctx.onSelect(node.uid) }
        .padding(padding)

    when {
        tag == "LinearLayout" -> LinearView(node, ctx, m)
        tag == "ScrollView" || tag == "HorizontalScrollView" || tag == "FrameLayout" || isContainer ->
            FrameView(node, ctx, m)
        else -> LeafView(node, tag, m)
    }
}

@Composable
private fun LinearView(node: LayoutNode, ctx: PreviewContext, modifier: Modifier) {
    val vertical = node.attr("android:orientation") != "horizontal"
    val gravity = node.attr("android:gravity").orEmpty().split('|').map { it.trim() }.toSet()
    val centerH = "center" in gravity || "center_horizontal" in gravity
    val centerV = "center" in gravity || "center_vertical" in gravity
    val end = "end" in gravity || "right" in gravity
    val bottom = "bottom" in gravity

    if (vertical) {
        val horizAlign = when {
            centerH -> Alignment.CenterHorizontally
            end -> Alignment.End
            else -> Alignment.Start
        }
        val vertArr = when {
            centerV -> Arrangement.Center
            bottom -> Arrangement.Bottom
            else -> Arrangement.Top
        }
        Column(modifier = modifier, horizontalAlignment = horizAlign, verticalArrangement = vertArr) {
            node.children.forEach { child ->
                val weight = child.attr("android:layout_weight")?.toFloatOrNull() ?: 0f
                val childScopeModifier = if (weight > 0f) Modifier.weight(weight) else Modifier
                NodeView(child, ctx, childScopeModifier)
            }
        }
    } else {
        val vertAlign = when {
            centerV -> Alignment.CenterVertically
            bottom -> Alignment.Bottom
            else -> Alignment.Top
        }
        val horizArr = when {
            centerH -> Arrangement.Center
            end -> Arrangement.End
            else -> Arrangement.Start
        }
        Row(modifier = modifier, verticalAlignment = vertAlign, horizontalArrangement = horizArr) {
            node.children.forEach { child ->
                val weight = child.attr("android:layout_weight")?.toFloatOrNull() ?: 0f
                val childScopeModifier = if (weight > 0f) Modifier.weight(weight) else Modifier
                NodeView(child, ctx, childScopeModifier)
            }
        }
    }
}

@Composable
private fun FrameView(node: LayoutNode, ctx: PreviewContext, modifier: Modifier) {
    Box(modifier = modifier) {
        node.children.forEach { child ->
            val align = resolveGravity(child.attr("android:layout_gravity"))
            NodeView(child, ctx, Modifier.align(align))
        }
    }
}

private fun resolveGravity(spec: String?): Alignment {
    if (spec.isNullOrEmpty()) return Alignment.TopStart
    val parts = spec.split('|').map { it.trim() }.toSet()
    val center = "center" in parts
    val centerH = center || "center_horizontal" in parts
    val centerV = center || "center_vertical" in parts
    val end = "end" in parts || "right" in parts
    val bottom = "bottom" in parts
    return when {
        centerH && centerV -> Alignment.Center
        centerH && bottom -> Alignment.BottomCenter
        centerH -> Alignment.TopCenter
        centerV && end -> Alignment.CenterEnd
        centerV -> Alignment.CenterStart
        bottom && end -> Alignment.BottomEnd
        bottom -> Alignment.BottomStart
        end -> Alignment.TopEnd
        else -> Alignment.TopStart
    }
}

@Composable
private fun LeafView(node: LayoutNode, tag: String, modifier: Modifier) {
    when (tag) {
        "TextView" -> {
            val text = node.attr("android:text") ?: node.attr("android:hint") ?: "(TextView)"
            val size = (parseDimension(node.attr("android:textSize")) ?: 14f).sp
            val color = parseColorArgb(node.attr("android:textColor"))?.let { Color(it.toInt()) }
                ?: MaterialTheme.colorScheme.onSurface
            val style = if (node.attr("android:text") == null && node.attr("android:hint") != null) FontStyle.Italic else FontStyle.Normal
            Text(
                text = text,
                modifier = modifier,
                fontSize = size,
                color = color,
                fontStyle = style,
                fontWeight = if (node.attr("android:textStyle") == "bold") FontWeight.Bold else FontWeight.Normal,
            )
        }
        "Button", "MaterialButton" -> {
            val text = node.attr("android:text") ?: "Button"
            Surface(
                modifier = modifier.defaultMinSize(minWidth = 64.dp, minHeight = 36.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Text(text, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        "EditText", "TextInputEditText" -> {
            val text = node.attr("android:text") ?: node.attr("android:hint") ?: "Eingabe…"
            Surface(
                modifier = modifier.defaultMinSize(minHeight = 44.dp),
                shape = RoundedCornerShape(4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ) {
                Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        "ImageView", "ImageButton" -> {
            Surface(
                modifier = modifier.defaultMinSize(48.dp, 48.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(4.dp),
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = node.attr("android:contentDescription") ?: "Image",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        "CheckBox" -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = node.attr("android:checked") == "true", onCheckedChange = null)
            Text(node.attr("android:text").orEmpty(), modifier = Modifier.padding(start = 4.dp))
        }
        "RadioButton" -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = node.attr("android:checked") == "true", onClick = null)
            Text(node.attr("android:text").orEmpty(), modifier = Modifier.padding(start = 4.dp))
        }
        "Switch", "MaterialSwitch" -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(node.attr("android:text").orEmpty(), modifier = Modifier.padding(end = 8.dp))
            Switch(checked = node.attr("android:checked") == "true", onCheckedChange = null)
        }
        "ProgressBar" -> Box(modifier = modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
        else -> {
            Box(
                modifier = modifier.defaultMinSize(40.dp, 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "<$tag>",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
