/*
 * :libs:dependency-updater-impl — Implementierung von :libs:dependency-updater-api:
 * Parser (TOML-Katalog + build.gradle[.kts]), Maven-Metadata-Abfrage, Update-Berechnung,
 * Anwenden der Versionen, Dismiss-Speicher, Hilt-Bindings.
 * Parser/Berechnung sind reines Kotlin (JVM-Unit-Tests, keine Android-Abhängigkeit).
 */
plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.dependency_updater_impl"
}

dependencies {
    implementation(project(":core:resources"))
    api(project(":libs:dependency-updater-api"))
    implementation(project(":libs:indexing-api"))
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(project(":libs:indexing-impl"))
}
