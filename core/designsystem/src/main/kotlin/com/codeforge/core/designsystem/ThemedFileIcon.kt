// Modul: :core:designsystem
package com.codeforge.core.designsystem

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.codeforge.core.domain.extension.ExtensionIconTheme
import java.io.File

/** Aktives Plugin-Icon-Theme; `null` = App-Standardicons. Wird von [CodeForgeTheme] gesetzt. */
val LocalIconTheme = compositionLocalOf<ExtensionIconTheme?> { null }

/** Lädt Icon-Bilder (PNG/WebP/JPEG) verkleinert und cacht sie; Fehler ergeben `null` (→ Standardicon). */
internal object IconBitmapCache {
    private const val TARGET_PX = 96
    private val cache = object : LruCache<String, ImageBitmap>(256) {}
    private val missing = java.util.Collections.synchronizedSet(HashSet<String>())

    fun get(path: String): ImageBitmap? {
        cache.get(path)?.let { return it }
        if (path in missing) return null
        val loaded = runCatching {
            val file = File(path)
            if (!file.isFile) return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= TARGET_PX && bounds.outHeight / (sample * 2) >= TARGET_PX) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }.getOrNull()
        if (loaded != null) cache.put(path, loaded) else missing.add(path)
        return loaded
    }
}

/** Pfad des Icon-Bildes für [name] im aktiven Theme oder `null`, wenn keins passt/lesbar ist. */
internal fun ExtensionIconTheme.bitmapFor(name: String, isDirectory: Boolean, isExpanded: Boolean): ImageBitmap? {
    val rel = if (isDirectory) icons.forFolder(name, isExpanded) else icons.forFile(name)
    return rel?.let { IconBitmapCache.get(File(baseDir, it).path) }
}

/**
 * Zeichnet das Icon einer Datei/eines Ordners aus dem aktiven Plugin-Icon-Theme; ohne Theme oder ohne
 * passendes Bild wird [fallback] (das bisherige Material-Icon) gezeichnet. Plugin-Icons werden nicht eingefärbt.
 */
@Composable
fun ThemedFileIcon(
    fileName: String,
    isDirectory: Boolean,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false,
    fallback: @Composable (Modifier) -> Unit
) {
    val theme = LocalIconTheme.current
    val bitmap = if (theme == null) null else remember(theme, fileName, isDirectory, isExpanded) {
        theme.bitmapFor(fileName, isDirectory, isExpanded)
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier, contentScale = ContentScale.Fit)
    } else {
        fallback(modifier)
    }
}

/** Wie [ThemedFileIcon], zeichnet aber nichts, wenn kein Plugin-Icon passt (für Stellen ohne bisheriges Icon: Tabs, Suche, Git). */
@Composable
fun OptionalThemedFileIcon(
    fileName: String,
    modifier: Modifier = Modifier,
    isDirectory: Boolean = false
) {
    ThemedFileIcon(fileName = fileName, isDirectory = isDirectory, modifier = modifier) { }
}
