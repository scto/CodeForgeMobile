// Modul: :core:domain
package com.codeforge.core.domain.model

/**
 * Herkunft einer Vorlage – jede Projektvorlage stammt aus einem Plugin (`contributes.projectTemplates`), auch die
 * mitgelieferten. [templateDir] ist der absolute Vorlagenordner, [iconFile] das optionale Vorschaubild.
 * [labels]/[descriptions] enthalten Übersetzungen je Sprachcode (z. B. `en`); fehlt eine, gelten [label]/[description].
 */
data class PluginTemplateRef(
    val pluginId: String,
    val label: String,
    val description: String,
    val templateDir: String,
    val iconFile: String? = null,
    /** Android-Projekt (Mindest-SDK wählbar) oder z. B. reines JVM-Projekt. */
    val android: Boolean = true,
    /** Kleinstes erlaubtes Mindest-SDK oder `null`. */
    val minSdk: Int? = null,
    /** Mit der App ausgeliefert (vorinstalliert). */
    val builtin: Boolean = false,
    val labels: Map<String, String> = emptyMap(),
    val descriptions: Map<String, String> = emptyMap()
)

/** Anzeigename für [language] (ISO-639-1), sonst [PluginTemplateRef.label]. */
fun PluginTemplateRef.labelFor(language: String): String = labels[language] ?: label

/** Beschreibung für [language] (ISO-639-1), sonst [PluginTemplateRef.description]. */
fun PluginTemplateRef.descriptionFor(language: String): String = descriptions[language] ?: description

/** Programmiersprache eines neuen Projekts. */
enum class ProjectLanguage { KOTLIN, JAVA }

/** Beschreibt eine wählbare Vorlage (ID `<pluginId>/<vorlagenId>`) samt erlaubter Sprachen und Gradle-DSL. */
data class ProjectTemplateDescriptor(
    val id: String,
    val plugin: PluginTemplateRef,
    val supportsJava: Boolean = true,
    val supportsKotlin: Boolean = true,
    val supportsKts: Boolean = true,
    val supportsGroovy: Boolean = true
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
        /** Standardwert für die minimale SDK-Version. */
        const val DEFAULT_MIN_SDK = 21
        /** Auswählbare minimale SDK-Versionen. */
        val MIN_SDK_OPTIONS = listOf(21, 24, 26, 28, 29, 30, 33)
    }
}

/** Ergebnis der Projekterstellung: Wurzel, Name, Modulzahl und Git-Status. */
data class ProjectHandle(
    val rootPath: String,
    val projectName: String,
    val moduleCount: Int = 1,
    val gitInitialized: Boolean = false,
    /** Hinweis zur Git-Initialisierung (z. B. „Initial-Commit übersprungen: keine Identität“). */
    val gitNote: String? = null
)
