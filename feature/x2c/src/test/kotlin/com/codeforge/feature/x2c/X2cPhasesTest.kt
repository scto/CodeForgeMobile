package com.codeforge.feature.x2c

import com.codeforge.core.domain.model.X2cProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class X2cPhasesTest {

    private val full = X2cPhasePlan(theme = true, wiring = true, baseline = true, verify = true)
    private val minimal = X2cPhasePlan(theme = false, wiring = false, baseline = false, verify = false)

    @Test
    fun `plan lists only phases the options need, in pipeline order`() {
        assertEquals(
            listOf(X2cPhase.ANALYSE, X2cPhase.LAYOUTS, X2cPhase.THEME, X2cPhase.WIRING, X2cPhase.BACKUP, X2cPhase.BASELINE, X2cPhase.APPLY, X2cPhase.VERIFY),
            full.phases
        )
        assertEquals(listOf(X2cPhase.ANALYSE, X2cPhase.LAYOUTS, X2cPhase.BACKUP, X2cPhase.APPLY), minimal.phases)
    }

    @Test
    fun `before the first progress everything is pending`() {
        val steps = X2cPhaseTracker(full).steps()
        assertTrue(steps.all { it.state == X2cStepState.PENDING })
    }

    @Test
    fun `a phase marks all earlier phases as done`() {
        val tracker = X2cPhaseTracker(full).onProgress(X2cProgress.CreatingBackup("/p"))
        val states = tracker.steps().associate { it.phase to it.state }
        assertEquals(X2cStepState.DONE, states[X2cPhase.ANALYSE])
        assertEquals(X2cStepState.DONE, states[X2cPhase.WIRING])
        assertEquals(X2cStepState.ACTIVE, states[X2cPhase.BACKUP])
        assertEquals(X2cStepState.PENDING, states[X2cPhase.APPLY])
    }

    @Test
    fun `phases never move backwards`() {
        val tracker = X2cPhaseTracker(full)
            .onProgress(X2cProgress.ApplyingChanges("x"))
            .onProgress(X2cProgress.Analyzing("late value"))
        assertEquals(X2cPhase.APPLY, tracker.active)
    }

    @Test
    fun `build before apply is the baseline, after apply it is the verification`() {
        val baseline = X2cPhaseTracker(full)
            .onProgress(X2cProgress.CreatingBackup("/p"))
            .onProgress(X2cProgress.VerifyingBuild("Vorher bauen"))
        assertEquals(X2cPhase.BASELINE, baseline.active)

        val verify = baseline
            .onProgress(X2cProgress.ApplyingChanges("x"))
            .onProgress(X2cProgress.VerifyingBuild(":app:compileDebugKotlin"))
        assertEquals(X2cPhase.VERIFY, verify.active)
    }

    @Test
    fun `without baseline option an early build step is the verification`() {
        val tracker = X2cPhaseTracker(X2cPhasePlan(theme = false, wiring = false, baseline = false, verify = true))
            .onProgress(X2cProgress.VerifyingBuild("assemble"))
        assertEquals(X2cPhase.VERIFY, tracker.active)
    }

    @Test
    fun `preparing the build counts as applying`() {
        assertEquals(X2cPhase.APPLY, X2cPhaseTracker(full).onProgress(X2cProgress.PreparingBuild("x")).active)
    }

    @Test
    fun `rollback and failure keep the phase and set flags`() {
        val base = X2cPhaseTracker(full).onProgress(X2cProgress.ApplyingChanges("x"))
        val rolled = base.onProgress(X2cProgress.RollingBack("x"))
        assertTrue(rolled.rolledBack)
        assertEquals(X2cPhase.APPLY, rolled.active)
        assertTrue(base.onProgress(X2cProgress.Failed("e")).failed)
        assertFalse(base.rolledBack)
    }

    @Test
    fun `idle and null change nothing`() {
        val base = X2cPhaseTracker(full)
        assertEquals(base, base.onProgress(null))
        assertEquals(base, base.onProgress(X2cProgress.Idle))
    }

    @Test
    fun `fraction exists only for counted phases`() {
        assertEquals(0.25f, X2cProgress.GeneratingComposables(1, 4, "a").phaseFraction()!!, 0.0001f)
        assertEquals(0.5f, X2cProgress.WiringActivities(2, 4, "A").phaseFraction()!!, 0.0001f)
        assertNull(X2cProgress.GeneratingComposables(1, 0, "a").phaseFraction())
        assertNull(X2cProgress.ApplyingChanges("x").phaseFraction())
        assertNull((null as X2cProgress?).phaseFraction())
    }

    @Test
    fun `overall percent grows with the phase and stays below 100`() {
        val early = X2cPhaseTracker(minimal).onProgress(X2cProgress.Analyzing("x"))
        val mid = X2cPhaseTracker(minimal).onProgress(X2cProgress.GeneratingComposables(2, 4, "a"))
        val late = X2cPhaseTracker(minimal).onProgress(X2cProgress.ApplyingChanges("x"))
        assertEquals(0, early.overallPercent(X2cProgress.Analyzing("x")))
        assertEquals(37, mid.overallPercent(X2cProgress.GeneratingComposables(2, 4, "a")))
        assertEquals(75, late.overallPercent(X2cProgress.ApplyingChanges("x")))
        assertNull(X2cPhaseTracker(minimal).overallPercent(null))
    }

    @Test
    fun `completed keeps the last state`() {
        val tracker = X2cPhaseTracker(minimal).onProgress(X2cProgress.ApplyingChanges("x"))
        assertEquals(tracker, tracker.onProgress(X2cProgress.Completed(testReport())))
    }
}
