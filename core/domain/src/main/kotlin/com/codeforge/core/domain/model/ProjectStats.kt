package com.codeforge.core.domain.model

/** Zeilen und Dateien einer Sprache im Projekt. */
data class LanguageStats(
    val language: String,
    val files: Int,
    /** Alle Zeilen. */
    val lines: Long,
    /** Zeilen mit Code (ohne Leer- und reine Kommentarzeilen). */
    val codeLines: Long,
    /** Ob es eine Programmiersprache ist (nicht XML, JSON, Markdown …). */
    val isProgramming: Boolean,
)

/** Codezeilen eines Gradle-Moduls (Ordner mit eigener Build-Datei; `:` = Wurzel). */
data class ModuleStats(val path: String, val files: Int, val codeLines: Long)

/** Eine große Quelldatei. */
data class LargeFile(val relativePath: String, val lines: Long)

/** Git-Angaben; `null`, wenn das Projekt kein Repository ist. */
data class GitStats(
    val branch: String?,
    /** Anzahl Commits; bei [commitsCapped] mindestens so viele. */
    val commits: Int,
    val commitsCapped: Boolean,
    /** Erster Commit (nur bekannt, wenn die ganze Historie gelesen wurde). */
    val firstCommitMillis: Long?,
    val lastCommitMillis: Long?,
    val authors: Int,
)

/** Woher das Erstellungsdatum stammt. */
enum class CreationSource { FIRST_COMMIT, FILESYSTEM, OLDEST_FILE }

/** Auswertung eines Projektordners für den Bildschirm „Projektinfo“. */
data class ProjectStats(
    val name: String,
    val rootPath: String,
    /** Häufigste Programmiersprache nach Codezeilen oder `null`. */
    val primaryLanguage: String?,
    /** Erkannte Build-Systeme, z. B. „Gradle (Kotlin DSL)“, „CMake“. */
    val buildSystems: List<String>,
    /** Projektart, z. B. „Android-App“, „Kotlin Multiplatform“. */
    val projectKinds: List<String>,
    val usesCompose: Boolean,
    val languages: List<LanguageStats>,
    val modules: List<ModuleStats>,
    val largestFiles: List<LargeFile>,
    val totalFiles: Int,
    /** Quelldateien in Programmiersprachen; die folgenden Zeilenzahlen beziehen sich nur auf sie. */
    val sourceFiles: Int,
    val totalLines: Long,
    val codeLines: Long,
    val commentLines: Long,
    val blankLines: Long,
    val sizeBytes: Long,
    val createdMillis: Long?,
    val createdSource: CreationSource?,
    val lastModifiedMillis: Long?,
    /** Gradle-Wrapper-Version, AGP-, Kotlin-Version, SDK-Stände (nur was gefunden wurde). */
    val gradleWrapper: String?,
    val agpVersion: String?,
    val kotlinVersion: String?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val compileSdk: Int?,
    val dependencyCount: Int?,
    val git: GitStats?,
    /** Ob die Dateigrenze erreicht wurde (dann sind alle Zahlen Untergrenzen). */
    val truncated: Boolean,
    val analyzedAtMillis: Long,
)
