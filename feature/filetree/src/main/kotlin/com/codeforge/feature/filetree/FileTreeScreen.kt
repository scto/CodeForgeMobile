/**
 * Modul: :feature:filetree
 * @author Thomas Schmid
 */
package com.codeforge.feature.filetree

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cafe.adriel.bonsai.core.node.BranchNode
import cafe.adriel.bonsai.core.node.Node
import cafe.adriel.bonsai.core.tree.Tree
import cafe.adriel.bonsai.filesystem.FileSystemTree
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import kotlinx.coroutines.flow.collectLatest
import okio.FileSystem
import okio.Path.Companion.toPath
import java.io.File
import java.util.Locale

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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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
            key(uiState.rootPath, uiState.refreshToken) {
                val tree = FileSystemTree(
                    rootPath = uiState.rootPath.toPath(),
                    fileSystem = FileSystem.SYSTEM,
                    selfInclude = false
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                ) {
                    items(
                        items = tree.nodes,
                        key = { it.key }
                    ) { node ->
                        FileTreeNodeRow(
                            node = node,
                            tree = tree,
                            onOpenFile = { onEvent(FileTreeUiEvent.FileOpened(it)) },
                            onContextMenu = { path, isDir ->
                                onEvent(FileTreeUiEvent.ContextMenuRequested(path, isDir))
                            }
                        )
                    }
                }
            }
        }

        SnackbarHost(snackbarHostState)
    }

    uiState.contextTarget?.let { target ->
        FileTreeContextMenuDialog(
            target = target,
            onProperties = { onEvent(FileTreeUiEvent.PropertiesClicked) },
            onRename = { onEvent(FileTreeUiEvent.RenameClicked) },
            onDelete = { onEvent(FileTreeUiEvent.DeleteClicked) },
            onDismiss = { onEvent(FileTreeUiEvent.DismissContextMenu) }
        )
    }

    uiState.propertiesTarget?.let { properties ->
        FileTreePropertiesDialog(
            properties = properties,
            onDismiss = { onEvent(FileTreeUiEvent.DismissProperties) }
        )
    }

    uiState.deleteConfirmTarget?.let { target ->
        FileTreeDeleteConfirmDialog(
            target = target,
            onConfirm = { onEvent(FileTreeUiEvent.ConfirmDelete) },
            onDismiss = { onEvent(FileTreeUiEvent.CancelDelete) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileTreeNodeRow(
    node: Node<okio.Path>,
    tree: Tree<okio.Path>,
    onOpenFile: (String) -> Unit,
    onContextMenu: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val file = node.content.toFile()
    val isDirectory = node is BranchNode
    val isExpanded = (node as? BranchNode)?.isExpanded == true
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val indentWidth = 20.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isDirectory) {
                        tree.toggleExpansion(node)
                    } else {
                        onOpenFile(file.absolutePath)
                    }
                },
                onLongClick = {
                    onContextMenu(file.absolutePath, isDirectory)
                }
            )
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Theme-adaptive durchgehende Einrückungslinien
        if (node.depth > 0) {
            Box(
                modifier = Modifier
                    .width(indentWidth * node.depth)
                    .height(44.dp)
                    .drawBehind {
                        val strokePx = 1.dp.toPx()
                        val stepPx = indentWidth.toPx()
                        for (i in 0 until node.depth) {
                            val lineX = (i * stepPx) + (stepPx / 2f)
                            drawLine(
                                color = outlineVariant,
                                start = Offset(lineX, 0f),
                                end = Offset(lineX, size.height),
                                strokeWidth = strokePx
                            )
                        }
                    }
            )
        }

        // Expand/Collapse Chevron für Ordner, Spacer für Dateien
        if (isDirectory) {
            IconButton(
                onClick = { tree.toggleExpansion(node) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(modifier = Modifier.size(24.dp))
        }

        // Datei- oder Ordner-Icon
        val icon = when {
            isDirectory && isExpanded -> Icons.Default.FolderOpen
            isDirectory -> Icons.Default.Folder
            else -> getFileIcon(file.name)
        }
        val iconTint = if (isDirectory) {
            MaterialTheme.colorScheme.primary
        } else {
            getFileIconTint(file.name, MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 2.dp, end = 8.dp)
                .size(20.dp),
            tint = iconTint
        )

        // 2-zeilige Dateiinfo: Zeile 1 Name, Zeile 2 Größe & Änderungsdatum
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = node.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isDirectory) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = FileTreeFormatters.formatSubtitle(file, isDirectory),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun getFileIcon(fileName: String): ImageVector {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (ext) {
        "kt", "kts", "java" -> Icons.Default.Code
        "xml", "json", "yaml", "yml", "toml", "properties", "gradle" -> Icons.Default.Settings
        "png", "jpg", "jpeg", "webp", "gif", "svg", "bmp" -> Icons.Default.Image
        "md", "txt", "log" -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }
}

private fun getFileIconTint(fileName: String, defaultColor: Color): Color {
    val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (ext) {
        "kt", "kts" -> Color(0xFF7F52FF)
        "java" -> Color(0xFFE76F51)
        "xml", "json", "yaml", "yml", "toml" -> Color(0xFF388E3C)
        "png", "jpg", "jpeg", "webp", "gif", "svg", "bmp" -> Color(0xFF00ACC1)
        "gradle" -> Color(0xFF02303A)
        "md", "txt" -> Color(0xFF5C6BC0)
        else -> defaultColor
    }
}

@Composable
private fun FileTreeContextMenuDialog(
    target: FileTreeContextTarget,
    onProperties: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(File(target.path).name) },
        text = {
            Column {
                Text(
                    text = if (target.isDirectory) stringRes(R.string.common_ordner) else stringRes(R.string.common_datei),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                ListItem(
                    headlineContent = { Text(stringRes(R.string.filetree_eigenschaften)) },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onProperties)
                )
                ListItem(
                    headlineContent = { Text(stringRes(R.string.common_umbenennen)) },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onRename)
                )
                ListItem(
                    headlineContent = {
                        Text(
                            stringRes(R.string.common_loeschen),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable(onClick = onDelete)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) }
        }
    )
}

@Composable
private fun FileTreePropertiesDialog(
    properties: FilePropertiesData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(stringRes(R.string.filetree_eigenschaften))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PropertyRow(label = stringRes(R.string.filetree_name), value = properties.name)
                PropertyRow(label = stringRes(R.string.filetree_pfad), value = properties.path)
                PropertyRow(label = stringRes(R.string.filetree_typ), value = properties.type)
                PropertyRow(label = stringRes(R.string.filetree_groesse), value = properties.sizeFormatted)
                PropertyRow(label = stringRes(R.string.filetree_geaendert), value = properties.modifiedFormatted)
                PropertyRow(label = stringRes(R.string.filetree_berechtigungen), value = properties.permissions)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_schliessen)) }
        }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun FileTreeDeleteConfirmDialog(
    target: FileTreeContextTarget,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val name = File(target.path).name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.filetree_loeschen_titel)) },
        text = { Text(stringRes(R.string.filetree_loeschen_nachricht, name)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringRes(R.string.common_loeschen))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringRes(R.string.common_abbrechen)) }
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
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text) }) { Text(stringRes(R.string.pw_ok)) }
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
