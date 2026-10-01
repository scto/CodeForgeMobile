// Modul: :feature:filetree
package com.codeforge.feature.filetree

import com.codeforge.core.resources.ResGetter

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import okio.Path
import java.io.File

enum class ClipboardMode { CUT, COPY }

data class ClipboardEntry(
    val path: Path,
    val name: String,
    val isDirectory: Boolean,
    val mode: ClipboardMode
)

@Composable
fun ClipboardIndicatorBar(
    clipboard: ClipboardEntry?,
    onClear: () -> Unit
) {
    if (clipboard == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${if (clipboard.mode == ClipboardMode.CUT) "Ausschneiden" else "Kopieren"}: ${clipboard.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        IconButton(onClick = onClear) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Zwischenablage leeren",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun SearchResultsList(
    rootPath: Path,
    query: String,
    uiScale: Float = 1.0f,
    useRegex: Boolean = false,
    caseSensitive: Boolean = false,
    onFileClick: (String) -> Unit,
    onFolderLongClick: (Path, String) -> Unit,
    onFileLongClick: (Path, String) -> Unit
) {
    val results = remember(query, rootPath, useRegex, caseSensitive) {
        val rootFile = File(rootPath.toString())
        if (!rootFile.exists() || query.isBlank()) emptyList<File>()
        else {
            val matchingFiles = mutableListOf<File>()
            rootFile.walkTopDown().maxDepth(5).forEach { file ->
                val name = file.name
                val matches = if (useRegex) {
                    runCatching { Regex(query, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)).containsMatchIn(name) }.getOrDefault(false)
                } else {
                    name.contains(query, ignoreCase = !caseSensitive)
                }
                if (matches) matchingFiles.add(file)
            }
            matchingFiles
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth()) {
        items(results) { file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFileClick(file.absolutePath) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun InlineInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    errorMessage: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    isError = errorMessage != null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(ResGetter.get(com.codeforge.core.resources.R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(ResGetter.get(com.codeforge.core.resources.R.string.action_cancel))
            }
        }
    )
}
