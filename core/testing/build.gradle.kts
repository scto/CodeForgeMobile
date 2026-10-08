plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.core.testing"
}

dependencies {
    api(project(":core:resources"))
    implementation(libs.kotlinx.coroutines.core)
}
