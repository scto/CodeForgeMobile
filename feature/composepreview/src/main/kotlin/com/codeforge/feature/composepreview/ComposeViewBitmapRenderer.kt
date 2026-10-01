// Modul: :feature:composepreview
package com.codeforge.feature.composepreview

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import java.io.ByteArrayOutputStream
import java.lang.reflect.Method

class ComposeViewBitmapRenderer(private val context: Context) {
    fun renderToBitmap(
        widthPx: Int = 1080,
        heightPx: Int = 1920,
        content: @Composable () -> Unit
    ): Bitmap {
        return Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    }
}

@Composable
fun ReflectiveComposableHost(targetMethod: Method) {
    runCatching {
        targetMethod.invoke(null)
    }
}

fun Bitmap.toPngBytes(): ByteArray {
    val stream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.PNG, 100, stream)
    return stream.toByteArray()
}
