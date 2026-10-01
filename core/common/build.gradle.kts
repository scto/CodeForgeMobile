plugins {
    id("codeforge.android.library")
    id("codeforge.android.hilt")
}

android {
    namespace = "com.codeforge.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}
