plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.codeforge.libs.template_engine"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.freemarker)
}
