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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import com.codeforge.core.designsystem.ThemedFileIcon
import com.codeforge.core.ui.TooltipIconButton
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest

@Composable
fun FileTreeRoute(
    modifier: Modifier = Modifier,
    rootPath: String,
    onOpenFile: (path: String) -> Unit,
    onOpenApkAnalyzer: (path: String) -> Unit = {},
    onScanHardcodedStrings: (path: String) -> Unit = {},
    onConvertLayoutToCompose: (path: String) -> Unit = {},
    viewModel: FileTreeViewModel = hiltViewModel()
) {
    LaunchedEffect(rootPath) { viewModel.initialize(rootPath) }

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is FileTreeUiEffect.FileOpenedInEditor -> onOpenFile(effect.path)
                is FileTreeUiEffect.OpenApkAnalyzer -> onOpenApkAnalyzer(effect.path)
                is FileTreeUiEffect.ScanHardcodedStrings -> onScanHardcodedStrings(effect.path)
                is FileTreeUiEffect.ConvertLayoutToCompose -> onConvertLayoutToCompose(effect.path)
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            Row {
                TooltipIconButton(onClick = { onEvent(FileTreeUiEvent.CreateFileClicked) }, label = stringRes(R.string.filetree_neue_datei)) {
                    Icon(Icons.Filled.NoteAdd, contentDescription = stringRes(R.string.filetree_neue_datei))
                }
                TooltipIconButton(onClick = { onEvent(FileTreeUiEvent.CreateDirectoryClicked) }, label = stringRes(R.string.filetree_neuer_ordner)) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = stringRes(R.string.filetree_neuer_ordner))
                }
                TooltipIconButton(onClick = { onEvent(FileTreeUiEvent.Refresh) }, label = stringRes(R.string.common_aktualisieren)) {
                    Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.common_aktualisieren))
                }
                FileTreeToolsMenu(tools = uiState.tools, onEvent = onEvent)
                FileTreeOptionsMenu(options = uiState.options, onEvent = onEvent)
            }
        }

        if (uiState.rootPath.isNotBlank()) {
            // Unter horizontalScroll ist die Breite unbegrenzt: ein Kind mit weight(1f) bekommt dort
            // Breite 0 (Text unsichtbar, nur Icons sichtbar). Deshalb: Zeilen ohne weight, mindestens
            // so breit wie der sichtbare Bereich (viewportWidth), längere Namen scrollen horizontal.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val viewportWidth = maxWidth
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                ) {
                    items(items = uiState.entries, key = { it.key }) { entry ->
                        FileTreeNodeRow(
                            entry = entry,
                            gitStatus = uiState.gitStatus,
                            onToggle = { onEvent(FileTreeUiEvent.ToggleExpanded(entry.key)) },
                            onOpenFile = { onEvent(FileTreeUiEvent.FileOpened(it)) },
                            onContextMenu = { path, isDir ->
                                onEvent(FileTreeUiEvent.ContextMenuRequested(path, isDir))
                            },
                            modifier = Modifier.widthIn(min = viewportWidth)
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
            onAnalyzeApk = { onEvent(FileTreeUiEvent.AnalyzeApkClicked) },
            onScanHardcodedStrings = { onEvent(FileTreeUiEvent.ScanHardcodedStringsClicked) },
            onConvertLayoutToCompose = { onEvent(FileTreeUiEvent.ConvertLayoutToComposeClicked) },
            pluginEntries = uiState.pluginMenuEntries,
            onPluginEntry = { onEvent(FileTreeUiEvent.RunPluginEntry(it.pluginId, it.commandId)) },
            onDismiss = { onEvent(FileTreeUiEvent.DismissContextMenu) }
        )
    }

    uiState.toolDialog?.let { dialog -> FileTreeToolDialog(dialog = dialog, onEvent = onEvent) }

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
/** Farbe des Namens nach Git-Status: Konflikt rot, geändert amber, neu/hinzugefügt grün; Ordner mit Änderungen amber. */
@Composable
private fun gitNameColor(path: String, isDirectory: Boolean, status: FileTreeGitStatus): Color {
    val status0 = status.files[path]
    return when {
        status0 == com.codeforge.core.domain.model.GitFileStatus.CONFLICTING -> MaterialTheme.colorScheme.error
        status0 == com.codeforge.core.domain.model.GitFileStatus.MODIFIED -> Color(0xFFD9A441)
        status0 == com.codeforge.core.domain.model.GitFileStatus.ADDED ||
            status0 == com.codeforge.core.domain.model.GitFileStatus.UNTRACKED -> Color(0xFF4CAF50)
        isDirectory && path in status.dirsWithChanges -> Color(0xFFD9A441)
        else -> Color.Unspecified
    }
}

@Composable
private fun FileTreeNodeRow(
    entry: FileTreeEntry,
    gitStatus: FileTreeGitStatus,
    onToggle: () -> Unit,
    onOpenFile: (String) -> Unit,
    onContextMenu: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val file = remember(entry.path) { File(entry.path) }
    val isDirectory = entry.isDirectory
    val isExpandable = entry.expandable
    val isExpanded = entry.expanded
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val indentWidth = 20.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {
                    if (isExpandable) onToggle() else onOpenFile(file.absolutePath)
                },
                onLongClick = {
                    if (entry.isReal) onContextMenu(file.absolutePath, isDirectory)
                }
            )
            .padding(vertical = 4.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Theme-adaptive durchgehende Einrückungslinien
        if (entry.depth > 0) {
            Box(
                modifier = Modifier
                    .width(indentWidth * entry.depth)
                    .height(44.dp)
                    .drawBehind {
                        val strokePx = 1.dp.toPx()
                        val stepPx = indentWidth.toPx()
                        for (i in 0 until entry.depth) {
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
        if (isExpandable) {
            IconButton(
                onClick = onToggle,
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

        if (entry.kind == FileTreeEntryKind.MODULE || entry.kind == FileTreeEntryKind.GROUP) {
            Icon(
                imageVector = if (entry.kind == FileTreeEntryKind.MODULE) Icons.Default.Inventory2 else Icons.Default.FolderSpecial,
                contentDescription = null,
                modifier = Modifier.padding(start = 2.dp, end = 8.dp).size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        } else {
            ThemedFileIcon(
                fileName = file.name,
                isDirectory = isDirectory,
                isExpanded = isExpanded,
                modifier = Modifier
                    .padding(start = 2.dp, end = 8.dp)
                    .size(20.dp)
            ) { iconModifier ->
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = iconModifier,
                    tint = iconTint
                )
            }
        }

        // 2-zeilige Dateiinfo: Zeile 1 Name, Zeile 2 Größe & Änderungsdatum
        Column {
            Text(
                text = entry.group?.let { groupLabel(it) } ?: entry.label +
                    (entry.detail?.let { " $it" } ?: ""),
                color = if (entry.isReal) gitNameColor(file.absolutePath, isDirectory, gitStatus) else Color.Unspecified,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isExpandable) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.isReal) {
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
}

@Composable
private fun groupLabel(group: FileTreeGroup): String = when (group) {
    FileTreeGroup.MANIFESTS -> stringRes(R.string.filetree_gruppe_manifests)
    FileTreeGroup.SOURCES -> stringRes(R.string.filetree_gruppe_quellen)
    FileTreeGroup.RESOURCES -> stringRes(R.string.filetree_gruppe_res)
    FileTreeGroup.ASSETS -> stringRes(R.string.filetree_gruppe_assets)
    FileTreeGroup.OTHER -> stringRes(R.string.filetree_gruppe_weitere)
    FileTreeGroup.GRADLE_SCRIPTS -> stringRes(R.string.filetree_gruppe_gradle)
    FileTreeGroup.PROJECT_FILES -> stringRes(R.string.filetree_gruppe_projektdateien)
}

/** PT-Menü (Plugin-Tools): listet die Werkzeuge aller aktivierten Plugins. */
@Composable
private fun FileTreeToolsMenu(tools: List<FileTreeTool>, onEvent: (FileTreeUiEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TooltipIconButton(onClick = { open = true }, label = stringRes(R.string.filetree_tools)) {
            Text("PT", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (tools.isEmpty()) {
                DropdownMenuItem(text = { Text(stringRes(R.string.filetree_tools_leer)) }, enabled = false, onClick = {})
            }
            tools.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(item.tool.label)
                            if (item.tool.description.isNotBlank()) {
                                Text(
                                    item.tool.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.Extension, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        open = false
                        onEvent(FileTreeUiEvent.ToolClicked(item.pluginId, item.tool.id))
                    }
                )
            }
        }
    }
}

@Composable
private fun FileTreeToolDialog(dialog: ToolDialogState, onEvent: (FileTreeUiEvent) -> Unit) {
    val dismiss = { onEvent(FileTreeUiEvent.ToolDialogDismissed) }
    when (dialog) {
        is ToolDialogState.Form -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(dialog.item.tool.label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    dialog.item.tool.confirm?.let { Text(it) }
                    dialog.item.tool.prompt?.let { prompt ->
                        OutlinedTextField(
                            value = dialog.input,
                            onValueChange = { onEvent(FileTreeUiEvent.ToolInputChanged(it)) },
                            label = { Text(prompt.label) },
                            placeholder = if (prompt.hint.isNotBlank()) ({ Text(prompt.hint) }) else null,
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(FileTreeUiEvent.ToolFormConfirmed) },
                    enabled = dialog.item.tool.prompt == null || dialog.input.isNotBlank()
                ) { Text(stringRes(R.string.filetree_tool_start)) }
            },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } }
        )

        is ToolDialogState.Install -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.filetree_tool_installieren_titel)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringRes(R.string.filetree_tool_installieren_text, dialog.item.tool.label))
                    dialog.missing.forEach { r ->
                        Text("• ${r.manager.id}: ${r.name}", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onEvent(FileTreeUiEvent.ToolInstallConfirmed) }) { Text(stringRes(R.string.filetree_tool_installieren)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } }
        )

        is ToolDialogState.Running -> {
            val scroll = rememberScrollState()
            LaunchedEffect(dialog.lines.size) { scroll.animateScrollTo(scroll.maxValue) }
            AlertDialog(
                onDismissRequest = {},
                title = { Text(dialog.item.tool.label) },
                text = {
                    Column {
                        Text(
                            text = when {
                                dialog.phase == ToolPhase.INSTALLING -> stringRes(R.string.filetree_tool_installiert)
                                dialog.phase == ToolPhase.RUNNING -> stringRes(R.string.filetree_tool_laeuft)
                                dialog.timedOut -> stringRes(R.string.filetree_tool_timeout)
                                else -> stringRes(R.string.filetree_tool_fertig, dialog.exitCode ?: -1)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (dialog.phase == ToolPhase.DONE && dialog.exitCode != 0) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = dialog.lines.joinToString("\n"),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(scroll)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = dismiss) {
                        Text(stringRes(if (dialog.phase == ToolPhase.DONE) R.string.common_schliessen else R.string.common_abbrechen))
                    }
                }
            )
        }
    }
}

/** Drei-Punkte-Menü: Sortiermodus, Sortieren nach, Reihenfolge, Ansicht, Versteckte anzeigen. */
@Composable
private fun FileTreeOptionsMenu(options: FileTreeOptions, onEvent: (FileTreeUiEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TooltipIconButton(onClick = { open = true }, label = stringRes(R.string.filetree_einstellungen)) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringRes(R.string.filetree_einstellungen))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            MenuSection(R.string.filetree_menu_sortmodus)
            MenuChoice(R.string.filetree_sortmodus_gruppiert, options.sortMode == FileTreeSortMode.GROUPED) {
                onEvent(FileTreeUiEvent.SortModeChanged(FileTreeSortMode.GROUPED))
            }
            MenuChoice(R.string.filetree_sortmodus_gemischt, options.sortMode == FileTreeSortMode.MIXED) {
                onEvent(FileTreeUiEvent.SortModeChanged(FileTreeSortMode.MIXED))
            }
            HorizontalDivider()
            MenuSection(R.string.filetree_menu_sortieren_nach)
            MenuChoice(R.string.filetree_sort_name, options.sortBy == FileTreeSortBy.NAME) { onEvent(FileTreeUiEvent.SortByChanged(FileTreeSortBy.NAME)) }
            MenuChoice(R.string.filetree_sort_typ, options.sortBy == FileTreeSortBy.TYPE) { onEvent(FileTreeUiEvent.SortByChanged(FileTreeSortBy.TYPE)) }
            MenuChoice(R.string.filetree_sort_groesse, options.sortBy == FileTreeSortBy.SIZE) { onEvent(FileTreeUiEvent.SortByChanged(FileTreeSortBy.SIZE)) }
            MenuChoice(R.string.filetree_sort_geaendert, options.sortBy == FileTreeSortBy.MODIFIED) { onEvent(FileTreeUiEvent.SortByChanged(FileTreeSortBy.MODIFIED)) }
            MenuChoice(R.string.filetree_sort_absteigend, options.descending) { onEvent(FileTreeUiEvent.DescendingChanged(!options.descending)) }
            HorizontalDivider()
            MenuSection(R.string.filetree_menu_ansicht)
            MenuChoice(R.string.filetree_ansicht_dateien, options.view == FileTreeView.FILES) { onEvent(FileTreeUiEvent.ViewChanged(FileTreeView.FILES)) }
            MenuChoice(R.string.filetree_ansicht_projekt, options.view == FileTreeView.PROJECT) { onEvent(FileTreeUiEvent.ViewChanged(FileTreeView.PROJECT)) }
            MenuChoice(R.string.filetree_ansicht_modul, options.view == FileTreeView.MODULES) { onEvent(FileTreeUiEvent.ViewChanged(FileTreeView.MODULES)) }
            HorizontalDivider()
            MenuChoice(R.string.filetree_versteckte_anzeigen, options.showHidden) { onEvent(FileTreeUiEvent.ShowHiddenChanged(!options.showHidden)) }
        }
    }
}

@Composable
private fun MenuSection(titleRes: Int) {
    Text(
        text = stringRes(titleRes),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

/** Menüpunkt mit Häkchen bei aktiver Auswahl; das Menü bleibt offen, damit mehrere Optionen änderbar sind. */
@Composable
private fun MenuChoice(labelRes: Int, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringRes(labelRes)) },
        leadingIcon = {
            if (selected) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            else Spacer(Modifier.size(18.dp))
        },
        onClick = onClick
    )
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
    onAnalyzeApk: () -> Unit,
    onScanHardcodedStrings: () -> Unit,
    onConvertLayoutToCompose: () -> Unit,
    pluginEntries: List<com.codeforge.core.domain.extension.PluginMenuEntry> = emptyList(),
    onPluginEntry: (com.codeforge.core.domain.extension.PluginMenuEntry) -> Unit = {},
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
                if (!target.isDirectory && target.path.endsWith(".apk", ignoreCase = true)) {
                    ListItem(
                        headlineContent = { Text(stringRes(R.string.apk_analyzer_kontextmenue)) },
                        leadingContent = { Icon(Icons.Default.Analytics, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onAnalyzeApk)
                    )
                }
                if (!target.isDirectory && target.path.endsWith(".xml", ignoreCase = true) && target.path.contains("/res/layout")) {
                    ListItem(
                        headlineContent = { Text(stringRes(R.string.x2c_context_convert_layout)) },
                        leadingContent = { Icon(Icons.Default.Transform, contentDescription = null) },
                        modifier = Modifier.clickable(onClick = onConvertLayoutToCompose)
                    )
                }
                pluginEntries.forEach { entry ->
                    ListItem(
                        headlineContent = { Text(entry.title) },
                        leadingContent = { Icon(Icons.Default.Extension, contentDescription = null) },
                        modifier = Modifier.clickable { onPluginEntry(entry) }
                    )
                }
                ListItem(
                    headlineContent = { Text(stringRes(R.string.hardcoded_strings_kontextmenue)) },
                    leadingContent = { Icon(Icons.Default.Translate, contentDescription = null) },
                    modifier = Modifier.clickable(onClick = onScanHardcodedStrings)
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
