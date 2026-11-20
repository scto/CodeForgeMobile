plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.plugin_sandbox"
    testOptions.unitTests.isIncludeAndroidResources = true
}

// Sandbox für Code-Plugins: isolierter Prozess (android:isolatedProcess), Protokoll, Stellvertreter in der App.
dependencies {
    api(project(":libs:plugin-api"))
    implementation(project(":core:domain"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
}
