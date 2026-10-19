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

    implementation(project(":libs:terminal-engine"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Gradle Tooling API: Veraltet / nicht verwendet (siehe GradleBridgeService).
    // Durch ProcessGradleBuildRepository ersetzt.
    implementation(libs.gradle.tooling.api)

    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
}
