package com.codeforge.app.workspace

import com.codeforge.core.datastore.proto.EditorConfig
import com.codeforge.core.datastore.proto.FileTreeConfig

data class WorkspaceUiState(
    val workspaceRoot: String? = null,
    val currentFilePath: String? = null,
    val currentContent: String = "",
    val isDrawerOpen: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val editorConfig: EditorConfig = EditorConfig.getDefaultInstance(),
    val fileTreeConfig: FileTreeConfig = FileTreeConfig.getDefaultInstance(),
)
