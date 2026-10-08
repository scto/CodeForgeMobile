plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.gradle_tooling_bridge"

    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(project(":core:resources"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Gradle Tooling API: läuft ausschließlich im :gradletooling-Prozess
    // (siehe GradleBridgeService), nie im App-Hauptprozess-Classloader.
    implementation(libs.gradle.tooling.api)
}
