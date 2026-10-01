package com.codeforge.feature.editor.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.util.Xml
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import org.xmlpull.v1.XmlPullParser
import java.io.File

/**
 * Renders Image & Mipmap files directly inside the editor tab.
 * Supports PNG, JPG, WEBP, GIF, ICO, SVG, VectorDrawable XMLs, and Adaptive Icons.
 *
 * @author Thomas Schmid
 */
@Composable
fun ImageFilePreview(
    filePath: String,
    modifier: Modifier = Modifier
) {
    val cleanPath = remember(filePath) {
        filePath.removePrefix("file://").let { runCatching { Uri.decode(it) }.getOrDefault(it) }
    }
    val file = remember(cleanPath) { File(cleanPath) }
    val isXml = remember(cleanPath) { file.extension.lowercase() == "xml" }
    val context = LocalContext.current

    val bitmap = remember(cleanPath) {
        runCatching {
            if (file.exists() && file.isFile && !isXml) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else null
        }.getOrNull()
    }

    val drawable = remember(cleanPath, context) {
        runCatching {
            if (file.exists() && file.isFile) {
                val direct = Drawable.createFromPath(file.absolutePath)
                if (direct != null) direct
                else if (isXml) {
                    loadXmlDrawable(file, context)
                } else null
            } else null
        }.getOrNull()
    }

    val xmlContent = remember(cleanPath) {
        if (isXml && file.exists()) {
            runCatching { file.readText() }.getOrNull()
        } else null
    }

    val fileSizeText = remember(file) {
        if (file.exists()) {
            val bytes = file.length()
            if (bytes < 1024) "$bytes B"
            else if (bytes < 1024 * 1024) "${bytes / 1024} KB"
            else String.format("%.2f MB", bytes.toDouble() / (1024 * 1024))
        } else "0 B"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF1E1E1E),
            modifier = Modifier
                .padding(16.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
        ) {
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .background(Color(0xFF282828)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = file.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                } else if (drawable != null) {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                setImageDrawable(drawable)
                            }
                        },
                        update = { imageView ->
                            imageView.setImageDrawable(drawable)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                try {
                                    setImageURI(Uri.fromFile(file))
                                } catch (_: Exception) {}
                            }
                        },
                        update = { imageView ->
                            try {
                                imageView.setImageURI(Uri.fromFile(file))
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Image Details Info Bar
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = file.absolutePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    val dimText = when {
                        bitmap != null -> "${bitmap.width} × ${bitmap.height} px"
                        drawable != null && drawable.intrinsicWidth > 0 -> "${drawable.intrinsicWidth} × ${drawable.intrinsicHeight} dp"
                        isXml -> "XML Resource"
                        else -> "Resource"
                    }
                    Text(
                        text = dimText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = fileSizeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (xmlContent != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E1E1E),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "XML Resource Structure",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = xmlContent,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = Color(0xFFD4D4D4)
                    )
                }
            }
        }
    }
}

private fun loadXmlDrawable(file: File, context: Context): Drawable? {
    if (!file.exists() || !file.isFile) return null
    return runCatching {
        val text = file.readText()
        if (text.contains("<adaptive-icon")) {
            loadAdaptiveIcon(file, text, context)
        } else {
            file.inputStream().use { stream ->
                val parser = Xml.newPullParser()
                parser.setInput(stream, "UTF-8")
                var type = parser.next()
                while (type != XmlPullParser.START_TAG && type != XmlPullParser.END_DOCUMENT) {
                    type = parser.next()
                }
                if (type == XmlPullParser.START_TAG) {
                    val attrs = Xml.asAttributeSet(parser)
                    if (parser.name == "vector") {
                        VectorDrawableCompat.createFromXmlInner(
                            context.resources,
                            parser,
                            attrs,
                            context.theme
                        )
                    } else {
                        Drawable.createFromXml(context.resources, parser)
                    }
                } else null
            }
        }
    }.getOrNull()
}

private fun loadAdaptiveIcon(file: File, xmlText: String, context: Context): Drawable? {
    return runCatching {
        val resDir = file.parentFile?.parentFile ?: return null
        fun findDrawableFile(refName: String): File? {
            val cleanName = refName.substringAfterLast('/').substringAfterLast(':')
            val candidates = listOf("drawable", "drawable-v24", "mipmap-anydpi-v26", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi")
            val exts = listOf("xml", "png", "webp", "jpg")
            for (dir in candidates) {
                for (ext in exts) {
                    val target = File(resDir, "$dir/$cleanName.$ext")
                    if (target.exists()) return target
                }
            }
            return null
        }

        val bgMatch = Regex("""android:drawable="([^"]+)"""").find(xmlText.substringAfter("<background", ""))
        val fgMatch = Regex("""android:drawable="([^"]+)"""").find(xmlText.substringAfter("<foreground", ""))

        val bgFile = bgMatch?.groupValues?.getOrNull(1)?.let { findDrawableFile(it) }
        val fgFile = fgMatch?.groupValues?.getOrNull(1)?.let { findDrawableFile(it) }

        val bgDrawable = bgFile?.let { loadDrawableFromFile(it, context) }
        val fgDrawable = fgFile?.let { loadDrawableFromFile(it, context) }

        val layers = listOfNotNull(bgDrawable, fgDrawable).toTypedArray()
        if (layers.isNotEmpty()) {
            LayerDrawable(layers)
        } else null
    }.getOrNull()
}

private fun loadDrawableFromFile(file: File, context: Context): Drawable? {
    if (file.extension.lowercase() != "xml") {
        return Drawable.createFromPath(file.absolutePath) ?: BitmapFactory.decodeFile(file.absolutePath)?.let {
            BitmapDrawable(context.resources, it)
        }
    }
    return loadXmlDrawable(file, context)
}

fun isImageFilePath(path: String): Boolean {
    val clean = path.removePrefix("file://").let { runCatching { Uri.decode(it) }.getOrDefault(it) }
    val ext = clean.substringAfterLast('.', "").lowercase()

    if (ext in setOf("png", "jpg", "jpeg", "webp", "gif", "ico", "bmp", "svg")) return true

    if (ext == "xml") {
        val lowerPath = clean.lowercase()
        if (lowerPath.contains("mipmap") || lowerPath.contains("drawable")) {
            val file = File(clean)
            if (file.exists() && file.isFile) {
                return runCatching {
                    val header = file.readText().take(500).lowercase()
                    header.contains("<vector") || header.contains("<adaptive-icon")
                }.getOrDefault(false)
            }
        }
    }
    return false
}
