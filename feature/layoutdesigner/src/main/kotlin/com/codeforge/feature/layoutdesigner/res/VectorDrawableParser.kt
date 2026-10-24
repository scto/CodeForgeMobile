/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Parst Android-Vektorgrafiken (<vector ...>) in Compose [ImageVector]-Instanzen.
 */
package com.codeforge.feature.layoutdesigner.res

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.codeforge.feature.layoutdesigner.model.parseColorArgb
import com.codeforge.feature.layoutdesigner.model.parseDimension
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

object VectorDrawableParser {

    fun parse(inputStream: InputStream): ImageVector? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val doc = try {
            factory.newDocumentBuilder().parse(inputStream)
        } catch (_: Exception) {
            return null
        }

        val root = doc.documentElement ?: return null
        if (root.tagName != "vector") return null

        val widthDp = parseDimension(root.getAttribute("android:width")) ?: 24f
        val heightDp = parseDimension(root.getAttribute("android:height")) ?: 24f
        val vpWidth = root.getAttribute("android:viewportWidth").toFloatOrNull() ?: widthDp
        val vpHeight = root.getAttribute("android:viewportHeight").toFloatOrNull() ?: heightDp

        val builder = ImageVector.Builder(
            name = "ParsedVector",
            defaultWidth = widthDp.dp,
            defaultHeight = heightDp.dp,
            viewportWidth = vpWidth,
            viewportHeight = vpHeight,
        )

        parseGroupOrPaths(root, builder)
        return try {
            builder.build()
        } catch (_: Exception) {
            null
        }
    }

    private fun parseGroupOrPaths(parentElem: Element, builder: ImageVector.Builder) {
        val children = parentElem.childNodes
        for (i in 0 until children.length) {
            val n = children.item(i)
            if (n.nodeType != Node.ELEMENT_NODE) continue
            val elem = n as Element
            when (elem.tagName) {
                "path" -> {
                    val pathData = elem.getAttribute("android:pathData")
                    if (pathData.isEmpty()) continue
                    val fillColorRaw = elem.getAttribute("android:fillColor")
                    val strokeColorRaw = elem.getAttribute("android:strokeColor")
                    val strokeWidthRaw = elem.getAttribute("android:strokeWidth")

                    val fill = parseColorArgb(fillColorRaw)?.let { SolidColor(Color(it.toInt())) }
                    val stroke = parseColorArgb(strokeColorRaw)?.let { SolidColor(Color(it.toInt())) }
                    val strokeW = strokeWidthRaw.toFloatOrNull() ?: 0f

                    try {
                        val pathNodes = PathParser().parsePathString(pathData).toNodes()
                        builder.addPath(
                            pathData = pathNodes,
                            fill = fill,
                            stroke = stroke,
                            strokeLineWidth = strokeW,
                        )
                    } catch (_: Exception) {
                        // Ungültige Pfaddaten ignorieren
                    }
                }
                "group" -> {
                    // Rekursiv Kinder der Gruppe verarbeiten
                    parseGroupOrPaths(elem, builder)
                }
            }
        }
    }
}
