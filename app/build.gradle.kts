plugins {
    alias(libs.plugins.codeforge.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.app"

    defaultConfig {
        applicationId = "com.codeforge.app"
        versionCode = 30000
        versionName = "3.0.0"

        // Wird von :libs:termux-shared per Reflection aus `com.codeforge.app.BuildConfig` gelesen
        // (TermuxConstants.BUILD_CONFIG_CLASS_NAME / TermuxBootstrap) — muss zum Bootstrap-Paketformat passen.
        buildConfigField("String", "TERMUX_PACKAGE_VARIANT", "\"apt-android-7\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }
}

dependencies {
    implementation(project(":core:resources"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:navigation"))
    implementation(project(":core:datastore"))
    implementation(project(":core:data"))

    implementation(project(":feature:onboarding"))
    implementation(project(":feature:welcome"))
    implementation(project(":feature:projectwizard"))
    implementation(project(":feature:editor"))
    implementation(project(":feature:composepreview"))
    implementation(project(":feature:filetree"))
    implementation(project(":feature:terminal"))
    implementation(project(":feature:sdkmanager"))
    implementation(project(":feature:layoutdesigner"))
    implementation(project(":feature:themebuilder"))
    implementation(project(":feature:git"))
    implementation(project(":feature:search"))
    implementation(project(":feature:modulemaker"))
    implementation(project(":feature:plugins"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:dependencyupdates"))

    implementation(project(":libs:terminal-engine"))
    implementation(project(":libs:template-engine"))
    implementation(project(":libs:gradle-tooling-bridge"))
    implementation(project(":libs:lsp-client"))
    implementation(project(":libs:plugin-api"))
    // Hilt-Bindings für Indexierung + Dependency-Updater (Features sehen nur die -api-Module)
    implementation(project(":libs:indexing-api"))
    implementation(project(":libs:indexing-impl"))
    implementation(project(":libs:dependency-updater-api"))
    implementation(project(":libs:dependency-updater-impl"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.activity.compose)
    // Theme.Material3.DayNight.NoActionBar (XML-Fenster-Theme in res/values/themes.xml)
    implementation(libs.google.material)
    implementation("androidx.compose.material:material-icons-extended:1.7.3")

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
