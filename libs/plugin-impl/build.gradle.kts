plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.plugin_impl"
}

// Laufzeit des Erweiterungssystems: Laden (DexClassLoader), Registry, Contribution-Registry, Installer.
dependencies {
    api(project(":libs:plugin-api"))
    implementation(project(":libs:plugin-sandbox"))
    implementation(project(":core:resources"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(project(":core:domain"))
    // API 5: freigegebene App-Einstellungen (IdeSettingsAccessImpl)
    implementation(project(":core:datastore"))
    // API 5: Dateibaum/Editor über Dateiänderungen informieren
    implementation(project(":core:navigation"))
    implementation(project(":libs:terminal-engine"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(project(":core:navigation"))
    testImplementation(libs.kotlinx.coroutines.test)
}
