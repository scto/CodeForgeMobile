// Modul: :feature:themebuilder
package com.codeforge.feature.themebuilder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

private const val SAMPLE_EDGE = 128

/** Liest ein Bild verkleinert (≤ 128 px Kante) als ARGB-Pixel; `null`, wenn es sich nicht lesen lässt. */
internal fun loadSamplePixels(context: Context, uri: Uri): IntArray? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= SAMPLE_EDGE && bounds.outHeight / (sample * 2) >= SAMPLE_EDGE) sample *= 2
    val decoded = bounds.takeIf { it.outWidth > 0 && it.outHeight > 0 }?.let {
        resolver.openInputStream(uri)?.use { s -> BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    } ?: return null
    val scale = SAMPLE_EDGE.toFloat() / maxOf(decoded.width, decoded.height)
    val bitmap = if (scale < 1f) {
        Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
    } else {
        decoded
    }
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    if (bitmap !== decoded) bitmap.recycle()
    decoded.recycle()
    return pixels
}
