plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.lsp_client"
}

dependencies {
    implementation(project(":core:resources"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:domain"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // JSON-RPC-Nachrichten (LSP-Framing: Content-Length-Header + JSON-Body)
    implementation(libs.kotlinx.serialization.json)
}
