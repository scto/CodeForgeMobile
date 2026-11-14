plugins {
    alias(libs.plugins.codeforge.kotlin.library)
    alias(libs.plugins.codeforge.quality)
}

// compileOnly: wie in den Plugin-Modulen selbst — diese Typen sind zur Laufzeit im
// Host-App-Prozess vorhanden und werden über Classloader-Parent-Delegation aufgelöst.
dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(project(":core:domain"))
    compileOnly(project(":libs:terminal-engine"))
    compileOnly(libs.kotlinx.coroutines.core)
}

// Kompiliert mit JDK 21 (wie CI und Termux). Der Bytecode bleibt Java 17 (jvmTarget aus der build-logic),
// passend zu :libs:plugin-api; d8 entzuckert für Android.
kotlin {
    jvmToolchain(21)
}
