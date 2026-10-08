/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 *
 * Dateibaum auf Basis von Bonsai (https://github.com/adrielcafe/bonsai, Maven Central,
 * `cafe.adriel.bonsai:bonsai-file-system`). Seit der Umstellung "Filetree als
 * NavigationDrawer, Editor als Hauptbildschirm" ist [FileTreeRoute] der Drawer-Inhalt
 * von `ProjectWorkspaceRoute` (:app) statt einer eigenständigen Vollbild-Route.
 *
 * Bonsai-API (verifiziert anhand des offiziellen Samples
 * `sample/.../tree/FileSystemTreeScreen.kt` im Bonsai-Repo):
 *   - `FileSystemTree(rootPath: okio.Path, fileSystem: okio.FileSystem, selfInclude)`
 *     ist selbst `@Composable` und liefert ein `Tree<okio.Path>`.
 *   - `Bonsai(tree, modifier, onClick, onDoubleClick, onLongClick, style)` rendert ihn;
 *     Branch-Knoten (Verzeichnisse) togglen ihre Expansion per `tree.toggleExpansion(node)`.
 */
package com.codeforge.feature.filetree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.bonsai.core.Bonsai
import cafe.adriel.bonsai.core.node.BranchNode
import cafe.adriel.bonsai.core.node.LeafNode
import cafe.adriel.bonsai.filesystem.FileSystemBonsaiStyle
import cafe.adriel.bonsai.filesystem.FileSystemTree
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import kotlinx.coroutines.flow.collectLatest
import okio.FileSystem
import okio.Path.Companion.toPath
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
                is FileTreeUiEffect.FileOpenedInEditor -> onOpenFile(effect.path)
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
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = uiState.rootPath.substringAfterLast('/').ifBlank { Res.string(R.string.common_projekt) },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
            Row {
                IconButton(onClick = { onEvent(FileTreeUiEvent.CreateFileClicked) }) {
                    Icon(Icons.Filled.NoteAdd, contentDescription = stringRes(R.string.filetree_neue_datei))
                }
                IconButton(onClick = { onEvent(FileTreeUiEvent.CreateDirectoryClicked) }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = stringRes(R.string.filetree_neuer_ordner))
                }
                IconButton(onClick = { onEvent(FileTreeUiEvent.Refresh) }) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.common_aktualisieren))
                }
            }
        }

        if (uiState.rootPath.isNotBlank()) {
            // `refreshToken` im Key erzwingt eine neue Tree-/Node-Instanz nach
            // Create/Rename/Delete — siehe KDoc an FileTreeUiState.refreshToken.
            val tree = remember(uiState.rootPath, uiState.refreshToken) {
                FileSystemTree(
                    rootPath = uiState.rootPath.toPath(),
                    fileSystem = FileSystem.SYSTEM,
                    selfInclude = false
                )
            }

            Bonsai(
                tree = tree,
                modifier = Modifier.fillMaxSize(),
                style = FileSystemBonsaiStyle(),
                onClick = { node ->
                    when (node) {
                        is LeafNode -> onEvent(FileTreeUiEvent.FileOpened(node.content.toFile().absolutePath))
                        is BranchNode -> tree.toggleExpansion(node)
                        else -> Unit
                    }
                },
                onLongClick = { node ->
                    onEvent(
                        FileTreeUiEvent.ContextMenuRequested(
                            path = node.content.toFile().absolutePath,
                            isDirectory = node is BranchNode
                        )
                    )
                }
            )
        }

        SnackbarHost(snackbarHostState)
    }

    uiState.contextTarget?.let { target ->
        FileTreeContextMenuDialog(
            target = target,
            onRename = { onEvent(FileTreeUiEvent.RenameClicked) },
            onDelete = { onEvent(FileTreeUiEvent.DeleteClicked) },
            onDismiss = { onEvent(FileTreeUiEvent.DismissContextMenu) }
        )
    }

    uiState.pendingAction?.let { action ->
        FileTreeNameDialog(
            action = action,
            onConfirm = { name -> onEvent(FileTreeUiEvent.ActionConfirmed(name)) },
            onDismiss = { onEvent(FileTreeUiEvent.ActionCancelled) }
        )
    }
}

@Composable
private fun FileTreeContextMenuDialog(
    target: FileTreeContextTarget,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(File(target.path).name) },
        text = { Text(if (target.isDirectory) stringRes(R.string.common_ordner) else stringRes(R.string.common_datei)) },
        confirmButton = {
            TextButton(onClick = onDelete) { Text(stringRes(R.string.common_loeschen)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onRename) { Text(stringRes(R.string.common_umbenennen)) }
                TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) }
            }
        }
    )
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
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) }
        }
    )
}

private fun dialogTitle(type: FileTreeActionType): String = when (type) {
    FileTreeActionType.CREATE_FILE -> Res.string(R.string.filetree_neue_datei)
    FileTreeActionType.CREATE_DIRECTORY -> Res.string(R.string.filetree_neuer_ordner)
    FileTreeActionType.RENAME -> Res.string(R.string.common_umbenennen)
}
