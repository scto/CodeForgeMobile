plugins {
    alias(libs.plugins.codeforge.android.library.compose)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.plugin_ui_api"
}

// SDK-Modul für Plugin-Oberflächen (Compose). Getrennt von :libs:plugin-api, damit Plugins ohne UI kein Compose
// brauchen. Plugins binden es `compileOnly` ein; zur Laufzeit kommen Compose und dieses Modul aus der App
// (DexClassLoader mit App-Classloader als Parent) – Plugins müssen gegen dieselbe Compose-Version kompilieren.
dependencies {
    api(project(":core:domain"))
}
