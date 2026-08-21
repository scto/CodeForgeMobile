plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // KEIN AGP-Dependency notwendig, da Detekt-Plugin-Apply
    // aus dem Convention-Plugin entfernt wurde (Fix 3).
    // Custom-Tasks nutzen ausschließlich reine Kotlin/Gradle-APIs.
}

kotlin {
    jvmToolchain(17)
}