plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "{{packageName}}.core.data"
    compileSdk = 35
    defaultConfig { minSdk = {{minSdk}} }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
