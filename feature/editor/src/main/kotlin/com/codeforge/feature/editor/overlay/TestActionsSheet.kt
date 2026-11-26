// Modul: :feature:editor
package com.codeforge.feature.editor.overlay

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Aktionsblatt: Test bzw. Screenshot-Test ausführen, Referenzbilder aktualisieren, Ergebnisse ansehen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestActionsSheet(
    state: TestActionsState,
    onAction: (TestGutterAction) -> Unit,
    onOpenImage: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = state.target.label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            state.module?.let { module ->
                Text(
                    text = "${module.gradlePath} · ${toolLabel(module.screenshotTool)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                if (module.screenshotTool == ScreenshotTool.COMPOSE_PLUGIN || module.screenshotTool == ScreenshotTool.TEST_SUITE) {
                    Text(
                        text = stringRes(R.string.test_gutter_module_scope),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
            state.actions.forEach { action ->
                ListItem(
                    headlineContent = { Text(actionLabel(action)) },
                    leadingContent = { Icon(actionIcon(action), contentDescription = null) },
                    modifier = Modifier.clickable { onAction(action) }
                )
            }
            state.images?.let { images -> ResultImages(images, onOpenImage) }
        }
    }
}

@Composable
private fun ResultImages(images: List<String>, onOpenImage: (String) -> Unit) {
    if (images.isEmpty()) {
        Text(
            text = stringRes(R.string.test_gutter_no_results),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(24.dp)
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 140.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .padding(horizontal = 16.dp)
    ) {
        items(images, key = { it }) { path -> ResultThumbnail(path, onOpenImage) }
    }
}

@Composable
private fun ResultThumbnail(path: String, onOpenImage: (String) -> Unit) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(path) }
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.clickable { onOpenImage(path) }
    ) {
        Column {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            ) {
                bitmap?.let { Image(it, contentDescription = File(path).name, contentScale = ContentScale.Fit) }
            }
            Text(
                text = File(path).name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(6.dp)
            )
        }
    }
}

private const val THUMB_PX = 360

private fun decodeThumbnail(path: String): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= THUMB_PX || bounds.outHeight / (sample * 2) >= THUMB_PX) sample *= 2
    BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}.getOrNull()

@Composable
private fun toolLabel(tool: ScreenshotTool): String = when (tool) {
    ScreenshotTool.ROBORAZZI -> "Roborazzi"
    ScreenshotTool.COMPOSE_PLUGIN -> stringRes(R.string.test_gutter_tool_compose_plugin)
    ScreenshotTool.TEST_SUITE -> stringRes(R.string.test_gutter_tool_test_suite)
    ScreenshotTool.NONE -> stringRes(R.string.test_gutter_tool_unit)
}

@Composable
private fun actionLabel(action: TestGutterAction): String = stringRes(
    when (action) {
        TestGutterAction.RUN -> R.string.test_gutter_run
        TestGutterAction.VALIDATE_SCREENSHOTS -> R.string.test_gutter_validate
        TestGutterAction.UPDATE_REFERENCES -> R.string.test_gutter_update_references
        TestGutterAction.SHOW_RESULTS -> R.string.test_gutter_show_results
        TestGutterAction.OPEN_REPORT -> R.string.test_gutter_open_report
    }
)

private fun actionIcon(action: TestGutterAction): ImageVector = when (action) {
    TestGutterAction.RUN -> Icons.Default.PlayArrow
    TestGutterAction.VALIDATE_SCREENSHOTS -> Icons.Default.Compare
    TestGutterAction.UPDATE_REFERENCES -> Icons.Default.CameraAlt
    TestGutterAction.SHOW_RESULTS -> Icons.Default.PhotoLibrary
    TestGutterAction.OPEN_REPORT -> Icons.AutoMirrored.Filled.OpenInNew
}
