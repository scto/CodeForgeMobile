plugins {
    id("codeforge.android.library.compose")
}

android {
    namespace = "com.codeforge.core.ui"
}

dependencies {
    api(project(":core:resources"))
    implementation(libs.kotlinx.coroutines.core)
}
