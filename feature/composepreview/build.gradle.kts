plugins {
    id("codeforge.android.library.compose")
    id("codeforge.android.hilt")
}

android {
    namespace = "com.codeforge.feature.composepreview"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":libs:terminal-engine"))

    implementation("androidx.compose.material:material-icons-extended:1.7.3")

    // Für ComposeViewBitmapRenderer: Compose-Hosting außerhalb einer Activity.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6")
    implementation("androidx.savedstate:savedstate-ktx:1.2.1")

    implementation(libs.hilt.navigation.compose)

    // ACHTUNG: sehr große Dependency (~80 MB), erhöht die APK-Größe spürbar.
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.0.20")
    implementation("org.jetbrains.kotlin:kotlin-compose-compiler-plugin-embeddable:2.0.20")
}
