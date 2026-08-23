plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.protobuf) apply false
    alias(libs.plugins.dokka)
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}

tasks.dokkaHtmlMultiModule.configure {
    outputDirectory.set(file("$rootDir/docs"))
}