plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.plugin_api"
}

dependencies {
    implementation(project(":core:resources"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:domain"))
    implementation(project(":libs:terminal-engine"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
