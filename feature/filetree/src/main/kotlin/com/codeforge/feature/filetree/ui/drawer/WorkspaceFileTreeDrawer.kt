package com.codeforge.feature.filetree.ui.drawer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.codeforge.core.datastore.proto.FileTreeConfig
import com.codeforge.feature.filetree.FileTreeDrawer

@Composable
fun WorkspaceFileTreeDrawer(
    workspaceRoot: String?,
    onFileSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    fileTreeConfig: FileTreeConfig = FileTreeConfig.getDefaultInstance(),
    onCloseDrawer: (() -> Unit)? = null
) {
    val root = if (workspaceRoot.isNullOrBlank()) "/storage/emulated/0" else workspaceRoot
    FileTreeDrawer(
        rootPath = root,
        onFileClick = onFileSelected,
        modifier = modifier,
        fileTreeConfig = fileTreeConfig,
        onCloseDrawer = onCloseDrawer
    )
}
