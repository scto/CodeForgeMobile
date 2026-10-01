package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.GeneratedProjectHandle
import com.codeforge.core.domain.model.TemplateDescriptor

interface TemplateEngineRepository {
    suspend fun listTemplates(): List<TemplateDescriptor>
    suspend fun generate(
        descriptor: TemplateDescriptor,
        params: Map<String, String>,
        targetDir: String
    ): Result<GeneratedProjectHandle>
}
