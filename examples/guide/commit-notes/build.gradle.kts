plugins {
    alias(libs.plugins.codeforge.kotlin.library)
    alias(libs.plugins.codeforge.quality)
}

// compileOnly: SDK und Coroutines sind zur Laufzeit im Host-Prozess vorhanden (Parent-Delegation in PluginRuntime).
dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(libs.kotlinx.coroutines.core)
}

// Kompiliert mit JDK 21 (wie CI und Termux). Der Bytecode bleibt Java 17 (jvmTarget aus der build-logic),
// passend zu :libs:plugin-api; d8 entzuckert für Android.
kotlin {
    jvmToolchain(21)
}

// Ausgabe wie bisher: examples/out/guide-commit-notes/guide-commit-notes.zip (+ entpackte Kopie).
extra["pluginOutDir"] = "examples/out/${project.name}"
extra["pluginArchiveName"] = "${project.name}.zip"
extra["pluginUnpackedCopy"] = true
apply(from = rootProject.file("gradle/code-plugin.gradle.kts"))
