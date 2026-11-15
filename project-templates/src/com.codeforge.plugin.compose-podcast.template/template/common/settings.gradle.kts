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
include(":feature:discover")
include(":feature:library")
include(":feature:player")
include(":feature:settings")
