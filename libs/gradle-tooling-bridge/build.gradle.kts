plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.codeforge.libs.gradle_tooling_bridge"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":libs:terminal-engine"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Gradle Tooling API: läuft ausschließlich im :gradletooling-Prozess
    // (siehe GradleBridgeService), nie im App-Hauptprozess-Classloader.
    implementation(libs.gradle.tooling.api)
}
