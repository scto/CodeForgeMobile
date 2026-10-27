plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.plugin_api"
}

// SDK-Modul: nur die Schnittstellen für Code-Plugins (CodeForgePlugin, PluginContext, PluginHostServices).
// Manifest-/Contribution-Modelle liegen in :core:domain (com.codeforge.core.domain.extension),
// die Laufzeit in :libs:plugin-impl.
dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:domain"))
    implementation(project(":libs:terminal-engine"))
}
