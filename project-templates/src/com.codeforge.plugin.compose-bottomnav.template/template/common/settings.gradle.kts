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

rootProject.name = "{{projectName}}"

include(":app")
include(":core:domain")
include(":core:data")
include(":feature:today")
include(":feature:tasks")
include(":feature:profile")
