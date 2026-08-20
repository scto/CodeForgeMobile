// Modul: :core:domain
package com.codeforge.core.domain.usecase

import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.repository.TemplateRepository
import javax.inject.Inject

class GenerateProjectUseCase @Inject constructor(
    private val templateRepository: TemplateRepository
) {
    suspend operator fun invoke(
        templateId: String,
        params: Map<String, String>,
        targetDir: String
    ): Result<ProjectHandle> =
        templateRepository.generateProject(templateId, params, targetDir)
}
