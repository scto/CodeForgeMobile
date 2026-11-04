/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

/** Schweregrad eines Build-Problems. */
enum class ProblemSeverity {
    ERROR,
    WARNING,
    INFO,
}

/** Quelle eines Build-Problems (Kotlin-Compiler, javac, AAPT2, Gradle). */
enum class ProblemSource {
    KOTLIN,
    JAVA,
    AAPT,
    GRADLE,
    OTHER,
}

/** Ein Problem aus der Build-Ausgabe mit Ort ([path], [line], [column]), Nachricht und ggf. Task. */
data class BuildProblem(
    val severity: ProblemSeverity,
    val source: ProblemSource,
    val path: String?,
    val line: Int?,
    val column: Int?,
    val message: String,
    val taskName: String? = null,
)

/** Zustand eines Builds. */
enum class BuildStatus {
    IDLE,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

/** Eine Build-Sitzung: Task, Startzeit, Dauer, Status und Anzahl Fehler/Warnungen. */
data class BuildSession(
    val taskName: String,
    val startTimeMillis: Long,
    val durationMillis: Long = 0L,
    val status: BuildStatus = BuildStatus.RUNNING,
    val totalErrors: Int = 0,
    val totalWarnings: Int = 0,
)
