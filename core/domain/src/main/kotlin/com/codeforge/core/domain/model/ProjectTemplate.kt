// Modul: :core:domain
package com.codeforge.core.domain.model

enum class TemplateParamType { STRING, PACKAGE_NAME, BOOLEAN, MIN_SDK, DIRECTORY }

data class TemplateParam(
    val key: String,
    val label: String,
    val type: TemplateParamType,
    val defaultValue: String,
    val required: Boolean = true
)

enum class TemplateCategory { COMPOSE, JAVA, KOTLIN_CLI, MULTI_MODULE }

data class ProjectTemplateDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val category: TemplateCategory,
    val requiredParams: List<TemplateParam>
)

data class ProjectTemplate(
    val id: String,
    val name: String,
    val description: String,
    val iconResId: Int = 0,
    val category: String = "General"
)

data class TemplateParameter(
    val key: String,
    val label: String,
    val defaultValue: String,
    val description: String = ""
)

data class CreationConfig(
    val templateId: String,
    val projectName: String,
    val packageName: String,
    val targetDirectory: String,
    val parameters: Map<String, String> = emptyMap()
)

data class ProjectHandle(
    val id: String,
    val name: String,
    val rootPath: String,
    val templateId: String? = null
)
