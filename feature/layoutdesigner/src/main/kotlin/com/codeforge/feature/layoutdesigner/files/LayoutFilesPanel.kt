/**
 * Modul: :feature:layoutdesigner
 * @author Thomas Schmid
 *
 * Drawer-Panel: listet die Layout-Dateien des Projekts und legt neue an. Öffnet den Designer
 * über [onOpenDesigner] (Navigation liegt in :app).
 */
package com.codeforge.feature.layoutdesigner.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@Composable
fun LayoutFilesPanel(
    rootPath: String,
    onOpenDesigner: (path: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LayoutFilesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(rootPath) { viewModel.onEvent(LayoutFilesUiEvent.Initialize(rootPath)) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is LayoutFilesUiEffect.OpenDesigner -> onOpenDesigner(effect.path)
            }
        }
    }

    LayoutFilesContent(modifier = modifier, state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun LayoutFilesContent(
    state: LayoutFilesUiState,
    onEvent: (LayoutFilesUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringRes(R.string.layout_files_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { onEvent(LayoutFilesUiEvent.Refresh) }) {
                Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.common_aktualisieren))
            }
            IconButton(onClick = { onEvent(LayoutFilesUiEvent.ShowCreate) }) {
                Icon(Icons.Filled.Add, contentDescription = stringRes(R.string.layout_new))
            }
        }
        if (state.isScanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
        if (!state.isScanning && state.files.isEmpty() && state.error == null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringRes(R.string.layout_files_empty), style = MaterialTheme.typography.bodyMedium)
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(state.files, key = { it.path }) { file ->
                Column(
                    Modifier.fillMaxWidth().clickable { onEvent(LayoutFilesUiEvent.Open(file.path)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(file.path.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        file.relativePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider()
            }
        }
    }

    if (state.showCreate) {
        AlertDialog(
            onDismissRequest = { onEvent(LayoutFilesUiEvent.DismissCreate) },
            title = { Text(stringRes(R.string.layout_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = state.newName,
                        onValueChange = { onEvent(LayoutFilesUiEvent.NameChanged(it)) },
                        label = { Text(stringRes(R.string.layout_new_name)) },
                        singleLine = true,
                        isError = state.createError != null,
                        supportingText = state.createError?.let { { Text(it) } },
                    )
                    Text(stringRes(R.string.layout_new_module), style = MaterialTheme.typography.labelMedium)
                    state.resDirs.forEach { dir ->
                        FilterChip(
                            selected = dir == state.selectedResDir,
                            onClick = { onEvent(LayoutFilesUiEvent.ResDirSelected(dir)) },
                            label = { Text(dir, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onEvent(LayoutFilesUiEvent.ConfirmCreate) }) { Text(stringRes(R.string.layout_create)) }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(LayoutFilesUiEvent.DismissCreate) }) { Text(stringRes(R.string.common_abbrechen)) }
            },
        )
    }
}
