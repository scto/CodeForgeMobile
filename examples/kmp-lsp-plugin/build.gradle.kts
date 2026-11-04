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

// Kein Toolchain-Zwang (läuft auch mit JDK 21): Bytecode-Ziel 17, d8 entzuckert für Android.
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

apply(from = rootProject.file("examples/plugin-packaging.gradle.kts"))
