plugins {
    alias(libs.plugins.codeforge.android.library.compose)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.feature.filetree"
}

dependencies {
    implementation(project(":core:resources"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.compose.material:material-icons-extended:1.7.3")

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Bonsai: Dateibaum-Darstellung (als NavigationDrawer-Inhalt in ProjectWorkspaceRoute,
    // :app, verwendet; Bonsai-Verdrahtung liegt direkt in FileTreeScreen.kt) —
    // https://github.com/adrielcafe/bonsai, Maven Central.
    implementation(libs.bonsai.core)
    implementation(libs.bonsai.file.system)

    testImplementation(project(":core:testing"))
}
