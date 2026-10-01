package com.codeforge.core.data.repository

import android.content.Context
import com.codeforge.core.domain.model.GeneratedProjectHandle
import com.codeforge.core.domain.model.TemplateDescriptor
import com.codeforge.core.domain.model.TemplateParam
import com.codeforge.core.domain.repository.TemplateEngineRepository
import com.codeforge.libs.template_engine.CreateTemplate
import com.codeforge.libs.template_engine.ProjectTemplate
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TemplateEngineRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TemplateEngineRepository {

    override suspend fun listTemplates(): List<TemplateDescriptor> {
        return listOf(
            TemplateDescriptor(
                id = "empty_activity",
                name = "Empty Activity",
                description = "Creates a new Android project with an Empty Activity.",
                requiredParams = listOf(
                    TemplateParam("name", "Project Name", "MyApplication"),
                    TemplateParam("appId", "Package Name", "com.example.myapplication"),
                    TemplateParam("minSdk", "Minimum SDK", "24"),
                    TemplateParam("language", "Language (Kotlin/Java)", "Kotlin")
                ),
                kind = "EMPTY_ACTIVITY"
            ),
            TemplateDescriptor(
                id = "basic_activity",
                name = "Basic Activity",
                description = "Creates a new Android project with Basic Activity and Navigation.",
                requiredParams = listOf(
                    TemplateParam("name", "Project Name", "MyApplication"),
                    TemplateParam("appId", "Package Name", "com.example.myapplication"),
                    TemplateParam("minSdk", "Minimum SDK", "24"),
                    TemplateParam("language", "Language (Kotlin/Java)", "Kotlin")
                ),
                kind = "BASIC_ACTIVITY"
            ),
            TemplateDescriptor(
                id = "compose_activity",
                name = "Empty Compose Activity",
                description = "Creates a new Android project with Jetpack Compose.",
                requiredParams = listOf(
                    TemplateParam("name", "Project Name", "MyApplication"),
                    TemplateParam("appId", "Package Name", "com.example.myapplication"),
                    TemplateParam("minSdk", "Minimum SDK", "24"),
                    TemplateParam("language", "Language", "Kotlin")
                ),
                kind = "COMPOSE_ACTIVITY"
            ),
            TemplateDescriptor(
                id = "bottom_nav_activity",
                name = "Bottom Navigation Activity",
                description = "Creates a new Android project with Bottom Navigation.",
                requiredParams = listOf(
                    TemplateParam("name", "Project Name", "MyApplication"),
                    TemplateParam("appId", "Package Name", "com.example.myapplication"),
                    TemplateParam("minSdk", "Minimum SDK", "24"),
                    TemplateParam("language", "Language (Kotlin/Java)", "Kotlin")
                ),
                kind = "BOTTOM_NAV_ACTIVITY"
            ),
            TemplateDescriptor(
                id = "nav_drawer_activity",
                name = "Navigation Drawer Activity",
                description = "Creates a new Android project with Navigation Drawer.",
                requiredParams = listOf(
                    TemplateParam("name", "Project Name", "MyApplication"),
                    TemplateParam("appId", "Package Name", "com.example.myapplication"),
                    TemplateParam("minSdk", "Minimum SDK", "24"),
                    TemplateParam("language", "Language (Kotlin/Java)", "Kotlin")
                ),
                kind = "NAV_DRAWER_ACTIVITY"
            )
        )
    }

    override suspend fun generate(
        descriptor: TemplateDescriptor,
        params: Map<String, String>,
        targetDir: String
    ): Result<GeneratedProjectHandle> = runCatching {
        val projName = params["name"] ?: "MyApplication"
        val appId = params["appId"] ?: "com.example.myapplication"
        val minSdk = params["minSdk"]?.toIntOrNull() ?: 24
        val language = params["language"] ?: "Kotlin"

        val projectDir = File(targetDir, projName)
        projectDir.mkdirs()

        val kind = try {
            ProjectTemplate.Kind.valueOf(descriptor.kind)
        } catch (e: Exception) {
            ProjectTemplate.Kind.EMPTY_ACTIVITY
        }

        val projectTemplate = ProjectTemplate(
            id = descriptor.id,
            name = descriptor.name,
            description = descriptor.description,
            kind = kind
        )

        CreateTemplate(
            context = context,
            template = projectTemplate,
            projectDir = projectDir,
            appId = appId,
            minSdk = minSdk,
            language = language
        )

        GeneratedProjectHandle(
            projectName = projName,
            rootPath = projectDir.absolutePath
        )
    }
}
