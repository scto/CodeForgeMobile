plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.codeforge.quality)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.codeforge.core.domain"
}

dependencies {
    api(project(":libs:j2k"))
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
