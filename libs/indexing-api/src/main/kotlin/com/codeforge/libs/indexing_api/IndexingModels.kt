/**
 * Modul: :libs:indexing-api
 * @author Thomas Schmid
 *
 * Datenmodelle der Projekt-Indexierung. Alle Pfade sind absolute Dateisystempfade
 * (java.io.File-Welt) — das Projekt wird nach dem SAF-Import/Clone/Wizard immer als lokaler
 * Ordner geöffnet (siehe docs/bonsai-sora-app-integration.md).
 */
package com.codeforge.libs.indexing_api

/** Art einer indexierten Datei. */
enum class IndexedFileKind {
    KOTLIN_SOURCE,
    JAVA_SOURCE,
    ANDROID_RESOURCE_XML,
    GRADLE_BUILD,
    GRADLE_SETTINGS,
    /** Weitere Gradle-Skripte, z. B. `dependencies.gradle` / `versions.gradle.kts` (Script-Plugins). */
    GRADLE_SCRIPT,
    VERSION_CATALOG,
    GRADLE_PROPERTIES,
    OTHER
}

/** Eine indexierte Datei (Pfad, relativer Pfad, Größe, Änderungszeit, Art). */
data class IndexedFile(
    val path: String,
    val relativePath: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val kind: IndexedFileKind
) {
    /** Dateiname ohne Verzeichnis. */
    val name: String get() = relativePath.substringAfterLast('/')
}

/**
 * Ein Gradle-Modul aus `settings.gradle(.kts)` (`include(":feature:editor")`). Das Root-Projekt
 * hat den Gradle-Pfad ":".
 */
data class GradleModule(
    val gradlePath: String,
    val directory: String,
    /** Absoluter Pfad zu `build.gradle(.kts)` des Moduls, `null` wenn keine Build-Datei existiert. */
    val buildFile: String?
) {
    /** Ob es das Root-Projekt ist. */
    val isRoot: Boolean get() = gradlePath == ":"
    /** Kurzname des Moduls. */
    val name: String get() = if (isRoot) ":" else gradlePath.substringAfterLast(':')
}

/** Bereich, für den ein Repository gilt: Plugins oder Abhängigkeiten. */
enum class RepositoryScope {
    /** `pluginManagement { repositories { } }` und `buildscript { repositories { } }` */
    PLUGINS,
    /** `dependencyResolutionManagement { repositories { } }` und modul-/projektweite `repositories { }` */
    DEPENDENCIES
}

/** Ein Maven-Repository; [url] endet immer auf '/'. */
data class MavenRepository(val name: String, val url: String) {
    companion object {
        fun normalizeUrl(url: String): String = url.trim().let { if (it.endsWith("/")) it else "$it/" }
    }
}

/** Häufig verwendete Repositories. */
object WellKnownRepositories {
    /** Google Maven. */
    val GOOGLE = MavenRepository("google", "https://dl.google.com/dl/android/maven2/")
    /** Maven Central. */
    val MAVEN_CENTRAL = MavenRepository("mavenCentral", "https://repo.maven.apache.org/maven2/")
    /** Gradle Plugin Portal. */
    val GRADLE_PLUGIN_PORTAL = MavenRepository("gradlePluginPortal", "https://plugins.gradle.org/m2/")
}

/** Deklaration eines Repositories mit Bereich und Datei. */
data class RepositoryDeclaration(
    val repository: MavenRepository,
    val scope: RepositoryScope,
    /** Absoluter Pfad der Datei, in der das Repository deklariert ist. */
    val declaredIn: String
)

/** Ergebnis der Indexierung: Dateien, Module, Kataloge, Repositories und Zeitpunkt. */
data class ProjectIndex(
    val rootPath: String,
    val rootProjectName: String,
    val files: List<IndexedFile>,
    val settingsFile: String?,
    /** Enthält immer das Root-Projekt (":") an erster Stelle, danach alle `include`-Module. */
    val modules: List<GradleModule>,
    /** Absolute Pfade aller TOML-Versionskataloge (`gradle/libs.versions.toml`, in settings deklarierte). */
    val versionCatalogs: List<String>,
    val repositories: List<RepositoryDeclaration>,
    val indexedAtMillis: Long
) {
    /** Ob das Projekt einen Version Catalog hat. */
    val hasVersionCatalog: Boolean get() = versionCatalogs.isNotEmpty()

    /** Alle Build-Dateien aller Module (inkl. Root), ohne Duplikate, in Modul-Reihenfolge. */
    val buildFiles: List<String> get() = modules.mapNotNull { it.buildFile }.distinct()

    /** Alle Dateien der Art [kind]. */
    fun filesOfKind(kind: IndexedFileKind): List<IndexedFile> = files.filter { it.kind == kind }

    /** Alle Dateien mit dem Namen [fileName]. */
    fun findByName(fileName: String): List<IndexedFile> = files.filter { it.name == fileName }
}
