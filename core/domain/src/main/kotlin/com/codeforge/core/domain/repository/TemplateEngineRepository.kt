// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.model.ProjectTemplateDescriptor

/**
 * Analog zu Android Studio Wizards (Abschnitt 8 des Skills). Implementiert in
 * :libs:template-engine (Freemarker-.ftl-Dateien + JSON/YAML-Manifest).
 */
interface TemplateEngineRepository {
    suspend fun listTemplates(): List<ProjectTemplateDescriptor>

    suspend fun generate(
        descriptor: ProjectTemplateDescriptor,
        params: Map<String, String>,
        targetDir: String
    ): Result<ProjectHandle>
}
