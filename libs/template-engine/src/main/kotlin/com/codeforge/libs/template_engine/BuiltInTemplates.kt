// Modul: :libs:template-engine
package com.codeforge.libs.template_engine

import com.codeforge.core.domain.model.ProjectTemplateDescriptor
import com.codeforge.core.domain.model.TemplateCategory
import com.codeforge.core.domain.model.TemplateParam
import com.codeforge.core.domain.model.TemplateParamType

/**
 * Vier Basis-Templates gemäß Skill Abschnitt 8. Jedes Template referenziert ein
 * .ftl-Manifest unter assets/templates/<id>/ (Freemarker), das hier noch nicht
 * mitgeliefert wird — generate() erzeugt aktuell eine minimale, aber valide
 * Projektstruktur direkt im Code als Platzhalter für den echten Freemarker-Renderer.
 */
object BuiltInTemplates {

    val composeActivity = ProjectTemplateDescriptor(
        id = "compose_empty_activity",
        name = "Empty Compose Activity",
        description = "Minimales Compose-Projekt mit einer einzelnen Activity und MaterialTheme.",
        category = TemplateCategory.COMPOSE,
        requiredParams = listOf(
            TemplateParam("projectName", "Projektname", TemplateParamType.STRING, "MeineApp"),
            TemplateParam("packageName", "Package-Name", TemplateParamType.PACKAGE_NAME, "com.example.app"),
            TemplateParam("minSdk", "Minimale SDK-Version", TemplateParamType.MIN_SDK, "26")
        )
    )

    val javaClass = ProjectTemplateDescriptor(
        id = "java_console_app",
        name = "Java Console App",
        description = "Einfaches Java-Projekt mit Main-Klasse, ohne Android-Abhängigkeiten.",
        category = TemplateCategory.JAVA,
        requiredParams = listOf(
            TemplateParam("projectName", "Projektname", TemplateParamType.STRING, "MeinJavaProjekt"),
            TemplateParam("packageName", "Package-Name", TemplateParamType.PACKAGE_NAME, "com.example.cli")
        )
    )

    val kotlinCli = ProjectTemplateDescriptor(
        id = "kotlin_cli_app",
        name = "Kotlin CLI App",
        description = "Kotlin-JVM-Projekt mit main()-Funktion, ideal für Skripte und Tools.",
        category = TemplateCategory.KOTLIN_CLI,
        requiredParams = listOf(
            TemplateParam("projectName", "Projektname", TemplateParamType.STRING, "MeinKotlinTool"),
            TemplateParam("packageName", "Package-Name", TemplateParamType.PACKAGE_NAME, "com.example.tool")
        )
    )

    val multiModule = ProjectTemplateDescriptor(
        id = "multi_module_android",
        name = "Multi-Module Android Projekt",
        description = "Android-App mit :app und :core:common Modulen, vorbereitet für Skalierung.",
        category = TemplateCategory.MULTI_MODULE,
        requiredParams = listOf(
            TemplateParam("projectName", "Projektname", TemplateParamType.STRING, "MeinModularesProjekt"),
            TemplateParam("packageName", "Package-Name", TemplateParamType.PACKAGE_NAME, "com.example.modular"),
            TemplateParam("minSdk", "Minimale SDK-Version", TemplateParamType.MIN_SDK, "26"),
            TemplateParam("includeTests", "Test-Modul einbeziehen", TemplateParamType.BOOLEAN, "true", required = false)
        )
    )

    val all = listOf(composeActivity, javaClass, kotlinCli, multiModule)
}
