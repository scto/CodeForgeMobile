plugins {
    alias(libs.plugins.codeforge.android.library.compose)
}

android {
    namespace = "com.codeforge.plugin.gradle"
}

dependencies {
    compileOnly(project(":libs:plugin-api"))
    compileOnly(project(":libs:plugin-ui-api"))
    testImplementation(libs.junit)
}

apply(from = "../code-plugin.gradle.kts")
