package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.CfmFileInfo
import com.codeforge.core.domain.model.CfmOpenFile
import com.codeforge.core.domain.model.CfmWorkspace
import java.io.File

interface WorkspaceRepository {
    companion object {
        const val CFM_DIR_NAME = ".cfm"
        const val WORKSPACE_FILE_NAME = "cfm.json"
        const val SCHEMA_VERSION = 1

        val DEFAULT_EXCLUDED_DIRS = setOf(
            ".cfm",
            ".git",
            "build",
            ".gradle",
            ".idea",
            "node_modules",
            "bin",
            "out",
            ".cxx",
            ".externalNativeBuild"
        )
    }

    suspend fun loadWorkspace(projectDir: File): CfmWorkspace?

    suspend fun saveWorkspace(projectDir: File, workspace: CfmWorkspace): Result<Unit>

    suspend fun scanProjectFiles(projectDir: File, maxFiles: Int = 10000): List<CfmFileInfo>

    suspend fun updateModifiedFiles(projectDir: File, modifiedRelativePaths: List<String>): Result<Unit>

    suspend fun updateOpenFiles(projectDir: File, openFiles: List<CfmOpenFile>): Result<Unit>

    suspend fun ensureGitExclude(projectDir: File): Result<Unit>
}
