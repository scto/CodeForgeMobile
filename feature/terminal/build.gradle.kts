plugins {
    id("codeforge.android.library.compose")
    id("codeforge.android.hilt")
}

android {
    namespace = "com.codeforge.feature.terminal"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:datastore"))

    implementation(libs.hilt.navigation.compose)
    implementation(project(":libs:terminal-engine"))
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.3")
}
