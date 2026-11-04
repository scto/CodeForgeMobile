package com.codeforge.feature.assetstudio.logic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorNode
import androidx.compose.ui.graphics.vector.VectorPath
import java.util.Locale

/** Wandelt einen Compose-[ImageVector] in ein Android-`<vector>`-Drawable (XML) um. */
object VectorXmlWriter {

    /** @param tint überschreibt alle Füll- und Strichfarben (z. B. die Wunschfarbe des Drawables); `null` = Original. */
    fun toXml(vector: ImageVector, tint: Color? = null): String = buildString {
        appendLine("""<vector xmlns:android="http://schemas.android.com/apk/res/android"""")
        appendLine("""    android:width="${num(vector.defaultWidth.value)}dp"""")
        appendLine("""    android:height="${num(vector.defaultHeight.value)}dp"""")
        appendLine("""    android:viewportWidth="${num(vector.viewportWidth)}"""")
        appendLine("""    android:viewportHeight="${num(vector.viewportHeight)}">""")
        writeGroup(vector.root, tint, "    ", isRoot = true)
        appendLine("</vector>")
    }

    private fun StringBuilder.writeGroup(group: VectorGroup, tint: Color?, indent: String, isRoot: Boolean) {
        val inner = if (isRoot) indent else "$indent    "
        if (!isRoot) {
            appendLine("$indent<group")
            appendLine("""$indent    android:rotation="${num(group.rotation)}"""")
            appendLine("""$indent    android:pivotX="${num(group.pivotX)}"""")
            appendLine("""$indent    android:pivotY="${num(group.pivotY)}"""")
            appendLine("""$indent    android:scaleX="${num(group.scaleX)}"""")
            appendLine("""$indent    android:scaleY="${num(group.scaleY)}"""")
            appendLine("""$indent    android:translateX="${num(group.translationX)}"""")
            appendLine("""$indent    android:translateY="${num(group.translationY)}">""")
        }
        for (node: VectorNode in group) {
            when (node) {
                is VectorGroup -> writeGroup(node, tint, inner, isRoot = false)
                is VectorPath -> writePath(node, tint, inner)
            }
        }
        if (!isRoot) appendLine("$indent</group>")
    }

    private fun StringBuilder.writePath(path: VectorPath, tint: Color?, indent: String) {
        val fill = (path.fill as? androidx.compose.ui.graphics.SolidColor)?.value
        val stroke = (path.stroke as? androidx.compose.ui.graphics.SolidColor)?.value
        appendLine("$indent<path")
        if (fill != null) appendLine("""$indent    android:fillColor="${hex(tint ?: fill)}"""")
        if (fill != null && path.fillAlpha != 1f) appendLine("""$indent    android:fillAlpha="${num(path.fillAlpha)}"""")
        if (stroke != null) {
            appendLine("""$indent    android:strokeColor="${hex(tint ?: stroke)}"""")
            appendLine("""$indent    android:strokeWidth="${num(path.strokeLineWidth)}"""")
            if (path.strokeAlpha != 1f) appendLine("""$indent    android:strokeAlpha="${num(path.strokeAlpha)}"""")
            appendLine("""$indent    android:strokeLineCap="${cap(path.strokeLineCap)}"""")
            appendLine("""$indent    android:strokeLineJoin="${join(path.strokeLineJoin)}"""")
        }
        appendLine("""$indent    android:pathData="${pathData(path.pathData)}"/>""")
    }

    private fun cap(c: StrokeCap) = when (c) { StrokeCap.Round -> "round"; StrokeCap.Square -> "square"; else -> "butt" }
    private fun join(j: StrokeJoin) = when (j) { StrokeJoin.Round -> "round"; StrokeJoin.Bevel -> "bevel"; else -> "miter" }

    /** `#AARRGGBB` bzw. `#RRGGBB`, wie es Android-Vector-Drawables erwarten. */
    internal fun hex(c: Color): String {
        val argb = c.toArgbInt()
        return if (argb ushr 24 == 0xFF) String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
        else String.format(Locale.ROOT, "#%08X", argb)
    }

    private fun Color.toArgbInt(): Int {
        fun ch(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()
        return (ch(alpha) shl 24) or (ch(red) shl 16) or (ch(green) shl 8) or ch(blue)
    }

    /** Formatiert eine Zahl für XML. */
    internal fun num(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString() else String.format(Locale.ROOT, "%.4f", v).trimEnd('0').trimEnd('.')

    /** Pfaddaten als Text. */
    internal fun pathData(nodes: List<PathNode>): String = nodes.joinToString("") { n ->
        when (n) {
            is PathNode.Close -> "Z"
            is PathNode.MoveTo -> "M${p(n.x, n.y)}"
            is PathNode.RelativeMoveTo -> "m${p(n.dx, n.dy)}"
            is PathNode.LineTo -> "L${p(n.x, n.y)}"
            is PathNode.RelativeLineTo -> "l${p(n.dx, n.dy)}"
            is PathNode.HorizontalTo -> "H${num(n.x)}"
            is PathNode.RelativeHorizontalTo -> "h${num(n.dx)}"
            is PathNode.VerticalTo -> "V${num(n.y)}"
            is PathNode.RelativeVerticalTo -> "v${num(n.dy)}"
            is PathNode.CurveTo -> "C${p(n.x1, n.y1)} ${p(n.x2, n.y2)} ${p(n.x3, n.y3)}"
            is PathNode.RelativeCurveTo -> "c${p(n.dx1, n.dy1)} ${p(n.dx2, n.dy2)} ${p(n.dx3, n.dy3)}"
            is PathNode.ReflectiveCurveTo -> "S${p(n.x1, n.y1)} ${p(n.x2, n.y2)}"
            is PathNode.RelativeReflectiveCurveTo -> "s${p(n.dx1, n.dy1)} ${p(n.dx2, n.dy2)}"
            is PathNode.QuadTo -> "Q${p(n.x1, n.y1)} ${p(n.x2, n.y2)}"
            is PathNode.RelativeQuadTo -> "q${p(n.dx1, n.dy1)} ${p(n.dx2, n.dy2)}"
            is PathNode.ReflectiveQuadTo -> "T${p(n.x, n.y)}"
            is PathNode.RelativeReflectiveQuadTo -> "t${p(n.dx, n.dy)}"
            is PathNode.ArcTo -> "A${num(n.horizontalEllipseRadius)},${num(n.verticalEllipseRadius)} ${num(n.theta)} " +
                "${if (n.isMoreThanHalf) 1 else 0},${if (n.isPositiveArc) 1 else 0} ${p(n.arcStartX, n.arcStartY)}"
            is PathNode.RelativeArcTo -> "a${num(n.horizontalEllipseRadius)},${num(n.verticalEllipseRadius)} ${num(n.theta)} " +
                "${if (n.isMoreThanHalf) 1 else 0},${if (n.isPositiveArc) 1 else 0} ${p(n.arcStartDx, n.arcStartDy)}"
        }
    }

    private fun p(x: Float, y: Float) = "${num(x)},${num(y)}"
}
