plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.roborazzi) apply false
    // Dokka im Root: aggregiert die API-Doku aller Module (./gradlew dokkaGenerate -> build/dokka/html)
    alias(libs.plugins.dokka)
}

// Dokka auf jedes Kotlin-Modul anwenden und in die Aggregation aufnehmen; Mermaid-Diagramme in KDoc ```mermaid.
subprojects {
    plugins.withId("org.jetbrains.kotlin.android") { applyDokka() }
    plugins.withId("org.jetbrains.kotlin.jvm") { applyDokka() }
}

fun Project.applyDokka() {
    if (pluginManager.hasPlugin("org.jetbrains.dokka")) return
    pluginManager.apply("org.jetbrains.dokka")
    dependencies.add("dokkaPlugin", rootProject.libs.dokka.mermaid.plugin)
    rootProject.dependencies.add("dokka", this)
}

dependencies {
    dokkaPlugin(libs.dokka.mermaid.plugin)
}

dokka {
    moduleName.set("CodeForgeMobile")
    pluginsConfiguration.html {
        footerMessage.set("CodeForgeMobile – API-Dokumentation")
        // Aussehen wie der CodeForge Store: Farben/Formen per Stylesheet, Logo ersetzt das Dokka-Symbol
        customStyleSheets.from(rootProject.file("docs/dokka/store-dokka.css"))
        customAssets.from(rootProject.file("docs/dokka/logo-icon.svg"))
    }
}

// `clean` stellt das Base-Plugin (vom Dokka-Root-Plugin) bereit und löscht das Build-Verzeichnis.
