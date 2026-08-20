// Modul: :core:domain
package com.codeforge.core.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class ProjectTemplate(
    val id: String,
    val name: String,
    val description: String,
    val category: TemplateCategory,
    val requiredParams: List<TemplateParam>
)

enum class TemplateCategory {
    COMPOSE, MULTIMODULE, JAVA, KOTLIN_CLI
}

@Immutable
data class TemplateParam(
    val key: String,
    val label: String,
    val hint: String,
    val defaultValue: String,
    val type: ParamType
)

enum class ParamType {
    TEXT, PACKAGE_NAME, DIRECTORY_PATH, BOOLEAN
}

@Immutable
data class ProjectHandle(
    val id: String,
    val name: String,
    val path: String,
    val templateId: String
)
