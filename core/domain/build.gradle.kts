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
    api(project(":libs:x2c"))
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

// Optionaler Test gegen ein echtes, gebautes Projekt: CF_GRADLE_SAMPLE=/pfad ./gradlew :core:domain:testDebugUnitTest
android.testOptions.unitTests.all { test ->
    System.getenv("CF_GRADLE_SAMPLE")?.let { test.systemProperty("cf.gradleSample", it) }
    System.getenv("CF_IMPORTED_SAMPLE")?.let { test.systemProperty("cf.importedSample", it) }
}
