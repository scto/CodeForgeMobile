plugins {
    alias(libs.plugins.app.android.application)
}

android {
    namespace = "{{packageName}}"

    defaultConfig {
        applicationId = "{{packageName}}"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:resources"))
    implementation(project(":feature:today"))
    implementation(project(":feature:tasks"))
    implementation(project(":feature:profile"))
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}
