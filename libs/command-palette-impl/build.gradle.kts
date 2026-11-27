/*
 * :libs:command-palette-impl — Implementierung von :libs:command-palette-api: ein Anbieter je Bereich
 * (Refactoring, Navigation, Code, Editor, Sprachserver, Git, Dateibaum, Build & Run, Agent/KI, IDE, Plugins),
 * Sammeln, unscharfes Filtern und Hilt-Bindung. Reines Kotlin bis auf die String-Ressourcen (JVM-Unit-Tests).
 */
plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.command_palette_impl"
}

dependencies {
    api(project(":libs:command-palette-api"))
    implementation(project(":core:resources"))
    implementation(project(":core:domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
