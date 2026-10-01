import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    id("codeforge.android.application")
    id("codeforge.android.hilt")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

android {
    namespace = "com.codeforge.app"

    defaultConfig {
        applicationId = "com.codeforge.app"
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    lint {
        disable += "ProtectedPermissions"
        disable += "ExpiredTargetSdkVersion"
        abortOnError = false
        checkReleaseBuilds = false
    }

    packaging {
        resources {
            // kotlin-compiler-embeddable conflicts with kotlin-stdlib
            excludes += "/kotlin/**"
            excludes += "META-INF/**.kotlin_module"
            pickFirsts += "kotlin/coroutines/coroutines.kotlin_builtins"
            pickFirsts += "kotlin/reflect/reflect.kotlin_builtins"
            pickFirsts += "kotlin/kotlin.kotlin_builtins"
            pickFirsts += "kotlin/collections/collections.kotlin_builtins"
            pickFirsts += "kotlin/annotation/annotation.kotlin_builtins"
            pickFirsts += "kotlin/ranges/ranges.kotlin_builtins"
            pickFirsts += "kotlin/internal/internal.kotlin_builtins"
        }
    }
}

ktlint {
    android.set(true)
    ignoreFailures.set(true)
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.HTML)
    }
}

detekt {
    config.setFrom(files("config/detekt/detekt.yml"))
    allRules = true
    autoCorrect = true
}

tasks.named("preBuild") {
    dependsOn(":app:ktlintFormat")
    dependsOn(":app:ktlintCheck")
    // dependsOn(":app:detekt")
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))
    implementation(libs.androidx.datastore.proto)
    implementation(libs.androidx.datastore.preferences)
    implementation(project(":core:ui"))
    implementation(project(":core:resources"))
    implementation(project(":core:navigation"))
    implementation(project(":core:datastore"))
    implementation(project(":core:common"))
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
    implementation(project(":feature:plugins"))
    implementation(project(":feature:settings"))

    implementation(project(":libs:terminal-engine"))
    implementation(project(":libs:template-engine"))
    implementation(project(":libs:gradle-tooling-bridge"))
    implementation(project(":libs:lsp-client"))
    implementation(project(":libs:plugin-api"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
}
