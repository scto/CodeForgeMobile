plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
