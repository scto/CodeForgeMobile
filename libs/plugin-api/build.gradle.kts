plugins {
    id("codeforge.android.library")
}

android {
    namespace = "com.codeforge.libs.plugin_api"
}

dependencies {
    implementation(project(":core:common"))
}
