// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.model.ProjectTemplate
import kotlinx.coroutines.flow.Flow

interface TemplateRepository {
    fun getAvailableTemplates(): Flow<List<ProjectTemplate>>
    suspend fun generateProject(
        templateId: String,
        params: Map<String, String>,
        targetDir: String
    ): Result<ProjectHandle>
}
