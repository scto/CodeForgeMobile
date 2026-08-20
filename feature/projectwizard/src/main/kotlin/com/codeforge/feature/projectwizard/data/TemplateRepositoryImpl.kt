// Modul: :feature:projectwizard
package com.codeforge.feature.projectwizard.data

import com.codeforge.core.domain.model.ParamType
import com.codeforge.core.domain.model.ProjectHandle
import com.codeforge.core.domain.model.ProjectTemplate
import com.codeforge.core.domain.model.TemplateCategory
import com.codeforge.core.domain.model.TemplateParam
import com.codeforge.core.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.UUID
import javax.inject.Inject

/**
 * Stub-Implementierung der TemplateRepository.
 * Die echte Implementierung delegiert an :libs:template-engine.
 */
class TemplateRepositoryImpl @Inject constructor() : TemplateRepository {

    private val builtInTemplates = listOf(
        ProjectTemplate(
            id = "empty_compose_activity",
            name = "Empty Compose Activity",
            description = "Eine leere Activity mit Jetpack Compose und Material 3.",
            category = TemplateCategory.COMPOSE,
            requiredParams = commonParams()
        ),
        ProjectTemplate(
            id = "multi_module_compose",
            name = "Multi-Module Compose App",
            description = "Clean Architecture mit :app, :core:* und :feature:* Modulen.",
            category = TemplateCategory.MULTIMODULE,
            requiredParams = commonParams() + listOf(
                TemplateParam(
                    key = "featureModuleName",
                    label = "Erstes Feature-Modul",
                    hint = "z.B. home",
                    defaultValue = "home",
                    type = ParamType.TEXT
                )
            )
        ),
        ProjectTemplate(
            id = "java_console_app",
            name = "Java Console App",
            description = "Einfache Java-Konsolenanwendung mit Gradle.",
            category = TemplateCategory.JAVA,
            requiredParams = commonParams(useSdkLevel = false)
        ),
        ProjectTemplate(
            id = "kotlin_cli_tool",
            name = "Kotlin CLI Tool",
            description = "Kommandozeilen-Tool mit Kotlin und kotlinx-cli.",
            category = TemplateCategory.KOTLIN_CLI,
            requiredParams = commonParams(useSdkLevel = false)
        )
    )

    override fun getAvailableTemplates(): Flow<List<ProjectTemplate>> =
        flowOf(builtInTemplates)

    override suspend fun generateProject(
        templateId: String,
        params: Map<String, String>,
        targetDir: String
    ): Result<ProjectHandle> {
        // TODO: Delegiere an :libs:template-engine (Freemarker-basiert)
        return runCatching {
            ProjectHandle(
                id = UUID.randomUUID().toString(),
                name = params["projectName"] ?: "UnnamedProject",
                path = targetDir,
                templateId = templateId
            )
        }
    }

    private fun commonParams(useSdkLevel: Boolean = true): List<TemplateParam> = buildList {
        add(TemplateParam(
            key = "projectName",
            label = "Projektname",
            hint = "z.B. MyAwesomeApp",
            defaultValue = "MyApp",
            type = ParamType.TEXT
        ))
        add(TemplateParam(
            key = "packageName",
            label = "Package-Name",
            hint = "z.B. com.example.myapp",
            defaultValue = "com.example.myapp",
            type = ParamType.PACKAGE_NAME
        ))
        add(TemplateParam(
            key = "projectPath",
            label = "Projektpfad",
            hint = "/sdcard/CodeForgeProjects/",
            defaultValue = "/sdcard/CodeForgeProjects/MyApp",
            type = ParamType.DIRECTORY_PATH
        ))
        if (useSdkLevel) {
            add(TemplateParam(
                key = "minSdk",
                label = "Min SDK",
                hint = "z.B. 26",
                defaultValue = "26",
                type = ParamType.TEXT
            ))
        }
    }
}
