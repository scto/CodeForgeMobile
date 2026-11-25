plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

    buildFeatures { compose = true }

    testOptions { unitTests.isIncludeAndroidResources = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    
}

androidComponents {
    beforeVariants { builder ->
        builder.hostTests[com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
implementation("com.google.android.material:material:1.12.0")
implementation("androidx.activity:activity-compose:1.9.2")
implementation("androidx.compose.material3:material3:1.3.1")
implementation("androidx.compose.ui:ui-tooling-preview:1.7.3")
}