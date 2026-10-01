plugins {
    id("codeforge.android.library.compose")
    id("codeforge.android.hilt")
}

android {
    namespace = "com.codeforge.feature.filetree"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:datastore"))

    implementation(libs.hilt.navigation.compose)
    implementation(libs.bundles.filetree)
    implementation(libs.okio)
    implementation("androidx.compose.material:material-icons-extended:1.7.3")
}
