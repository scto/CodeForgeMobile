plugins {
    alias(libs.plugins.codeforge.android.library.compose)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.feature.editor"

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":core:resources"))
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:navigation"))
    implementation(project(":core:datastore"))
    implementation(project(":libs:code-tools"))
    implementation(project(":libs:debug-client"))

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
    implementation(libs.treesitter.core)
    implementation(libs.treesitter.kotlin)
    implementation(libs.treesitter.java)
    implementation(libs.treesitter.json)
    implementation(libs.treesitter.xml)
    implementation(libs.treesitter.cpp)
    implementation(libs.treesitter.c)

    // Für androidx.core.content.FileProvider (content://-URIs zum Teilen/Öffnen von Dateien
    // aus dem Editor heraus, z.B. "Öffnen mit..." / Teilen-Intent).
    implementation(libs.androidx.core.ktx)
    implementation("androidx.vectordrawable:vectordrawable:1.1.0")


    implementation(project(":libs:lsp-client"))
    implementation(project(":libs:ai-client"))
    // Update-Chips/Dialog im Editor (nur das -api; Implementierung wird in :app per Hilt gebunden)
    implementation(project(":libs:dependency-updater-api"))

    testImplementation(libs.junit)
    testImplementation("com.google.code.gson:gson:2.11.0")
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
