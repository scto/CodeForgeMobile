import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "com.codeforge.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    compileOnly(libs.findLibrary("android-gradlePlugin").get())
    compileOnly(libs.findLibrary("kotlin-gradlePlugin").get())
    compileOnly(libs.findLibrary("ksp-gradlePlugin").get())
    compileOnly(libs.findLibrary("hilt-gradlePlugin").get())
    compileOnly(libs.findLibrary("compose-gradlePlugin").get())
    compileOnly(libs.findLibrary("ktlint-gradlePlugin").get())
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "codeforge.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "codeforge.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "codeforge.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "codeforge.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("kotlinLibrary") {
            id = "codeforge.kotlin.library"
            implementationClass = "KotlinLibraryConventionPlugin"
        }
        register("quality") {
            id = "codeforge.quality"
            implementationClass = "QualityConventionPlugin"
        }
        register("terminalBootstrap") {
            id = "codeforge.terminal.bootstrap"
            implementationClass = "TerminalBootstrapPackagesPlugin"
        }
    }
}
