plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "{{packageName}}.feature.profile"
    compileSdk = 35
    defaultConfig { minSdk = {{minSdk}} }
    buildFeatures { compose = true }
    // Compose-Vorschau der IDE: Android-Ressourcen in Unit-Tests
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Die Vorschau-Aufgaben brauchen Unit-Tests in jeder Variante (auch release).
androidComponents {
    beforeVariants { builder ->
        builder.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
