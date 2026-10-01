package com.codeforge.core.domain.model

data class TemplateParam(
    val key: String,
    val label: String,
    val defaultValue: String = "",
    val required: Boolean = true
)

data class TemplateDescriptor(
    val id: String,
    val name: String,
    val description: String,
    val requiredParams: List<TemplateParam> = emptyList(),
    val kind: String = "EMPTY_ACTIVITY"
)

data class GeneratedProjectHandle(
    val projectName: String,
    val rootPath: String
)
