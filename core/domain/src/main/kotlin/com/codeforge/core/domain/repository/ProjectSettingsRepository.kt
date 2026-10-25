package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ProjectBuildSettings
import kotlinx.coroutines.flow.Flow

interface ProjectSettingsRepository {
    fun getSettings(projectPath: String): Flow<ProjectBuildSettings>
    suspend fun saveSettings(settings: ProjectBuildSettings)
    suspend fun generateInitScript(settings: ProjectBuildSettings): String
}
