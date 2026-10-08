// Modul: :core:domain
package com.codeforge.core.domain.repository

import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.model.ProjectRequest
import com.codeforge.core.domain.model.ProjectTemplateDescriptor

/** Projekterzeugung aus den eingebauten Android-Vorlagen. Implementierung: :libs:template-engine. */
interface TemplateEngineRepository {
    suspend fun listTemplates(): List<ProjectTemplateDescriptor>

    /** Standard-Zielordner für neue Projekte (app-privat, wie beim Import). */
    fun defaultProjectsDirectory(): String

    suspend fun generate(request: ProjectRequest): Result<ProjectHandle>
}
