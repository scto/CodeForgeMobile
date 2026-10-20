plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.core.domain"
}

dependencies {
    implementation(project(":core:resources"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:common"))

    // javax.inject für Hilt-annotierte UseCases ohne Hilt-Plugin-Abhängigkeit in :core:domain
    implementation("javax.inject:javax.inject:1")
    implementation(libs.kotlinx.serialization.json)
}

dependencies {
    testImplementation(libs.junit)
}

dependencies {
    testImplementation(project(":core:testing"))
}
