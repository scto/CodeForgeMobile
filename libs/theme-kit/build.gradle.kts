// Modul: :libs:theme-kit
// Material-3-Themes aus einer Grundfarbe: Engine, Code-Erzeugung (Compose/XML), Projekt-Übernahme, Plugin-Export.
plugins {
    alias(libs.plugins.codeforge.kotlin.library)
    alias(libs.plugins.codeforge.quality)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":libs:color-utilities"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
