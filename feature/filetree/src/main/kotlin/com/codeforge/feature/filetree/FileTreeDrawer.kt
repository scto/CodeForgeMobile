package com.codeforge.feature.filetree

import com.codeforge.core.resources.ResGetter

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cafe.adriel.bonsai.core.Bonsai
import cafe.adriel.bonsai.core.BonsaiStyle
import cafe.adriel.bonsai.core.node.BranchNode
import cafe.adriel.bonsai.core.tree.Tree
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

private data class DirSnapshot(val lastModified: Long, val listing: String)

private enum class InlineMode { None, NewFile, NewDirectory, Rename }

@Composable
fun FileTreeDrawer(
    rootPath: String,
    onFileClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    fileTreeConfig: com.codeforge.core.datastore.proto.FileTreeConfig = com.codeforge.core.datastore.proto.FileTreeConfig.getDefaultInstance(),
    onCloseDrawer: (() -> Unit)? = null
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val navigationBarPadding = with(density) {
        ViewCompat.getRootWindowInsets(view)
            ?.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.navigationBars())
            ?.bottom
            ?.toDp() ?: 0.dp
    }
    val decodedPath = remember(rootPath) {
        val d = android.net.Uri.decode(rootPath)
        if (d.isBlank()) "/storage/emulated/0" else d
    }
    val rootPathOkio = remember(decodedPath) { decodedPath.toPath() }
    var refreshTrigger by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var clipboard by remember { mutableStateOf<ClipboardEntry?>(null) }
    var contextTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }
    var contextMenuOffset by remember { mutableStateOf(IntOffset.Zero) }
    var inlineMode by remember { mutableStateOf(InlineMode.None) }
    var inlineText by remember { mutableStateOf("") }
    var inlineError by remember { mutableStateOf<String?>(null) }
    var isCompactMode by remember { mutableStateOf(true) }
    var searchRegex by remember { mutableStateOf(false) }
    var searchCaseSensitive by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var activeFileTreeConfig by remember(fileTreeConfig) { mutableStateOf(fileTreeConfig) }
    var uiScale by remember(activeFileTreeConfig.fontSize) {
        mutableStateOf(if (activeFileTreeConfig.fontSize > 0) activeFileTreeConfig.fontSize / 10f else 1.2f)
    }
    var showIndentLines by remember(activeFileTreeConfig) {
        mutableStateOf(if (activeFileTreeConfig == com.codeforge.core.datastore.proto.FileTreeConfig.getDefaultInstance() || activeFileTreeConfig.fontSize == 0) true else activeFileTreeConfig.showIndentLines)
    }
    var showFileDetails by remember(activeFileTreeConfig) {
        mutableStateOf(if (activeFileTreeConfig == com.codeforge.core.datastore.proto.FileTreeConfig.getDefaultInstance() || activeFileTreeConfig.fontSize == 0) true else activeFileTreeConfig.showFileDetails)
    }
    val lastTouch = remember { intArrayOf(0, 0) }

    LaunchedEffect(fileTreeConfig) {
        activeFileTreeConfig = fileTreeConfig
        refreshTrigger++
    }

    val dirSnapshots = remember(rootPath) { mutableMapOf<String, DirSnapshot>() }

    LaunchedEffect(rootPathOkio, refreshTrigger) {
        while (true) {
            delay(2000)
            val isDirty = withContext(Dispatchers.IO) {
                var dirty = false
                val rootFile = File(decodedPath)
                if (rootFile.exists() && rootFile.isDirectory) {
                    val lastMod = rootFile.lastModified()
                    val cached = dirSnapshots[rootFile.absolutePath]
                    if (cached == null || cached.lastModified != lastMod) {
                        val snapshot = rootFile.listFiles()
                            ?.sortedBy { it.name }
                            ?.joinToString { "${it.name}:${it.isDirectory}" } ?: ""
                        if (cached == null || cached.listing != snapshot) {
                            dirSnapshots[rootFile.absolutePath] = DirSnapshot(lastMod, snapshot)
                            dirty = true
                        }
                    }
                }
                dirty
            }
            if (isDirty) refreshTrigger++
        }
    }

    val selectedBg = MaterialTheme.colorScheme.primaryContainer
    val onSurface = MaterialTheme.colorScheme.onSurface
    val scaledStyle = remember(uiScale, selectedBg, onSurface, showFileDetails) {
        BonsaiStyle<Path>(
            toggleIcon = { null },
            toggleIconSize = 0.dp,
            nodeIconSize = if (showFileDetails) 38.dp * uiScale else 22.dp * uiScale,
            nodeSelectedBackgroundColor = selectedBg,
            nodeNameTextStyle = TextStyle(fontSize = 12.sp * uiScale, color = onSurface),
            nodeNameStartPadding = 2.dp * uiScale
        )
    }

    var activeTree by remember { mutableStateOf<Tree<Path>?>(null) }

    ModalDrawerSheet(
        drawerShape = RectangleShape,
        windowInsets = WindowInsets.systemBars,
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalDivider()
            FileTreeToolbar(
                onCollapseAll = { activeTree?.collapseAll() },
                onExpandAll = { activeTree?.expandAll() },
                onToggleSearch = {
                    searchOpen = !searchOpen
                    if (!searchOpen) searchQuery = ""
                },
                isCompactMode = isCompactMode,
                onToggleCompact = { isCompactMode = !isCompactMode },
                searchRegex = searchRegex,
                onToggleRegex = { searchRegex = !searchRegex },
                searchCaseSensitive = searchCaseSensitive,
                onToggleCaseSensitive = { searchCaseSensitive = !searchCaseSensitive },
                sortOrder = activeFileTreeConfig.sortOrder,
                onSortOrderChanged = { newOrder ->
                    activeFileTreeConfig = activeFileTreeConfig.toBuilder().setSortOrder(newOrder).build()
                    refreshTrigger++
                },
                sortBy = activeFileTreeConfig.getSortBy(),
                onSortByChanged = { newSortBy ->
                    activeFileTreeConfig = activeFileTreeConfig.toBuilder().setSortBy(newSortBy).build()
                    refreshTrigger++
                },
                showHiddenFiles = activeFileTreeConfig.showHiddenFiles,
                onToggleShowHidden = {
                    activeFileTreeConfig = activeFileTreeConfig.toBuilder().setShowHiddenFiles(!activeFileTreeConfig.showHiddenFiles).build()
                    refreshTrigger++
                },
                showIndentLines = showIndentLines,
                onToggleShowIndentLines = {
                    showIndentLines = !showIndentLines
                    activeFileTreeConfig = activeFileTreeConfig.toBuilder().setShowIndentLines(showIndentLines).build()
                    refreshTrigger++
                },
                showFileDetails = showFileDetails,
                onToggleShowFileDetails = {
                    showFileDetails = !showFileDetails
                    activeFileTreeConfig = activeFileTreeConfig.toBuilder().setShowFileDetails(showFileDetails).build()
                    refreshTrigger++
                },
                uiScale = uiScale,
                onUiScaleChanged = { newScale ->
                    uiScale = newScale
                    activeFileTreeConfig = activeFileTreeConfig.toBuilder().setFontSize((newScale * 10).toInt()).build()
                },
                viewMode = activeFileTreeConfig.viewMode,
                onViewModeChanged = { newViewMode ->
                    activeFileTreeConfig = activeFileTreeConfig.toBuilder().setViewMode(newViewMode).build()
                    refreshTrigger++
                },
                menuExpanded = menuExpanded,
                onToggleMenu = { menuExpanded = !menuExpanded },
                onDismissMenu = { menuExpanded = false },
                searchOpen = searchOpen,
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                onCloseDrawer = onCloseDrawer
            )

            HorizontalDivider()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            do {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.firstOrNull()?.let {
                                    lastTouch[0] = it.position.x.toInt()
                                    lastTouch[1] = it.position.y.toInt()
                                }
                                if (event.changes.size >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f) {
                                        uiScale = (uiScale * zoomChange).coerceIn(0.7f, 2.5f)
                                    }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
                if (searchQuery.isNotEmpty()) {
                    SearchResultsList(
                        rootPath = rootPathOkio,
                        query = searchQuery,
                        uiScale = uiScale,
                        useRegex = searchRegex,
                        caseSensitive = searchCaseSensitive,
                        onFileClick = { path -> onFileClick(path) },
                        onFolderLongClick = { path, name ->
                            contextTarget = ContextMenuTarget(path, name, true)
                        },
                        onFileLongClick = { path, name ->
                            contextTarget = ContextMenuTarget(path, name, false)
                        }
                    )
                } else {
                    androidx.compose.runtime.key(activeFileTreeConfig, refreshTrigger, uiScale, showIndentLines, showFileDetails, isCompactMode) {
                        val tree = CompactFileSystemTree(
                            rootPath = rootPathOkio,
                            fileSystem = FileSystem.SYSTEM,
                            selfInclude = true,
                            refreshTrigger = refreshTrigger,
                            uiScale = uiScale,
                            compactMode = isCompactMode,
                            sortOrder = activeFileTreeConfig.sortOrder,
                            sortBy = activeFileTreeConfig.getSortBy(),
                            showHiddenFiles = activeFileTreeConfig.showHiddenFiles,
                            showIndentLines = showIndentLines,
                            showFileDetails = showFileDetails,
                            viewMode = activeFileTreeConfig.viewMode
                        )
                        activeTree = tree

                        LaunchedEffect(tree) {
                            tree.expandRoot()
                        }

                        Bonsai(
                            tree = tree,
                            style = scaledStyle,
                            modifier = Modifier.fillMaxSize(),
                            onClick = { node ->
                                val file = File(node.content.toString())
                                if (file.isFile) {
                                    onFileClick(file.absolutePath)
                                } else {
                                    tree.toggleExpansion(node)
                                }
                            },
                            onLongClick = { node ->
                                tree.clearSelection()
                                tree.selectNode(node)
                                val file = File(node.content.toString())
                                contextTarget = ContextMenuTarget(
                                    path = node.content,
                                    name = file.name,
                                    isDirectory = file.isDirectory
                                )
                                contextMenuOffset = IntOffset(lastTouch[0], lastTouch[1])
                            }
                        )
                    }
                }
            }

            ClipboardIndicatorBar(
                clipboard = clipboard,
                onClear = { clipboard = null }
            )
        }
    }

    var deleteTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }
    var propertiesTarget by remember { mutableStateOf<ContextMenuTarget?>(null) }

    FileTreeContextMenu(
        target = contextTarget,
        clipboard = clipboard,
        offset = contextMenuOffset,
        onDismiss = { contextTarget = null },
        onNewFile = { target ->
            inlineText = ""
            inlineError = null
            inlineMode = InlineMode.NewFile
        },
        onNewDirectory = { target ->
            inlineText = ""
            inlineError = null
            inlineMode = InlineMode.NewDirectory
        },
        onCut = { target ->
            clipboard = ClipboardEntry(target.path, target.name, target.isDirectory, ClipboardMode.CUT)
        },
        onCopy = { target ->
            clipboard = ClipboardEntry(target.path, target.name, target.isDirectory, ClipboardMode.COPY)
        },
        onPaste = { target ->
            val clip = clipboard ?: return@FileTreeContextMenu
            val targetDir = if (target.isDirectory) File(target.path.toString()) else File(target.path.toString()).parentFile
            if (targetDir != null && targetDir.isDirectory) {
                val srcFile = File(clip.path.toString())
                val destFile = File(targetDir, srcFile.name)
                if (srcFile.exists()) {
                    if (clip.mode == ClipboardMode.CUT) {
                        srcFile.renameTo(destFile)
                        clipboard = null
                    } else {
                        srcFile.copyRecursively(destFile, overwrite = true)
                    }
                    refreshTrigger++
                }
            }
        },
        onRename = { target ->
            inlineText = target.name
            inlineError = null
            inlineMode = InlineMode.Rename
        },
        onDelete = { target ->
            deleteTarget = target
        },
        onCopyPath = {},
        onProperties = { target ->
            propertiesTarget = target
        }
    )

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_confirm_delete_title)) },
            text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_delete_confirm_msg, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val file = File(target.path.toString())
                        file.deleteRecursively()
                        deleteTarget = null
                        refreshTrigger++
                    }
                ) {
                    Text(ResGetter.get(com.codeforge.core.resources.R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(ResGetter.get(com.codeforge.core.resources.R.string.action_cancel))
                }
            }
        )
    }

    propertiesTarget?.let { target ->
        val file = File(target.path.toString())
        val lastMod = if (file.exists()) formatLastModified(file.lastModified()) else ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_unknown)
        val sizeStr = if (file.exists() && file.isFile) formatFileSize(file.length()) else if (file.isDirectory) ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_folder) else "0 B"
        val permissions = buildString {
            append(if (file.canRead()) "R" else "-")
            append(if (file.canWrite()) "W" else "-")
            append(if (file.canExecute()) "X" else "-")
            if (file.isHidden) append(ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_hidden))
        }

        AlertDialog(
            onDismissRequest = { propertiesTarget = null },
            title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.action_properties)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_prop_name, file.name), style = MaterialTheme.typography.bodyMedium)
                    Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_prop_path, file.absolutePath), style = MaterialTheme.typography.bodySmall)
                    Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_prop_type, if (file.isDirectory) ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_folder) else ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_file)), style = MaterialTheme.typography.bodySmall)
                    Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_prop_size, sizeStr), style = MaterialTheme.typography.bodySmall)
                    Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_prop_modified, lastMod), style = MaterialTheme.typography.bodySmall)
                    Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_prop_permissions, permissions), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { propertiesTarget = null }) {
                    Text(ResGetter.get(com.codeforge.core.resources.R.string.action_close))
                }
            }
        )
    }

    if (inlineMode != InlineMode.None && contextTarget != null) {
        val title = when (inlineMode) {
            InlineMode.NewFile -> ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_title_new_file)
            InlineMode.NewDirectory -> ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_title_new_folder)
            InlineMode.Rename -> ResGetter.get(com.codeforge.core.resources.R.string.action_rename)
            InlineMode.None -> ""
        }
        InlineInputDialog(
            title = title,
            value = inlineText,
            onValueChange = { inlineText = it },
            errorMessage = inlineError,
            onConfirm = {
                val target = contextTarget ?: return@InlineInputDialog
                val targetDir = if (target.isDirectory) File(target.path.toString()) else File(target.path.toString()).parentFile ?: File(rootPath)
                when (inlineMode) {
                    InlineMode.NewFile -> {
                        val newFile = File(targetDir, inlineText)
                        if (newFile.createNewFile()) {
                            refreshTrigger++
                            inlineMode = InlineMode.None
                        } else {
                            inlineError = ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_err_create_file)
                        }
                    }
                    InlineMode.NewDirectory -> {
                        val newDir = File(targetDir, inlineText)
                        if (newDir.mkdirs()) {
                            refreshTrigger++
                            inlineMode = InlineMode.None
                        } else {
                            inlineError = ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_err_create_dir)
                        }
                    }
                    InlineMode.Rename -> {
                        val oldFile = File(target.path.toString())
                        val newFile = File(oldFile.parentFile, inlineText)
                        if (oldFile.renameTo(newFile)) {
                            refreshTrigger++
                            inlineMode = InlineMode.None
                        } else {
                            inlineError = ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_err_rename)
                        }
                    }
                    InlineMode.None -> {}
                }
            },
            onDismiss = { inlineMode = InlineMode.None }
        )
    }
}
