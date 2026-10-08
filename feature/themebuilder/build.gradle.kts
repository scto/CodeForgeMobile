plugins {
    alias(libs.plugins.codeforge.android.library.compose)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.feature.themebuilder"
}

dependencies {
    implementation(project(":core:resources"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:datastore"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
}
