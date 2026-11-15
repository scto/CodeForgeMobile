/**
 * Modul: :feature:plugins
 * @author Thomas Schmid
 */
package com.codeforge.feature.plugins

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.extension.PluginLogLevel
import com.codeforge.core.domain.extension.PluginLogLine
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Zeigt die letzten Protokollzeilen eines Plugins; scrollt zur neuesten. */
@Composable
internal fun PluginLogDialogView(
    dialog: PluginLogDialog,
    onEvent: (PluginsUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(dialog.lines.size) {
        if (dialog.lines.isNotEmpty()) listState.scrollToItem(dialog.lines.lastIndex)
    }
    AlertDialog(
        onDismissRequest = { onEvent(PluginsUiEvent.LogDismissed) },
        modifier = modifier,
        title = { Text(stringRes(R.string.plugins_log_title, dialog.title)) },
        text = {
            if (dialog.lines.isEmpty()) {
                Text(stringRes(R.string.plugins_log_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).horizontalScroll(rememberScrollState())
                ) {
                    itemsIndexed(dialog.lines) { _, line -> LogLineText(line) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onEvent(PluginsUiEvent.LogDismissed) }) { Text(stringRes(R.string.common_schliessen)) } },
        dismissButton = { TextButton(onClick = { onEvent(PluginsUiEvent.LogCleared) }) { Text(stringRes(R.string.plugins_log_clear)) } }
    )
}

@Composable
private fun LogLineText(line: PluginLogLine) {
    val format = remember { SimpleDateFormat("HH:mm:ss", Locale.ROOT) }
    val color = when (line.level) {
        PluginLogLevel.ERROR -> MaterialTheme.colorScheme.error
        PluginLogLevel.WARN -> MaterialTheme.colorScheme.tertiary
        PluginLogLevel.DEBUG -> MaterialTheme.colorScheme.onSurfaceVariant
        PluginLogLevel.INFO -> Color.Unspecified
    }
    val text = buildString {
        append(format.format(Date(line.timeMillis))).append(' ')
        append(line.level.name.first()).append('/').append(line.tag).append(": ").append(line.message)
        line.stackTrace?.let { append('\n').append(it) }
    }
    Text(text, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = color)
}
