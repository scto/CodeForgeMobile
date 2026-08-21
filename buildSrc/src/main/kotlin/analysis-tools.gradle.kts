// buildSrc/src/main/kotlin/analysis-tools.gradle.kts
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.Detekt

plugins {
    id("io.gitlab.arturbosch.detekt")
}

val baselineProjectPath: String =
    (findProperty("baselineProjectDir") as String?) ?: "${rootDir}/../CodeForgeMobileMain"

extensions.configure<DetektExtension> {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("${rootProject.rootDir}/config/detekt/detekt.yml"))
    source.setFrom(files("src/main/kotlin", "src/main/java"))
}

tasks.withType<Detekt>().configureEach {
    reports {
        html.required.set(true)
        xml.required.set(true)
        sarif.required.set(false)
        txt.required.set(false)
    }
    jvmTarget = "17"
}

tasks.register<VersionCatalogDiffTask>("diffVersionCatalogs") {
    baselineCatalog.set(File("$baselineProjectPath/gradle/libs.versions.toml"))
    compareCatalog.set(layout.projectDirectory.file("gradle/libs.versions.toml"))
    reportFile.set(layout.buildDirectory.file("reports/version-catalog-diff.md"))
}

tasks.register<DetektCompareTask>("detektCompareReport") {
    dependsOn("detekt")
    baselineReports.setFrom(
        fileTree("$baselineProjectPath") { include("**/reports/detekt/detekt.xml") }
    )
    compareReports.setFrom(
        fileTree(layout.buildDirectory.dir("reports/detekt")) { include("**/detekt.xml") }
    )
    reportFile.set(layout.buildDirectory.file("reports/detekt-compare.md"))
}
