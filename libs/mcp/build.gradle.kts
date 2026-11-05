plugins {
    alias(libs.plugins.codeforge.kotlin.library)
    alias(libs.plugins.codeforge.quality)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    api(project(":libs:agent"))

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
