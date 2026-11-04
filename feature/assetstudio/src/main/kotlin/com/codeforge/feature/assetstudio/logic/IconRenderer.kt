package com.codeforge.feature.assetstudio.logic

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import java.io.ByteArrayOutputStream

/** Rendert Icons (Vektor oder Bitmap, mit Form und Hintergrund) zu PNG – für die Legacy-Launcher-Icons. */
object IconRenderer {

    /** Legacy-Launcher-Dichten und Kantenlängen in Pixel. */
    val LEGACY_SIZES = listOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)

    /** Motiv des Symbols. */
    sealed interface Motif {
        class Raster(val image: ImageBitmap) : Motif
        class Vector(val vector: ImageVector, val tint: Color?) : Motif
    }

    /** Legacy-PNGs `mipmap-<dpi>/<name>.png` (gewählte Form) und `<name>_round.png` (Kreis). */
    fun planLegacyPngs(name: String, motif: Motif, shape: IconShape, backgroundArgb: Int, scale: Float): List<PlannedFile> =
        LEGACY_SIZES.flatMap { (dpi, px) ->
            listOf(
                PlannedFile("mipmap-$dpi/$name.png", renderPng(px, motif, shape, backgroundArgb, scale)),
                PlannedFile("mipmap-$dpi/${name}_round.png", renderPng(px, motif, IconShape.CIRCLE, backgroundArgb, scale)),
            )
        }

    /** Rendert das Symbol als PNG. */
    fun renderPng(px: Int, motif: Motif, shape: IconShape, backgroundArgb: Int, scale: Float): ByteArray {
        val bmp = render(px, motif, shape, backgroundArgb, scale).asAndroidBitmap()
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    /** Rendert das Symbol als Bitmap. */
    fun render(px: Int, motif: Motif, shape: IconShape, backgroundArgb: Int, scale: Float): ImageBitmap {
        val target = ImageBitmap(px, px)
        val canvas = Canvas(target)
        val size = px.toFloat()
        canvas.save()
        canvas.clipPath(shapePath(shape, size))
        canvas.drawRect(Rect(0f, 0f, size, size), Paint().apply { color = Color(backgroundArgb) })
        val inner = size * scale.coerceIn(0.1f, 1f)
        val off = (size - inner) / 2f
        when (motif) {
            is Motif.Raster -> canvas.drawImageRect(
                image = motif.image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(motif.image.width, motif.image.height),
                dstOffset = IntOffset(off.toInt(), off.toInt()),
                dstSize = IntSize(inner.toInt(), inner.toInt()),
                paint = Paint().apply { isAntiAlias = true; filterQuality = androidx.compose.ui.graphics.FilterQuality.High },
            )
            is Motif.Vector -> {
                canvas.save()
                canvas.translate(off, off)
                drawVector(canvas, motif.vector, inner, motif.tint)
                canvas.restore()
            }
        }
        canvas.restore()
        return target
    }

    private fun shapePath(shape: IconShape, size: Float): Path = Path().apply {
        when (shape) {
            IconShape.CIRCLE -> addOval(Rect(0f, 0f, size, size))
            IconShape.ROUNDED -> addRoundRect(RoundRect(Rect(0f, 0f, size, size), CornerRadius(size * 0.22f)))
            IconShape.SQUARE -> addRect(Rect(0f, 0f, size, size))
        }
    }

    /** Zeichnet [vector] quadratisch in [sizePx] (Viewport wird auf die Fläche skaliert). */
    fun drawVector(canvas: Canvas, vector: ImageVector, sizePx: Float, tint: Color?) {
        val s = sizePx / maxOf(vector.viewportWidth, vector.viewportHeight)
        canvas.save()
        canvas.scale(s, s)
        drawGroup(canvas, vector.root, tint)
        canvas.restore()
    }

    private fun drawGroup(canvas: Canvas, group: VectorGroup, tint: Color?) {
        canvas.save()
        canvas.translate(group.translationX + group.pivotX, group.translationY + group.pivotY)
        canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)
        for (node in group) {
            when (node) {
                is VectorGroup -> drawGroup(canvas, node, tint)
                is VectorPath -> {
                    val path = node.pathData.toPath()
                    (node.fill as? SolidColor)?.let { f ->
                        canvas.drawPath(path, Paint().apply {
                            isAntiAlias = true
                            style = PaintingStyle.Fill
                            color = (tint ?: f.value).copy(alpha = node.fillAlpha * (tint ?: f.value).alpha)
                        })
                    }
                    (node.stroke as? SolidColor)?.let { st ->
                        canvas.drawPath(path, Paint().apply {
                            isAntiAlias = true
                            style = PaintingStyle.Stroke
                            strokeWidth = node.strokeLineWidth
                            color = (tint ?: st.value).copy(alpha = node.strokeAlpha * (tint ?: st.value).alpha)
                        })
                    }
                }
            }
        }
        canvas.restore()
    }
}
