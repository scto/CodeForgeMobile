pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        gradlePluginPortal()
        maven { url = uri("https://jitpack.io")}
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        maven { url = uri("https://jitpack.io")}
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
        }
    }
}

rootProject.name = "CodeForgeMobile"
include(":app")

include(":core:common")
include(":core:data")
include(":core:datastore")
include(":core:designsystem")
include(":core:domain")
include(":core:navigation")
include(":core:resources")
include(":core:testing")
include(":core:ui")

include(":feature:composepreview")
include(":feature:editor")
include(":feature:filetree")
include(":feature:git")
include(":feature:layoutdesigner")
include(":feature:onboarding")
include(":feature:plugins")
include(":feature:projectwizard")
include(":feature:sdkmanager")
include(":feature:settings")
include(":feature:terminal")
include(":feature:themebuilder")
include(":feature:welcome")

include(":libs:gradle-tooling-bridge")
include(":libs:lsp-client")
include(":libs:plugin-api")
include(":libs:template-engine")
include(":libs:terminal-engine")
include(":examples:lsp-plugin-common")
include(":examples:kotlin-lsp-plugin")
include(":examples:java-lsp-plugin")
