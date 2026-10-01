plugins {
    id("codeforge.android.library")
}

android {
    namespace = "com.codeforge.libs.template_engine"
}

dependencies {
    implementation(project(":core:common"))
}
