/*
 * :libs:indexing-impl — Implementierung von :libs:indexing-api (Dateiscan, settings.gradle-Parser,
 * Repository-Parser, gecachter ProjectIndexer + Hilt-Binding).
 * Die Parser sind reines Kotlin ohne Android-Abhängigkeit (JVM-Unit-Tests).
 */
plugins {
    alias(libs.plugins.codeforge.android.library)
    alias(libs.plugins.codeforge.android.hilt)
    alias(libs.plugins.codeforge.quality)
}

android {
    namespace = "com.codeforge.libs.indexing_impl"
}

dependencies {
    implementation(project(":core:resources"))
    api(project(":libs:indexing-api"))
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    testImplementation(libs.junit)
}
