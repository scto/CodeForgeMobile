package com.codeforge.core.domain.model

/** Einstellungen eines Build-Typs (Minify, Ressourcen-Shrinking, PNG-Crunch, debuggable, ProGuard-Regeln, Signierung). */
data class BuildTypeSettings(
    val minifyEnabled: Boolean = false,
    val shrinkResources: Boolean = false,
    val crunchPngs: Boolean = false,
    val debuggable: Boolean = false,
    val proguardRulesPath: String? = null,
    val signingEnabled: Boolean = false
)

/** Build-Einstellungen eines Projekts für Release und Debug. */
data class ProjectBuildSettings(
    val projectPath: String,
    /** Einstellungen des Release-Builds. */
    val release: BuildTypeSettings = BuildTypeSettings(
        minifyEnabled = true,
        shrinkResources = false,
        crunchPngs = true,
        debuggable = false,
        signingEnabled = true
    ),
    /** Einstellungen des Debug-Builds. */
    val debug: BuildTypeSettings = BuildTypeSettings(
        minifyEnabled = false,
        shrinkResources = false,
        crunchPngs = false,
        debuggable = true,
        signingEnabled = false
    )
)
