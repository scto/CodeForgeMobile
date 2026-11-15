pluginManagement {
    includeBuild("build-logic")
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

rootProject.name = "{{projectName}}"

include(":app")
include(":core:domain")
include(":core:data")
include(":core:resources")
include(":core:designsystem")
include(":feature:notes")
include(":feature:editor")
include(":feature:settings")
