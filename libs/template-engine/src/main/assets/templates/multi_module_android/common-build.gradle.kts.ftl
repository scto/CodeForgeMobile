plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "${packageName}.common"
    compileSdk = 35

    defaultConfig {
        minSdk = ${minSdk}
    }
}
