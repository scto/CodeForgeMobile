plugins {
    alias(libs.plugins.app.android.library.compose)
}

android {
    namespace = "{{packageName}}.core.designsystem"
}

dependencies {
    implementation(project(":core:resources"))
}
