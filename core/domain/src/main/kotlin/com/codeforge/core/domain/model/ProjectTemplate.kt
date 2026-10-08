// Modul: :core:domain
package com.codeforge.core.domain.model

/** Art der Projektvorlage (Android-Projekte mit Gradle, Views oder Compose). */
enum class ProjectTemplateKind {
    NO_ACTIVITY,
    EMPTY_ACTIVITY,
    CPP_ACTIVITY,
    BASIC_ACTIVITY,
    NAV_DRAWER_ACTIVITY,
    BOTTOM_NAV_ACTIVITY,
    TABBED_ACTIVITY,
    NO_ANDROIDX_ACTIVITY,
    COMPOSE_ACTIVITY
}

enum class ProjectLanguage { KOTLIN, JAVA }

/**
 * Beschreibt eine Vorlage. Anzeigename, Beschreibung und Icon liegen als Ressourcen in
 * :feature:projectwizard (Zuordnung über [id]) — die Domain bleibt frei von Android-Ressourcen.
 */
data class ProjectTemplateDescriptor(
    val id: String,
    val kind: ProjectTemplateKind,
    val supportsJava: Boolean = true
)

/** Alle Eingaben des Wizards für die Projekterzeugung. */
data class ProjectRequest(
    val template: ProjectTemplateDescriptor,
    val projectName: String,
    val packageName: String,
    /** Übergeordneter Ordner; das Projekt entsteht in `<targetDir>/<projectName>`. */
    val targetDir: String,
    val minSdk: Int = DEFAULT_MIN_SDK,
    val language: ProjectLanguage = ProjectLanguage.KOTLIN,
    /** `true`: `*.gradle.kts`, `false`: Groovy-`*.gradle`. */
    val useKotlinDsl: Boolean = true,
    /** `git init` (Branch main) mit Initial-Commit, sofern in den Git-Einstellungen eine Identität gesetzt ist. */
    val initGit: Boolean = true
) {
    companion object {
        const val DEFAULT_MIN_SDK = 21
        val MIN_SDK_OPTIONS = listOf(21, 24, 26, 28, 29, 30, 33)
    }
}

data class ProjectHandle(
    val rootPath: String,
    val projectName: String,
    val moduleCount: Int = 1,
    val gitInitialized: Boolean = false,
    /** Hinweis zur Git-Initialisierung (z. B. „Initial-Commit übersprungen: keine Identität“). */
    val gitNote: String? = null
)
