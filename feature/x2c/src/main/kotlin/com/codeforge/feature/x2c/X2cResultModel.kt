package com.codeforge.feature.x2c

import com.codeforge.core.domain.model.X2cBuildProblem
import com.codeforge.core.domain.model.X2cBuildStepResult
import com.codeforge.core.domain.model.X2cBuildStepStatus
import com.codeforge.core.domain.model.X2cReport

enum class X2cBuildBadge { NOT_RUN, SUCCESS, FAILED, SKIPPED }

/** Gesamtstatus des Builds für die Anzeige. Ein erneuter Lauf („Build ausführen“) hat Vorrang vor dem Lauf der Migration. */
fun buildBadge(report: X2cReport, rerun: X2cBuildStepResult? = null): X2cBuildBadge {
    if (rerun != null) {
        return when (rerun.status) {
            X2cBuildStepStatus.SUCCESS -> X2cBuildBadge.SUCCESS
            X2cBuildStepStatus.FAILED -> X2cBuildBadge.FAILED
            X2cBuildStepStatus.SKIPPED -> X2cBuildBadge.SKIPPED
        }
    }
    report.buildVerificationSuccess?.let { return if (it) X2cBuildBadge.SUCCESS else X2cBuildBadge.FAILED }
    val steps = report.buildSteps
    return when {
        steps.isEmpty() -> X2cBuildBadge.NOT_RUN
        steps.any { it.status == X2cBuildStepStatus.FAILED } -> X2cBuildBadge.FAILED
        steps.all { it.status == X2cBuildStepStatus.SKIPPED } -> X2cBuildBadge.SKIPPED
        else -> X2cBuildBadge.SUCCESS
    }
}

/** Fehler (keine Warnungen) aus dem Lauf, eigene vor bereits vorhandenen; höchstens [limit]. */
fun buildErrors(report: X2cReport, rerun: X2cBuildStepResult? = null, limit: Int = 20): List<X2cBuildProblem> {
    val steps = if (rerun != null) listOf(rerun) else report.buildSteps
    return steps.flatMap { it.problems }
        .filter { it.severity.equals("error", ignoreCase = true) }
        .distinctBy { Triple(it.path, it.line, it.message) }
        .sortedBy { it.preExisting }
        .take(limit)
}
