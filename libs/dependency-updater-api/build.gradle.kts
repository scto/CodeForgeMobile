/*
 * :libs:dependency-updater-api
 *
 * Reine JVM-Library: Modelle + Schnittstelle des Dependency-Updaters (Update-Erkennung für
 * Projekte mit TOML-Versionskatalog oder reinen build.gradle(.kts)-Deklarationen).
 * Implementierung: :libs:dependency-updater-impl.
 */
plugins {
    alias(libs.plugins.codeforge.kotlin.library)
    alias(libs.plugins.codeforge.quality)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
}
