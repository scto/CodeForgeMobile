plugins {
    alias(libs.plugins.codeforge.android.library.compose)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.core.ui"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
