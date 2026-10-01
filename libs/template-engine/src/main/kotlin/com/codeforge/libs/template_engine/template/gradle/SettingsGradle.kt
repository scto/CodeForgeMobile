package com.codeforge.libs.template_engine.template.gradle

fun SettingsGradle(projectName: String): String = """
    pluginManagement {
        repositories {
            google()
            mavenCentral()
            gradlePluginPortal()
        }
    }

    dependencyResolutionManagement {
        repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
        repositories {
            google()
            mavenCentral()
        }
    }

    rootProject.name = "$projectName"

    include(":app")
""".trimIndent()
