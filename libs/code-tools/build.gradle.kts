/*
 * :libs:code-tools
 *
 * Reine JVM-Library (kein Android): Text-/Projektsuche mit Regex, Formatter, Zeilenbefehle und
 * Submodule-Maker-Logik. Bewusst ohne Android-Abhängigkeiten, damit alles als normaler
 * JUnit-Test läuft und von mehreren Features (:feature:editor, :feature:search,
 * :feature:modulemaker) gemeinsam genutzt werden kann.
 */
plugins {
    alias(libs.plugins.codeforge.kotlin.library)
    alias(libs.plugins.codeforge.quality)
}

dependencies {
    testImplementation(libs.junit)
}
