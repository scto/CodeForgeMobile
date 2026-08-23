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

data class ProjectHandle(
    val rootPath: String,
    val projectName: String
)
