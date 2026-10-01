plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.codeforge.libs.lsp_client"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":libs:terminal-engine"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // JSON-RPC-Nachrichten (LSP-Framing: Content-Length-Header + JSON-Body)
    implementation(libs.kotlinx.serialization.json)
}
