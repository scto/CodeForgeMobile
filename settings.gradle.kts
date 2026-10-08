pluginManagement {
    // Convention-Plugins (codeforge.*) — siehe build-logic/README.md
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
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    }
}

rootProject.name = "CodeForgeMobile"

include(
    ":app",

    ":core:resources",
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
    ":feature:composepreview",
    ":feature:filetree",
    ":feature:terminal",
    ":feature:sdkmanager",
    ":feature:layoutdesigner",
    ":feature:themebuilder",
    ":feature:git",
    ":feature:plugins",
    ":feature:settings",
    ":feature:dependencyupdates",
    ":feature:search",
    ":feature:modulemaker",

    ":libs:terminal-engine",
    ":libs:gradle-tooling-bridge",
    ":libs:lsp-client",
    ":libs:template-engine",
    ":libs:plugin-api",

    // Projekt-Indexierung + Dependency-Updater (siehe docs/indexing-and-dependency-updater.md)
    ":libs:indexing-api",
    ":libs:indexing-impl",
    ":libs:dependency-updater-api",
    ":libs:dependency-updater-impl",
    ":libs:code-tools",

    // Termux-Vendoring (siehe docs/sub/TERMUX-PORTING.md) — :libs:terminal-engine nutzt
    // diese jetzt statt PRoot für die interaktive :feature:terminal-Session
    ":libs:termux-emulator",
    ":libs:termux-view",
    ":libs:termux-shared",
    ":libs:termux-app",

    // Eigenständige Beispiel-Plugins — bewusst NICHT von :app abhängig (siehe jeweilige
    // README.md), werden als ZIP über :feature:plugins sideloaded, nicht in die App
    // einkompiliert.
    ":examples:lsp-plugin-common",
    ":examples:kotlin-lsp-plugin",
    ":examples:java-lsp-plugin",
)
