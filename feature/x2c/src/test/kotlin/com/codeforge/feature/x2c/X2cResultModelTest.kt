package com.codeforge.feature.x2c

import com.codeforge.core.domain.model.X2cBuildProblem
import com.codeforge.core.domain.model.X2cBuildStepResult
import com.codeforge.core.domain.model.X2cBuildStepStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X2cResultModelTest {

    private fun step(status: X2cBuildStepStatus, vararg problems: X2cBuildProblem) =
        X2cBuildStepResult(listOf("assembleDebug"), status, problems.toList())

    private fun error(msg: String, path: String? = "A.kt", line: Int? = 1, preExisting: Boolean = false) =
        X2cBuildProblem("error", path, line, null, msg, preExisting = preExisting)

    @Test
    fun `no steps and no verdict means the build did not run`() {
        assertEquals(X2cBuildBadge.NOT_RUN, buildBadge(testReport()))
    }

    @Test
    fun `explicit verdict wins over the steps`() {
        val ok = testReport(buildSteps = listOf(step(X2cBuildStepStatus.FAILED))).copy(buildVerificationSuccess = true)
        assertEquals(X2cBuildBadge.SUCCESS, buildBadge(ok))
        assertEquals(X2cBuildBadge.FAILED, buildBadge(ok.copy(buildVerificationSuccess = false)))
    }

    @Test
    fun `steps decide without a verdict`() {
        assertEquals(X2cBuildBadge.FAILED, buildBadge(testReport(buildSteps = listOf(step(X2cBuildStepStatus.SUCCESS), step(X2cBuildStepStatus.FAILED)))))
        assertEquals(X2cBuildBadge.SKIPPED, buildBadge(testReport(buildSteps = listOf(step(X2cBuildStepStatus.SKIPPED)))))
        assertEquals(X2cBuildBadge.SUCCESS, buildBadge(testReport(buildSteps = listOf(step(X2cBuildStepStatus.SUCCESS), step(X2cBuildStepStatus.SKIPPED)))))
    }

    @Test
    fun `a rerun replaces the earlier verdict`() {
        val failed = testReport().copy(buildVerificationSuccess = false)
        assertEquals(X2cBuildBadge.SUCCESS, buildBadge(failed, step(X2cBuildStepStatus.SUCCESS)))
    }

    @Test
    fun `errors list only errors, own errors first, duplicates removed, limited`() {
        val steps = listOf(
            step(
                X2cBuildStepStatus.FAILED,
                error("alt", preExisting = true),
                error("neu"),
                error("neu"),
                X2cBuildProblem("warning", "B.kt", 2, null, "nur Warnung")
            )
        )
        val errors = buildErrors(testReport(buildSteps = steps))
        assertEquals(listOf("neu", "alt"), errors.map { it.message })

        val many = List(30) { error("e$it", line = it) }
        assertEquals(5, buildErrors(testReport(buildSteps = listOf(step(X2cBuildStepStatus.FAILED, *many.toTypedArray()))), limit = 5).size)
    }

    @Test
    fun `rerun errors replace the errors of the migration run`() {
        val report = testReport(buildSteps = listOf(step(X2cBuildStepStatus.FAILED, error("vorher"))))
        val rerun = step(X2cBuildStepStatus.FAILED, error("nachher"))
        assertEquals(listOf("nachher"), buildErrors(report, rerun).map { it.message })
        assertTrue(buildErrors(report, step(X2cBuildStepStatus.SUCCESS)).isEmpty())
    }
}
