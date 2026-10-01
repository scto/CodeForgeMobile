package com.codeforge.feature.filetree.model

data class FileTreeNode(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val children: List<FileTreeNode> = emptyList()
)
