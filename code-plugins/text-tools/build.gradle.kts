plugins {
    alias(libs.plugins.codeforge.android.library)
}

android {
    namespace = "com.codeforge.plugin.text"
}

// Sandbox-Plugin: nur das SDK (compileOnly), keine Android-Dienste – der Code läuft im isolierten Prozess.
dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(project(":core:domain"))
    compileOnly(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}

apply(from = "../code-plugin.gradle.kts")
