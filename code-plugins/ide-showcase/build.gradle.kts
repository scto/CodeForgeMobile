plugins {
    alias(libs.plugins.codeforge.android.library.compose)
}

android {
    namespace = "com.codeforge.plugin.showcase"
}

// Plugin im App-Prozess (API 5): SDK, UI-API und Domain-Typen kommen zur Laufzeit aus der App (Coroutines über Compose).
dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(project(":libs:plugin-ui-api"))
    compileOnly(project(":core:domain"))
    testImplementation(project(":libs:plugin-api"))
    testImplementation(project(":core:domain"))
    testImplementation(libs.junit)
}

apply(from = rootProject.file("gradle/code-plugin.gradle.kts"))
