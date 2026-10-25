package com.codeforge.core.domain.model

data class BuildTypeSettings(
    val minifyEnabled: Boolean = false,
    val shrinkResources: Boolean = false,
    val crunchPngs: Boolean = false,
    val debuggable: Boolean = false,
    val proguardRulesPath: String? = null,
    val signingEnabled: Boolean = false
)

data class ProjectBuildSettings(
    val projectPath: String,
    val release: BuildTypeSettings = BuildTypeSettings(
        minifyEnabled = true,
        shrinkResources = false,
        crunchPngs = true,
        debuggable = false,
        signingEnabled = true
    ),
    val debug: BuildTypeSettings = BuildTypeSettings(
        minifyEnabled = false,
        shrinkResources = false,
        crunchPngs = false,
        debuggable = true,
        signingEnabled = false
    )
)
