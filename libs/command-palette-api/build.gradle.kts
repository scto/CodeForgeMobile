/*
 * :libs:command-palette-api
 *
 * Verträge der Befehlspalette: Einträge, Kategorien, Kontext und die Schnittstellen [CommandPalette] und
 * [PaletteProvider]. Implementierung (Anbieter je Bereich, Filter, Hilt-Bindung) in :libs:command-palette-impl.
 * Konsumenten (z. B. :feature:editor) hängen nur von diesem Modul ab.
 */
plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.command_palette_api"
}

dependencies {
    // api: LspServerCapabilities und IdeCommandInfo erscheinen in PaletteContext.
    api(project(":core:domain"))
    implementation(project(":core:resources"))
    testImplementation(libs.junit)
}
