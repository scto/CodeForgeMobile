// Modul: :feature:filetree
package com.codeforge.feature.filetree

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.collectLatest
import java.io.File

@Composable
fun FileTreeRoute(
    modifier: Modifier = Modifier,
    rootPath: String,
    onOpenFile: (path: String) -> Unit,
    viewModel: FileTreeViewModel = hiltViewModel()
) {
    LaunchedEffect(rootPath) { viewModel.initialize(rootPath) }

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is FileTreeUiEffect.OpenFileInEditor -> onOpenFile(effect.path)
                is FileTreeUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    FileTreeScreen(
        modifier = modifier,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun FileTreeScreen(
    modifier: Modifier = Modifier,
    uiState: FileTreeUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (FileTreeUiEvent) -> Unit
) {
    Scaffold(
        modifier = modifier,
        snackBarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.rootPath.substringAfterLast('/').ifBlank { "Projekt" })
                },
                actions = {
                    IconButton(onClick = { onEvent(FileTreeUiEvent.CreateFileClicked) }) {
                        Icon(Icons.Filled.NoteAdd, contentDescription = "Neue Datei")
                    }
                    IconButton(onClick = { onEvent(FileTreeUiEvent.CreateDirectoryClicked) }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Neuer Ordner")
                    }
                    IconButton(onClick = { onEvent(FileTreeUiEvent.Refresh) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Aktualisieren")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.isLoading && uiState.visibleNodes.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.visibleNodes, key = { it.entry.path }) { node ->
                        FileTreeRow(
                            node = node,
                            isMenuOpen = node.entry.path == uiState.selectedPath,
                            onClick = { onEvent(FileTreeUiEvent.NodeClicked(node.entry)) },
                            onLongClick = { onEvent(FileTreeUiEvent.NodeLongPressed(node.entry)) },
                            onDismissMenu = { onEvent(FileTreeUiEvent.DismissContextMenu) },
                            onRename = { onEvent(FileTreeUiEvent.RenameClicked(node.entry)) },
                            onDelete = { onEvent(FileTreeUiEvent.DeleteClicked(node.entry)) }
                        )
                    }
                }
            }
        }
    }

    uiState.pendingAction?.let { action ->
        FileTreeNameDialog(
            action = action,
            onConfirm = { name -> onEvent(FileTreeUiEvent.ActionConfirmed(name)) },
            onDismiss = { onEvent(FileTreeUiEvent.ActionCancelled) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeRow(
    node: FileTreeNodeUi,
    isMenuOpen: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(
                    start = (16 + node.depth * 16).dp,
                    end = 16.dp,
                    top = 10.dp,
                    bottom = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconFor(node),
                contentDescription = null,
                modifier = Modifier.padding(end = 12.dp)
            )
            Text(node.entry.name, style = MaterialTheme.typography.bodyMedium)
        }

        DropdownMenu(expanded = isMenuOpen, onDismissRequest = onDismissMenu) {
            DropdownMenuItem(text = { Text("Umbenennen") }, onClick = onRename)
            DropdownMenuItem(text = { Text("Löschen") }, onClick = onDelete)
        }
    }
}

private fun iconFor(node: FileTreeNodeUi): ImageVector = when {
    node.entry.isDirectory && node.isExpanded -> Icons.Filled.FolderOpen
    node.entry.isDirectory -> Icons.Filled.Folder
    else -> Icons.Filled.InsertDriveFile
}

@Composable
private fun FileTreeNameDialog(
    action: FileTreeAction,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember {
        mutableStateOf(
            if (action.type == FileTreeActionType.RENAME) File(action.targetPath).name else ""
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle(action.type)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

private fun dialogTitle(type: FileTreeActionType): String = when (type) {
    FileTreeActionType.CREATE_FILE -> "Neue Datei"
    FileTreeActionType.CREATE_DIRECTORY -> "Neuer Ordner"
    FileTreeActionType.RENAME -> "Umbenennen"
}
