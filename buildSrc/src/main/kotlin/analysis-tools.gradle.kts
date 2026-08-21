// Precompiled Script Plugin – registriert NUR Custom-Tasks.
// Detekt-Plugin-Apply erfolgt separat pro Modul via Version Catalog,
// um AGP-Classpath-Konflikte in buildSrc zu vermeiden (siehe NoClassDefFoundError-Fix).

val baselineProjectPath: String =
    (findProperty("baselineProjectDir") as String?) ?: "${rootDir}/../CodeForgeMobileMain"

tasks.register<VersionCatalogDiffTask>("diffVersionCatalogs") {
    group = "verification"
    description = "Vergleicht das lokale Version Catalog mit einem Baseline-Projekt."
    baselineCatalog.set(File("$baselineProjectPath/gradle/libs.versions.toml"))
    compareCatalog.set(layout.projectDirectory.file("gradle/libs.versions.toml"))
    reportFile.set(layout.buildDirectory.file("reports/version-catalog-diff.md"))
}

tasks.register<DetektCompareTask>("detektCompareReport") {
    group = "verification"
    description = "Vergleicht Detekt-Reports zwischen Baseline- und aktuellem Projekt."
    baselineReports.setFrom(
        fileTree(baselineProjectPath) { include("**/reports/detekt/detekt.xml") }
    )
    compareReports.setFrom(
        fileTree(layout.buildDirectory.dir("reports/detekt")) { include("**/detekt.xml") }
    )
    reportFile.set(layout.buildDirectory.file("reports/detekt-compare.md"))
}