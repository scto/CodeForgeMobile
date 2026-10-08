plugins {
    alias(libs.plugins.codeforge.android.library.compose)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.feature.dependencyupdates"
}

dependencies {
    implementation(project(":core:resources"))
    // Nur -api-Module: die Implementierung (:libs:dependency-updater-impl) wird in :app per Hilt gebunden.
    implementation(project(":libs:dependency-updater-api"))
    implementation(project(":core:navigation"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
}
