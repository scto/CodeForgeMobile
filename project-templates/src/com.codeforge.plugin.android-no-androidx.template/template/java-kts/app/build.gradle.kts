plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "{{packageName}}"
    compileSdk = 35
    

    defaultConfig {
        applicationId = "{{packageName}}"
        minSdk = {{minSdk}}
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { viewBinding = true }

    

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    
}



dependencies {
    implementation("androidx.core:core:1.13.1")
implementation("com.google.android.material:material:1.12.0")
}