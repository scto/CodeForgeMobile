plugins {
    alias(libs.plugins.codeforge.android.library.compose)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.core.resources"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.startup)
}
