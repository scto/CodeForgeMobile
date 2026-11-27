plugins {
    alias(libs.plugins.codeforge.android.library)
}

android {
    namespace = "com.codeforge.plugin.session"
}

// Sandbox-Plugin (API 5): nur das SDK (compileOnly), keine Android-Dienste – der Code läuft im isolierten Prozess.
dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(project(":core:domain"))
    compileOnly(libs.kotlinx.coroutines.core)
    testImplementation(project(":libs:plugin-api"))
    testImplementation(project(":core:domain"))
    testImplementation(libs.junit)
}

apply(from = rootProject.file("gradle/code-plugin.gradle.kts"))
