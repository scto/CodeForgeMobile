package com.codeforge.libs.template_engine.templates.gradle

fun GradleProperties(addAndroidX: Boolean): String = """
    # Project-wide Gradle settings.
    org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
    android.useAndroidX=$addAndroidX
    android.nonTransitiveRClass=true
    kotlin.code.style=official
""".trimIndent()
