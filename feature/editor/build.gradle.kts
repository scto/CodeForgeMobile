plugins {
    id("codeforge.android.library.compose")
    id("codeforge.android.hilt")
}

android {
    namespace = "com.codeforge.feature.editor"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:datastore"))

    implementation("androidx.compose.material:material-icons-extended:1.7.3")
    implementation(libs.hilt.navigation.compose)

    // Sora-Editor: Editor, Monarch, TextMate, TreeSitter, Native
    implementation(libs.bundles.rosemoe)
    implementation(libs.bundles.monarch)
    implementation(libs.bundles.treesitter)

    implementation(project(":feature:filetree"))
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.vectordrawable:vectordrawable:1.2.0")
    implementation("androidx.vectordrawable:vectordrawable-animated:1.2.0")
    implementation(project(":libs:lsp-client"))
}
