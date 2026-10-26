package com.codeforge.core.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CfmCursorPosition(
    val line: Int,
    val column: Int
)

@Serializable
data class CfmOpenFile(
    val path: String,
    val active: Boolean = false,
    val cursor: CfmCursorPosition? = null
)

@Serializable
data class CfmFileInfo(
    val path: String,
    val size: Long,
    val modified: Long
)

@Serializable
data class CfmWorkspace(
    val version: Int = 1,
    val project: String,
    val updatedAt: String,
    val files: List<CfmFileInfo> = emptyList(),
    val openFiles: List<CfmOpenFile> = emptyList(),
    val modifiedFiles: List<String> = emptyList()
)
