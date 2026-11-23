plugins {
    alias(libs.plugins.codeforge.kotlin.library)
    alias(libs.plugins.codeforge.quality)
}

// compileOnly: SDK und Coroutines sind zur Laufzeit im Host-Prozess vorhanden (Parent-Delegation in PluginRuntime).
dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(libs.kotlinx.coroutines.core)
}

kotlin {
    // Kompiliert mit JDK 21 (wie CI und Termux). Der Bytecode bleibt Java 17 (jvmTarget aus der build-logic),
    // passend zu :libs:plugin-api und zur App, in die das Plugin nach d8 geladen wird.
    jvmToolchain(21)
}

// Packen wie alle Code-Plugins (d8, build-info.json, optionale Signatur): gradle/code-plugin.gradle.kts.
extra["pluginOutDir"] = "templates/code-plugin/build/plugin-package"
extra["pluginArchiveName"] = "template-code-plugin.zip"
apply(from = rootProject.file("gradle/code-plugin.gradle.kts"))
