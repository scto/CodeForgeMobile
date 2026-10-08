package com.codeforge.libs.template_engine.templates.gradle

fun RootBuildGradle(useKts: Boolean): String = if (useKts) AppGradleKotlin() else AppGradleGroovy()

fun AppGradleKotlin(): String = """
    plugins {
        alias(libs.plugins.android.application) apply false
        alias(libs.plugins.android.library) apply false
        alias(libs.plugins.kotlin.android) apply false
        alias(libs.plugins.kotlin.compose) apply false
    }

    tasks.register<Delete>("clean") {
        delete(rootProject.layout.buildDirectory)
    }
""".trimIndent()

fun AppGradleGroovy(): String = """
    plugins {
        alias(libs.plugins.android.application) apply false
        alias(libs.plugins.android.library) apply false
        alias(libs.plugins.kotlin.android) apply false
        alias(libs.plugins.kotlin.compose) apply false
    }

    tasks.register('clean', Delete) {
        delete rootProject.buildDir
    }
""".trimIndent()
