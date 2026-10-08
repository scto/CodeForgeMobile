plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.core.data"
}

dependencies {
    implementation(project(":core:resources"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.jgit)
    implementation(libs.androidx.documentfile)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit)
}
