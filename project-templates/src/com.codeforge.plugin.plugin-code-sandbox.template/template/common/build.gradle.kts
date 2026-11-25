import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Plugin-API nur zum Kompilieren: zur Laufzeit stellt die App SDK, Kotlin und Coroutines bereit.
dependencies {
    compileOnly(files("libs/codeforge-plugin-sdk.jar"))
    compileOnly(libs.kotlinx.coroutines.core)
    testImplementation(files("libs/codeforge-plugin-sdk.jar"))
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // Nur Kotlin-APIs nutzen, die die Kotlin-Laufzeit der App kennt.
        apiVersion.set(KotlinVersion.KOTLIN_2_1)
        languageVersion.set(KotlinVersion.KOTLIN_2_1)
    }
}

// d8, build-info.json, optionale Signatur (CF_PLUGIN_SIGNING_KEY) → build/plugin/<id>.zip
extra["pluginOutDir"] = "build/plugin"
apply(from = file("gradle/code-plugin.gradle.kts"))
