// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.repository.TemplateEngineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateEngineRepositoryImpl @Inject constructor(
    private val renderer: FreemarkerTemplateRenderer
) : TemplateEngineRepository {

    override suspend fun listTemplates(): List<ProjectTemplateDescriptor> = BuiltInTemplates.all

    override suspend fun generate(
        descriptor: ProjectTemplateDescriptor,
        params: Map<String, String>,
        targetDir: String
    ): Result<ProjectHandle> = withContext(Dispatchers.IO) {
        runCatching {
            val projectName = params["projectName"]?.takeIf { it.isNotBlank() } ?: descriptor.name
            val root = File(targetDir, projectName)

            if (root.exists()) {
                error("Zielverzeichnis '${root.path}' existiert bereits.")
            }
            if (!root.mkdirs()) {
                error("Konnte Projektverzeichnis nicht anlegen: ${root.path}")
            }

            val enrichedParams = params + ("projectName" to projectName)
            renderer.render(templateId = descriptor.id, params = enrichedParams, targetRoot = root)

            ProjectHandle(
                id = root.path,
                name = projectName,
                rootPath = root.path,
                templateId = descriptor.id
            )
        }
    }
}
