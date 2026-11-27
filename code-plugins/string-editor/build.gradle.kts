plugins {
    alias(libs.plugins.codeforge.android.library.compose)
}

android {
    namespace = "com.codeforge.plugin.strings"
}

// Plugin im App-Prozess: SDK, UI-API, Domain-Typen, Compose und Material-Symbole kommen zur Laufzeit aus der App.
dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(project(":libs:plugin-ui-api"))
    compileOnly(project(":core:domain"))
    compileOnly(libs.androidx.compose.material.icons.extended)
    testImplementation(project(":libs:plugin-api"))
    testImplementation(libs.junit)
}

apply(from = rootProject.file("gradle/code-plugin.gradle.kts"))
