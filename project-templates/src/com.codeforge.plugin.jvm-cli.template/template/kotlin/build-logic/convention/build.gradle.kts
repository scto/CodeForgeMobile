plugins {
    `kotlin-dsl`
}

group = "{{packageName}}.buildlogic"

dependencies {
    // implementation: Das Projekt ist ein einzelnes Modul ohne Root-Build, die Plugins kommen mit der Convention.
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.ktlint.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinApplication") {
            id = "app.kotlin.application"
            implementationClass = "KotlinApplicationConventionPlugin"
        }
    }
}
