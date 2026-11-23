plugins {
    alias(libs.plugins.codeforge.android.library.compose)
}

android {
    namespace = "com.codeforge.plugin.markdown"
}

// compileOnly: das SDK liefert die App zur Laufzeit; Compose und Coroutines ebenso (nur zum Kompilieren hier).
dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(project(":libs:plugin-ui-api"))
    testImplementation(libs.junit)
}

apply(from = rootProject.file("gradle/code-plugin.gradle.kts"))
