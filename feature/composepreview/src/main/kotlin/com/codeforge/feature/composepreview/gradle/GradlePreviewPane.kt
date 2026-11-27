// Modul: :feature:composepreview
package com.codeforge.feature.composepreview.gradle

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.feature.composepreview.GradlePreviewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Genaue Vorschau über Gradle: Rendern anstoßen (läuft im Build-Panel) und die Bilder aus `previews.json` zeigen,
 * bevorzugt die der gewählten Funktion.
 */
@Composable
fun GradlePreviewPane(
    state: GradlePreviewState,
    selectedFunction: String?,
    onRender: (full: Boolean) -> Unit,
    onOpenImage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shown = remember(state.previews, selectedFunction) {
        state.previews.filter { it.functionName == selectedFunction }.ifEmpty { state.previews }
    }
    Column(modifier = modifier.fillMaxSize()) {
        if (state.module == null) {
            Hint(stringRes(R.string.composepreview_gradle_no_project))
            return@Column
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Button(onClick = { onRender(false) }, enabled = !state.waiting) {
                Text(stringRes(R.string.composepreview_gradle_render))
            }
            OutlinedButton(onClick = { onRender(true) }, enabled = !state.waiting) {
                Text(stringRes(R.string.composepreview_gradle_render_full))
            }
        }
        if (state.waiting) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        val status = if (state.hasManifest) {
            stringRes(
                R.string.composepreview_gradle_status,
                state.module,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.updatedAt))
            )
        } else {
            stringRes(R.string.composepreview_gradle_first_run, state.module)
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        when {
            !state.hasManifest -> Unit
            shown.isEmpty() -> Hint(stringRes(R.string.composepreview_gradle_none_in_file))
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp)
            ) {
                items(shown, key = { it.id }) { preview -> PreviewCard(preview, state.updatedAt, onOpenImage) }
            }
        }
    }
}

@Composable
private fun PreviewCard(preview: GradlePreviewImage, stamp: Long, onOpenImage: (String) -> Unit) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, preview.image, stamp) {
        value = preview.image?.let { path -> withContext(Dispatchers.IO) { decode(path) } }
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = preview.image != null) { preview.image?.let(onOpenImage) }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = listOfNotNull(preview.functionName, preview.variant).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 560.dp).padding(top = 6.dp)
            ) {
                val image = bitmap
                if (image != null) {
                    Image(image, contentDescription = preview.functionName, contentScale = ContentScale.Fit)
                } else {
                    Text(
                        text = stringRes(R.string.composepreview_gradle_not_rendered),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val MAX_PX = 1600

// Große Renderings verkleinert laden (Speicher), das Original öffnet der Bildbetrachter.
private fun decode(path: String): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MAX_PX || bounds.outHeight / (sample * 2) >= MAX_PX) sample *= 2
    if (!File(path).isFile) return@runCatching null
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()
