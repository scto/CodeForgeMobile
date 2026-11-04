package com.codeforge.libs.template_engine.templates.gradle

/**
 * Liefert den Text der Vorlagendatei `RootBuildGradle`.
 *
 * @param useKts Wert, der in die Vorlage eingesetzt wird.
 */
fun RootBuildGradle(useKts: Boolean): String = if (useKts) AppGradleKotlin() else AppGradleGroovy()

/** Liefert den Text der Vorlagendatei `AppGradleKotlin`. */
fun AppGradleKotlin(): String = """
    plugins {
        alias(libs.plugins.android.application) apply false
        alias(libs.plugins.android.library) apply false
        alias(libs.plugins.kotlin.compose) apply false
    }

    tasks.register<Delete>("clean") {
        delete(rootProject.layout.buildDirectory)
    }
""".trimIndent()

/** Liefert den Text der Vorlagendatei `AppGradleGroovy`. */
fun AppGradleGroovy(): String = """
    plugins {
        alias(libs.plugins.android.application) apply false
        alias(libs.plugins.android.library) apply false
        alias(libs.plugins.kotlin.compose) apply false
    }

    tasks.register('clean', Delete) {
        delete rootProject.layout.buildDirectory
    }
""".trimIndent()
