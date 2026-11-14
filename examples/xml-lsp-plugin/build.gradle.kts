plugins {
    alias(libs.plugins.codeforge.kotlin.library)
    alias(libs.plugins.codeforge.quality)
}

// compileOnly: plugin-api, domain, terminal-engine und Coroutines liegen zur Laufzeit im App-Prozess
// (DexClassLoader mit Host-Classloader als Parent). Die gemeinsame Basis wird dagegen ins classes.dex gebündelt.
dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(project(":core:domain"))
    compileOnly(project(":libs:terminal-engine"))
    compileOnly(libs.kotlinx.coroutines.core)
    implementation(project(":examples:lsp-plugin-common"))
}

// Kompiliert mit JDK 21 (wie CI und Termux). Der Bytecode bleibt Java 17 (jvmTarget aus der build-logic),
// passend zu :libs:plugin-api; d8 entzuckert für Android.
kotlin {
    jvmToolchain(21)
}

apply(from = rootProject.file("examples/plugin-packaging.gradle.kts"))
