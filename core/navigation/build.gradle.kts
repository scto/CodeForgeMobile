plugins {
    id("codeforge.android.library")
    id("codeforge.android.hilt")
}

android {
    namespace = "com.codeforge.core.navigation"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
