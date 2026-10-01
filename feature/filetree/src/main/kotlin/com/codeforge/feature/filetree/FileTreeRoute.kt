package com.codeforge.feature.filetree

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun FileTreeRoute(
    rootPath: String,
    onOpenFile: (String) -> Unit,
    onOpenGit: (String) -> Unit,
) {
    FileTreeDrawer(
        rootPath = rootPath,
        onFileClick = onOpenFile,
        modifier = Modifier.fillMaxSize(),
    )
}
