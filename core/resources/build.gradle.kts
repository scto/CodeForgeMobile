plugins {
    id("codeforge.android.library.compose")
}

android {
    namespace = "com.codeforge.core.resources"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
}
