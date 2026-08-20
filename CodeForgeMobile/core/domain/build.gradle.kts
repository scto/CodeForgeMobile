plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.codeforge.core.domain"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))

    // javax.inject für Hilt-annotierte UseCases ohne Hilt-Plugin-Abhängigkeit in :core:domain
    implementation("javax.inject:javax.inject:1")
}
