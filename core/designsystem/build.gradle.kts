plugins {
    id("codeforge.android.library.compose")
}

android {
    namespace = "com.codeforge.core.designsystem"
}

dependencies {
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(project(":core:datastore"))
}
