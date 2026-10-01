package com.codeforge.feature.filetree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.bonsai.core.node.Branch
import cafe.adriel.bonsai.core.node.BranchNode
import cafe.adriel.bonsai.core.node.Leaf
import cafe.adriel.bonsai.core.tree.Tree
import cafe.adriel.bonsai.core.tree.TreeScope
import com.codeforge.core.datastore.proto.FileTreeSortByProto
import com.codeforge.core.datastore.proto.FileTreeSortOrderProto
import com.codeforge.core.datastore.proto.FileTreeViewModeProto
import okio.FileSystem
import okio.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Custom Painter that draws continuous, theme-adaptive vertical hierarchy guide lines
 * for parent tree levels directly during node painting, bypassing Compose layout clipping.
 */
class TreeGuidelinePainter(
    private val iconPainter: Painter?,
    private val level: Int,
    private val indentPx: Float,
    private val lineColor: Color,
    private val showIndentLines: Boolean
) : Painter() {
    override val intrinsicSize: Size
        get() = iconPainter?.intrinsicSize ?: Size(24f, 24f)

    override fun DrawScope.onDraw() {
        if (showIndentLines && level > 0) {
            for (i in 0 until level) {
                val lineX = -((level - i) * indentPx) + (indentPx / 2f)
                drawLine(
                    color = lineColor,
                    start = Offset(lineX, -size.height * 3f),
                    end = Offset(lineX, size.height * 4f),
                    strokeWidth = 3f
                )
            }
        }
        iconPainter?.run {
            draw(size)
        }
    }
}

@Composable
fun CompactFileSystemTree(
    rootPath: Path,
    fileSystem: FileSystem,
    selfInclude: Boolean = false,
    refreshTrigger: Int = 0,
    uiScale: Float = 1f,
    compactMode: Boolean = true,
    sortOrder: FileTreeSortOrderProto = FileTreeSortOrderProto.SORT_ORDER_ASCENDING,
    sortBy: FileTreeSortByProto = FileTreeSortByProto.SORT_BY_NAME,
    showHiddenFiles: Boolean = false,
    showIndentLines: Boolean = true,
    showFileDetails: Boolean = true,
    viewMode: FileTreeViewModeProto = FileTreeViewModeProto.VIEW_MODE_MODULE
): Tree<Path> = Tree {
    CompactFileSystemTreeContent(
        rootPath = rootPath,
        fileSystem = fileSystem,
        selfInclude = selfInclude,
        refreshTrigger = refreshTrigger,
        uiScale = uiScale,
        compactMode = compactMode,
        sortOrder = sortOrder,
        sortBy = sortBy,
        showHiddenFiles = showHiddenFiles,
        showIndentLines = showIndentLines,
        showFileDetails = showFileDetails,
        viewMode = viewMode
    )
}

@Composable
private fun TreeScope.CompactFileSystemTreeContent(
    rootPath: Path,
    fileSystem: FileSystem,
    selfInclude: Boolean,
    refreshTrigger: Int,
    uiScale: Float,
    compactMode: Boolean,
    sortOrder: FileTreeSortOrderProto,
    sortBy: FileTreeSortByProto,
    showHiddenFiles: Boolean,
    showIndentLines: Boolean,
    showFileDetails: Boolean,
    viewMode: FileTreeViewModeProto
) {
    if (selfInclude) {
        androidx.compose.runtime.key(rootPath.toString()) {
            CompactFileSystemNode(
                path = rootPath,
                fileSystem = fileSystem,
                refreshTrigger = refreshTrigger,
                uiScale = uiScale,
                compactMode = compactMode,
                sortOrder = sortOrder,
                sortBy = sortBy,
                showHiddenFiles = showHiddenFiles,
                showIndentLines = showIndentLines,
                showFileDetails = showFileDetails,
                viewMode = viewMode,
                level = 0
            )
        }
    } else {
        val children = fileSystem.listOrNull(rootPath) ?: emptyList()
        filterAndSortPaths(children, fileSystem, showHiddenFiles, viewMode, sortBy, sortOrder).forEach { path ->
            androidx.compose.runtime.key(path.toString()) {
                CompactFileSystemNode(
                    path = path,
                    fileSystem = fileSystem,
                    refreshTrigger = refreshTrigger,
                    uiScale = uiScale,
                    compactMode = compactMode,
                    sortOrder = sortOrder,
                    sortBy = sortBy,
                    showHiddenFiles = showHiddenFiles,
                    showIndentLines = showIndentLines,
                    showFileDetails = showFileDetails,
                    viewMode = viewMode,
                    level = 0
                )
            }
        }
    }
}

@Composable
private fun TreeScope.CompactFileSystemNode(
    path: Path,
    fileSystem: FileSystem,
    refreshTrigger: Int,
    uiScale: Float,
    compactMode: Boolean,
    sortOrder: FileTreeSortOrderProto,
    sortBy: FileTreeSortByProto,
    showHiddenFiles: Boolean,
    showIndentLines: Boolean,
    showFileDetails: Boolean,
    viewMode: FileTreeViewModeProto,
    level: Int
) {
    val metadata = fileSystem.metadataOrNull(path) ?: return

    val primaryTint = MaterialTheme.colorScheme.primary
    val outlineTint = MaterialTheme.colorScheme.outline
    val lineColor = if (outlineTint != Color.Unspecified) {
        outlineTint.copy(alpha = 0.35f)
    } else {
        primaryTint.copy(alpha = 0.35f)
    }

    val customNodeIcon: @Composable (node: Any?) -> Unit = { node ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight()
        ) {
            // 1. Indentation columns per tree level (spacious 20.dp per level)
            if (level > 0) {
                val indentWidth = 20.dp * uiScale
                for (i in 0 until level) {
                    Box(
                        modifier = Modifier
                            .width(indentWidth)
                            .fillMaxHeight()
                            .drawBehind {
                                if (showIndentLines) {
                                    val lineX = size.width / 2f
                                    drawLine(
                                        color = lineColor,
                                        start = Offset(lineX, 0f),
                                        end = Offset(lineX, size.height),
                                        strokeWidth = 1.dp.toPx()
                                    )
                                }
                            }
                    )
                }
            }

            // 2. Folder toggle arrow icon or File spacer alignment
            if (metadata.isDirectory) {
                val isExpanded = (node as? BranchNode<*>)?.isExpanded ?: false
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp * uiScale)
                )
            } else {
                Spacer(modifier = Modifier.width(16.dp * uiScale))
            }

            Spacer(modifier = Modifier.width(2.dp * uiScale))

            // 3. File or Folder Icon
            if (metadata.isDirectory) {
                val isExpanded = (node as? BranchNode<*>)?.isExpanded ?: false
                Icon(
                    imageVector = iconForFolder(path.name, isExpanded),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp * uiScale)
                )
            } else {
                val ext = path.name.substringAfterLast('.', "").lowercase()
                val isComposableFile = remember(path.toString()) {
                    if (ext == "kt" || ext == "kts") {
                        runCatching {
                            fileSystem.read(path) {
                                val sampleSize = minOf(metadata.size ?: 4096L, 8192L)
                                readUtf8(sampleSize).contains("@Composable")
                            }
                        }.getOrDefault(false)
                    } else false
                }
                val icon = if (isComposableFile) iconForComposable() else iconForExtension(ext)
                val tint = if (isComposableFile) Color(0xFF6366F1) else MaterialTheme.colorScheme.primary
                Icon(
                    imageVector = icon,
                    contentDescription = path.name,
                    tint = tint,
                    modifier = Modifier.size(16.dp * uiScale)
                )
            }
        }
    }

    val nameComposable: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(vertical = 2.dp * uiScale),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = path.name,
                fontSize = 12.sp * uiScale,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showFileDetails) {
                val javaFile = remember(path.toString()) { java.io.File(path.toString()) }
                val sizeText = if (!metadata.isDirectory) {
                    val size = metadata.size ?: javaFile.length()
                    formatFileSize(size)
                } else {
                    "Ordner"
                }
                val lastMod = metadata.lastModifiedAtMillis ?: javaFile.lastModified()
                val dateText = formatLastModified(lastMod)
                val details = listOfNotNull(
                    sizeText.takeIf { it.isNotBlank() },
                    dateText.takeIf { it.isNotBlank() }
                ).joinToString(" • ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        fontSize = 9.5.sp * uiScale,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (!metadata.isDirectory) {
        Leaf(
            content = path,
            name = path.name,
            customName = { _ -> nameComposable() },
            customIcon = { node -> customNodeIcon(node) }
        )
        return
    }

    Branch(
        content = path,
        name = path.name,
        customName = { _ -> nameComposable() },
        customIcon = { node -> customNodeIcon(node) }
    ) {
        val children = fileSystem.listOrNull(path) ?: emptyList()
        filterAndSortPaths(children, fileSystem, showHiddenFiles, viewMode, sortBy, sortOrder).forEach { childPath ->
            androidx.compose.runtime.key(childPath.toString()) {
                CompactFileSystemNode(
                    path = childPath,
                    fileSystem = fileSystem,
                    refreshTrigger = refreshTrigger,
                    uiScale = uiScale,
                    compactMode = compactMode,
                    sortOrder = sortOrder,
                    sortBy = sortBy,
                    showHiddenFiles = showHiddenFiles,
                    showIndentLines = showIndentLines,
                    showFileDetails = showFileDetails,
                    viewMode = viewMode,
                    level = level + 1
                )
            }
        }
    }
}

fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
        else -> String.format(Locale.US, "%.1f GB", bytes.toDouble() / (1024 * 1024 * 1024))
    }
}

fun formatLastModified(millis: Long): String {
    if (millis <= 0) return ""
    val date = Date(millis)
    val sdf = SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault())
    return sdf.format(date)
}

fun filterAndSortPaths(
    paths: List<Path>,
    fileSystem: FileSystem,
    showHiddenFiles: Boolean,
    viewMode: FileTreeViewModeProto,
    sortBy: FileTreeSortByProto,
    sortOrder: FileTreeSortOrderProto
): List<Path> {
    return paths.filter { path ->
        val name = path.name
        val meta = fileSystem.metadataOrNull(path)
        val isDir = meta?.isDirectory ?: false

        when (viewMode) {
            FileTreeViewModeProto.VIEW_MODE_MODULE -> {
                if (isDir && (name == "build" || name == ".gradle" || name == ".idea" || name == "out" || name == ".git" || name == ".cxx" || name == ".externalNativeBuild")) {
                    return@filter false
                }
                if (!showHiddenFiles && name.startsWith(".")) {
                    return@filter false
                }
                true
            }
            FileTreeViewModeProto.VIEW_MODE_PROJECT -> {
                if (!showHiddenFiles) {
                    if (name.startsWith(".")) return@filter false
                    if (isDir && (name == "build" || name == ".gradle" || name == ".idea" || name == "out")) return@filter false
                }
                true
            }
            FileTreeViewModeProto.VIEW_MODE_FILE -> {
                true
            }
            else -> true
        }
    }.sortedWith { a, b ->
        val metaA = fileSystem.metadataOrNull(a)
        val metaB = fileSystem.metadataOrNull(b)
        val isDirA = metaA?.isDirectory ?: false
        val isDirB = metaB?.isDirectory ?: false

        if (isDirA != isDirB) {
            return@sortedWith if (isDirA) -1 else 1
        }

        val comp = when (sortBy) {
            FileTreeSortByProto.SORT_BY_NAME -> a.name.lowercase().compareTo(b.name.lowercase())
            FileTreeSortByProto.SORT_BY_TYPE -> {
                val extA = a.name.substringAfterLast('.', "").lowercase()
                val extB = b.name.substringAfterLast('.', "").lowercase()
                extA.compareTo(extB).takeIf { it != 0 } ?: a.name.lowercase().compareTo(b.name.lowercase())
            }
            FileTreeSortByProto.SORT_BY_SIZE -> {
                val sizeA = metaA?.size ?: 0L
                val sizeB = metaB?.size ?: 0L
                sizeA.compareTo(sizeB)
            }
            FileTreeSortByProto.SORT_BY_DATE -> {
                val dateA = metaA?.lastModifiedAtMillis ?: 0L
                val dateB = metaB?.lastModifiedAtMillis ?: 0L
                dateA.compareTo(dateB)
            }
            else -> a.name.lowercase().compareTo(b.name.lowercase())
        }

        if (sortOrder == FileTreeSortOrderProto.SORT_ORDER_DESCENDING) -comp else comp
    }
}
