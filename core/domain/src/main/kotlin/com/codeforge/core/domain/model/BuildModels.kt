/**
 * Modul: :core:domain
 * @author Thomas Schmid
 */
package com.codeforge.core.domain.model

enum class ProblemSeverity {
    ERROR,
    WARNING,
    INFO,
}

enum class ProblemSource {
    KOTLIN,
    JAVA,
    AAPT,
    GRADLE,
    OTHER,
}

data class BuildProblem(
    val severity: ProblemSeverity,
    val source: ProblemSource,
    val path: String?,
    val line: Int?,
    val column: Int?,
    val message: String,
    val taskName: String? = null,
)

enum class BuildStatus {
    IDLE,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class BuildSession(
    val taskName: String,
    val startTimeMillis: Long,
    val durationMillis: Long = 0L,
    val status: BuildStatus = BuildStatus.RUNNING,
    val totalErrors: Int = 0,
    val totalWarnings: Int = 0,
)
