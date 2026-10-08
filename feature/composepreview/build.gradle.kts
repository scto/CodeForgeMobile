plugins {
    alias(libs.plugins.codeforge.android.library.compose)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.feature.composepreview"
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

    // Für ComposeViewBitmapRenderer: Compose-Hosting außerhalb einer Activity.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // ACHTUNG: sehr große Dependency (~80 MB), erhöht die APK-Größe spürbar.
    // Siehe KDoc in ComposePreviewRendererImpl zu den ungelösten Classpath-Fragen.
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.21")
    implementation("org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:2.1.21")
}
