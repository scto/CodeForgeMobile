/*
 * :libs:indexing-api
 *
 * Reine JVM-Library (kein Android): Datenmodelle + Schnittstelle der Projekt-Indexierung
 * (Dateien, Gradle-Module, Versionskataloge, Maven-Repositories). Implementierung in
 * :libs:indexing-impl. Konsumenten (Feature-Module, :libs:dependency-updater-impl)
 * hängen ausschließlich von diesem -api-Modul ab.
 */
plugins {
    alias(libs.plugins.codeforge.kotlin.library)
    alias(libs.plugins.codeforge.quality)
}

dependencies {
    // api: Flow-Typen erscheinen in der öffentlichen Schnittstelle (ProjectIndexer.observe)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
