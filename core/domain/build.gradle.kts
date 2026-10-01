plugins {
    id("codeforge.android.library")
}

android {
    namespace = "com.codeforge.core.domain"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))

    // javax.inject für Hilt-annotierte UseCases ohne Hilt-Plugin-Abhängigkeit in :core:domain
    implementation("javax.inject:javax.inject:1")
}
