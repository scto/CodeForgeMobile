plugins {
    alias(libs.plugins.codeforge.android.library.compose)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.feature.editor"
}

dependencies {
    implementation(project(":core:resources"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:datastore"))
    implementation(project(":libs:code-tools"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation("androidx.compose.material:material-icons-extended:1.7.3")

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Sora-Editor: vollständiger Funktionsumfang (Incremental Highlight, Auto-Completion,
    // Code-Block-Indicators, Undo-Stack, Search/Replace, Word-Wrap, Diagnostics, Magnifier,
    // Bracket-Matching, Sticky Scroll) — TextMate- UND TreeSitter-Sprachunterstützung.
    implementation("io.github.Rosemoe.sora-editor:editor:0.23.4")
    implementation("io.github.Rosemoe.sora-editor:language-textmate:0.23.4")
    // TreeSitter-Sprachunterstützung — benötigt native Parser-Bibliotheken je Sprache.
    // Siehe TREESITTER.md (analog zur proot-Problematik in :libs:terminal-engine).
    implementation("io.github.Rosemoe.sora-editor:language-treesitter:0.23.4")

    // Für androidx.core.content.FileProvider (content://-URIs zum Teilen/Öffnen von Dateien
    // aus dem Editor heraus, z.B. "Öffnen mit..." / Teilen-Intent).
    implementation(libs.androidx.core.ktx)

    implementation(project(":libs:lsp-client"))
    // Update-Chips/Dialog im Editor (nur das -api; Implementierung wird in :app per Hilt gebunden)
    implementation(project(":libs:dependency-updater-api"))

    testImplementation(libs.junit)
    testImplementation(project(":core:testing"))
}
