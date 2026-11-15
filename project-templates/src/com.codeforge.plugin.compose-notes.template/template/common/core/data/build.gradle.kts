plugins {
    alias(libs.plugins.app.android.library)
    alias(libs.plugins.app.android.hilt)
}

android {
    namespace = "{{packageName}}.core.data"
}

dependencies {
    implementation(project(":core:domain"))
}
