plugins {
    alias(libs.plugins.codeforge.android.library)
}

android {
    namespace = "com.codeforge.plugin.pytest"
}

// compileOnly: SDK (inkl. ShellLaunchSpec aus :libs:terminal-engine) und Coroutines liefert die App zur Laufzeit.
dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(project(":libs:terminal-engine"))
    compileOnly(project(":core:domain"))
    compileOnly(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}

apply(from = rootProject.file("gradle/code-plugin.gradle.kts"))
