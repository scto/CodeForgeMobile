// buildSrc/build.gradle.kts
plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.6")
    // AGP muss im buildSrc-Classpath verfügbar sein, damit Detekt's optionale
    // Android-Integration (BaseExtension-Referenz) beim Plugin-Apply auflösbar ist
    compileOnly("com.android.tools.build:gradle:8.5.2")
}

kotlin {
    jvmToolchain(17)
}
