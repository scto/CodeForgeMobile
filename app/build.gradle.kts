plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    id("analysis-tools")
}

android {
    namespace = "com.codeforge.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codeforge.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":core:datastore"))
    implementation(project(":core:data"))

    implementation(project(":feature:onboarding"))
    implementation(project(":feature:welcome"))
    implementation(project(":feature:projectwizard"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:filetree"))
    implementation(project(":feature:terminal"))
    implementation(project(":feature:layoutdesigner"))
    implementation(project(":feature:themebuilder"))
    implementation(project(":feature:git"))
    implementation(project(":feature:plugins"))
    implementation(project(":feature:settings"))

    implementation(project(":libs:terminal-engine"))
    implementation(project(":libs:template-engine"))
    implementation(project(":libs:gradle-tooling-bridge"))
    implementation(project(":libs:lsp-client"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
}
