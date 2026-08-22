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

rootProject.name = "CodeForgeMobile"

include(
    ":app",

    ":core:designsystem",
    ":core:ui",
    ":core:common",
    ":core:data",
    ":core:domain",
    ":core:datastore",
    ":core:navigation",
    ":core:testing",

    ":feature:onboarding",
    ":feature:welcome",
    ":feature:projectwizard",
    ":feature:editor",
    ":feature:filetree",
    ":feature:terminal",
    ":feature:layoutdesigner",
    ":feature:themebuilder",
    ":feature:git",
    ":feature:plugins",
    ":feature:settings",
    ":feature:sdkmanager",

    ":libs:terminal-engine",
    ":libs:gradle-tooling-bridge",
    ":libs:lsp-client",
    ":libs:template-engine",
    ":libs:plugin-api",
)
