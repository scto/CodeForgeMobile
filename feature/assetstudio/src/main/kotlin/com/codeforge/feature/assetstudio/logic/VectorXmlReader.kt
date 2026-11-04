package com.codeforge.feature.assetstudio.logic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import org.w3c.dom.Element
import org.w3c.dom.Node
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Liest ein Android-`<vector>`-Drawable in einen [ImageVector] (für Vorschau, Einfärben, PNG-Rendering).
 * Unterstützt `group`, `path`, Hex-Farben, Alpha und Striche; `@color/…`-Referenzen und Gradients werden als Schwarz gelesen.
 */
object VectorXmlReader {

    private const val NS = "http://schemas.android.com/apk/res/android"

    /** Liest Vektor-XML als [ImageVector]; `null` bei Fehler. */
    fun parse(xml: String): ImageVector? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val root = factory.newDocumentBuilder().parse(xml.byteInputStream()).documentElement
        if (root.tagName != "vector") return null
        val vw = root.dim("viewportWidth") ?: return null
        val vh = root.dim("viewportHeight") ?: return null
        val builder = ImageVector.Builder(
            name = "imported",
            defaultWidth = (root.dim("width") ?: vw).dp,
            defaultHeight = (root.dim("height") ?: vh).dp,
            viewportWidth = vw,
            viewportHeight = vh,
        )
        readChildren(root, builder)
        builder.build()
    }.getOrNull()

    private fun readChildren(parent: Element, b: ImageVector.Builder) {
        var n: Node? = parent.firstChild
        while (n != null) {
            if (n is Element) when (n.tagName) {
                "group" -> {
                    b.addGroup(
                        rotate = n.dim("rotation") ?: 0f,
                        pivotX = n.dim("pivotX") ?: 0f,
                        pivotY = n.dim("pivotY") ?: 0f,
                        scaleX = n.dim("scaleX") ?: 1f,
                        scaleY = n.dim("scaleY") ?: 1f,
                        translationX = n.dim("translateX") ?: 0f,
                        translationY = n.dim("translateY") ?: 0f,
                    )
                    readChildren(n, b)
                    b.clearGroup()
                }
                "path" -> readPath(n, b)
            }
            n = n.nextSibling
        }
    }

    private fun readPath(e: Element, b: ImageVector.Builder) {
        val data = e.attr("pathData") ?: return
        val nodes = PathParser().parsePathString(data).toNodes()
        val fill = e.attr("fillColor")?.let(::color)
        val stroke = e.attr("strokeColor")?.let(::color)
        b.addPath(
            pathData = nodes,
            fill = fill?.let { SolidColor(it) },
            fillAlpha = e.dim("fillAlpha") ?: 1f,
            stroke = stroke?.let { SolidColor(it) },
            strokeAlpha = e.dim("strokeAlpha") ?: 1f,
            strokeLineWidth = e.dim("strokeWidth") ?: 0f,
            strokeLineCap = when (e.attr("strokeLineCap")) { "round" -> StrokeCap.Round; "square" -> StrokeCap.Square; else -> StrokeCap.Butt },
            strokeLineJoin = when (e.attr("strokeLineJoin")) { "round" -> StrokeJoin.Round; "bevel" -> StrokeJoin.Bevel; else -> StrokeJoin.Miter },
        )
        if (fill == null && stroke == null) {
            // Android füllt Pfade ohne fillColor nicht; wir zeichnen sie ebenfalls nicht → nichts hinzufügen.
        }
    }

    private fun Element.attr(name: String): String? =
        getAttributeNS(NS, name).takeIf { it.isNotEmpty() } ?: getAttribute("android:$name").takeIf { it.isNotEmpty() }

    private fun Element.dim(name: String): Float? =
        attr(name)?.trim()?.removeSuffix("dp")?.removeSuffix("dip")?.removeSuffix("px")?.toFloatOrNull()

    private fun color(text: String): Color =
        AssetFilePlanner.parseColor(text.takeIf { it.startsWith("#") } ?: "#000000")?.let { Color(it) } ?: Color.Black
}
