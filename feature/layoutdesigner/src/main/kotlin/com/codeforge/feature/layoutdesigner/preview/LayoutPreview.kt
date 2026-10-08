/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Compose-Näherung der Android-View-Hierarchie: LinearLayout (inkl. layout_weight/gravity),
 * FrameLayout (layout_gravity), Scroll-Container und die gängigen Widgets. Constraint-/Relative-
 * und unbekannte Container werden gestapelt (wie FrameLayout) gezeichnet. Jedes Element ist
 * antippbar und wählt den Knoten aus; Auswahl und Container-Umrisse werden hervorgehoben.
 */
package com.codeforge.feature.layoutdesigner.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeforge.feature.layoutdesigner.DevicePreset
import com.codeforge.feature.layoutdesigner.model.LayoutDocument
import com.codeforge.feature.layoutdesigner.model.LayoutNode
import com.codeforge.feature.layoutdesigner.model.SizeMode
import com.codeforge.feature.layoutdesigner.model.parseColorArgb
import com.codeforge.feature.layoutdesigner.model.parseDimension
import com.codeforge.feature.layoutdesigner.model.parseSize

@Immutable
private class PreviewContext(
    val selectedUid: Int?,
    val onSelect: (Int) -> Unit,
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
) {
    val context = remember(selectedUid, onSelect) { PreviewContext(selectedUid, onSelect) }
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
            NodeView(document.root, context, scopeModifier = Modifier)
        }
    }
}

@Composable
private fun NodeView(node: LayoutNode, ctx: PreviewContext, scopeModifier: Modifier) {
    val visibility = node.attr("android:visibility")
    if (visibility == "gone") return

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
        Column(
            modifier = modifier.clip(androidx.compose.ui.graphics.RectangleShape),
            verticalArrangement = when {
                centerV -> Arrangement.Center
                bottom -> Arrangement.Bottom
                else -> Arrangement.Top
            },
            horizontalAlignment = when {
                centerH -> Alignment.CenterHorizontally
                end -> Alignment.End
                else -> Alignment.Start
            },
        ) {
            node.children.forEach { child -> NodeView(child, ctx, columnChildModifier(child)) }
        }
    } else {
        Row(
            modifier = modifier.clip(androidx.compose.ui.graphics.RectangleShape),
            horizontalArrangement = when {
                centerH -> Arrangement.Center
                end -> Arrangement.End
                else -> Arrangement.Start
            },
            verticalAlignment = when {
                centerV -> Alignment.CenterVertically
                bottom -> Alignment.Bottom
                else -> Alignment.Top
            },
        ) {
            node.children.forEach { child -> NodeView(child, ctx, rowChildModifier(child)) }
        }
    }
}

private fun ColumnScope.columnChildModifier(child: LayoutNode): Modifier {
    var m: Modifier = Modifier
    val w = child.attr("android:layout_weight")?.toFloatOrNull() ?: 0f
    if (w > 0f) m = m.weight(w, fill = true)
    val g = child.attr("android:layout_gravity").orEmpty().split('|').toSet()
    m = when {
        "center_horizontal" in g || "center" in g -> m.align(Alignment.CenterHorizontally)
        "end" in g || "right" in g -> m.align(Alignment.End)
        else -> m
    }
    return m
}

private fun RowScope.rowChildModifier(child: LayoutNode): Modifier {
    var m: Modifier = Modifier
    val w = child.attr("android:layout_weight")?.toFloatOrNull() ?: 0f
    if (w > 0f) m = m.weight(w, fill = true)
    val g = child.attr("android:layout_gravity").orEmpty().split('|').toSet()
    m = when {
        "center_vertical" in g || "center" in g -> m.align(Alignment.CenterVertically)
        "bottom" in g -> m.align(Alignment.Bottom)
        else -> m
    }
    return m
}

@Composable
private fun FrameView(node: LayoutNode, ctx: PreviewContext, modifier: Modifier) {
    Box(modifier = modifier.clip(androidx.compose.ui.graphics.RectangleShape)) {
        node.children.forEach { child ->
            NodeView(child, ctx, frameChildModifier(child))
        }
    }
}

private fun BoxScope.frameChildModifier(child: LayoutNode): Modifier {
    val g = child.attr("android:layout_gravity").orEmpty().split('|').map { it.trim() }.toSet()
    val horizontal = when {
        "center_horizontal" in g || "center" in g -> 1
        "end" in g || "right" in g -> 2
        else -> 0
    }
    val vertical = when {
        "center_vertical" in g || "center" in g -> 1
        "bottom" in g -> 2
        else -> 0
    }
    val alignment = when (vertical * 3 + horizontal) {
        0 -> Alignment.TopStart
        1 -> Alignment.TopCenter
        2 -> Alignment.TopEnd
        3 -> Alignment.CenterStart
        4 -> Alignment.Center
        5 -> Alignment.CenterEnd
        6 -> Alignment.BottomStart
        7 -> Alignment.BottomCenter
        else -> Alignment.BottomEnd
    }
    return Modifier.align(alignment)
}

@Composable
private fun LeafView(node: LayoutNode, tag: String, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val text = node.attr("android:text").orEmpty()
    val textColor = parseColorArgb(node.attr("android:textColor"))?.let { Color(it.toInt()) }
    val textSize = (parseDimension(node.attr("android:textSize")) ?: 14f).sp
    val style = node.attr("android:textStyle").orEmpty()
    val weight = if ("bold" in style) FontWeight.Bold else FontWeight.Normal
    val fontStyle = if ("italic" in style) FontStyle.Italic else FontStyle.Normal
    val checked = node.attr("android:checked") == "true"

    when (tag) {
        "TextView" -> Text(
            text = text.ifEmpty { tag },
            modifier = modifier,
            color = textColor ?: scheme.onSurface,
            fontSize = textSize,
            fontWeight = weight,
            fontStyle = fontStyle,
        )
        "Button" -> Surface(
            modifier = modifier.defaultMinSize(minWidth = 64.dp, minHeight = 40.dp),
            shape = RoundedCornerShape(20.dp),
            color = scheme.primary,
        ) {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(
                    text.ifEmpty { tag },
                    color = textColor ?: scheme.onPrimary,
                    fontSize = textSize,
                    fontWeight = weight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        "EditText" -> Box(
            modifier = modifier
                .defaultMinSize(minHeight = 48.dp)
                .border(1.dp, scheme.outline, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            val shown = text.ifEmpty { node.attr("android:hint").orEmpty() }
            Text(
                shown,
                color = if (text.isEmpty()) scheme.onSurfaceVariant else (textColor ?: scheme.onSurface),
                fontSize = textSize,
            )
        }
        "CheckBox" -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = checked, onCheckedChange = null)
            Text(text, color = textColor ?: scheme.onSurface, fontSize = textSize)
        }
        "RadioButton" -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = checked, onClick = null)
            Text(text, color = textColor ?: scheme.onSurface, fontSize = textSize)
        }
        "Switch" -> Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = textColor ?: scheme.onSurface, fontSize = textSize)
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
        "ImageView", "ImageButton" -> Box(
            modifier = modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Image, contentDescription = null, tint = scheme.onSurfaceVariant)
        }
        "ProgressBar" -> CircularProgressIndicator(progress = { 0.65f }, modifier = modifier.size(40.dp))
        "Space" -> Spacer(modifier)
        "View" -> Box(modifier.defaultMinSize(minWidth = 8.dp, minHeight = 1.dp))
        else -> Box(
            modifier = modifier
                .defaultMinSize(minWidth = 48.dp, minHeight = 32.dp)
                .border(1.dp, scheme.outline.copy(alpha = 0.6f))
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(tag, color = scheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
