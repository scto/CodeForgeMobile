package com.codeforge.feature.x2c

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.X2cProgress

/** Reihenfolge entspricht dem Ablauf in `X2cRepositoryImpl.executeMigration`. */
enum class X2cPhase { ANALYSE, LAYOUTS, THEME, WIRING, BACKUP, BASELINE, APPLY, VERIFY }

enum class X2cStepState { DONE, ACTIVE, PENDING }

@Immutable
data class X2cPhaseStep(val phase: X2cPhase, val state: X2cStepState)

/** Welche Phasen der Lauf überhaupt durchläuft (abhängig von den gewählten Optionen). */
@Immutable
data class X2cPhasePlan(
    val theme: Boolean,
    val wiring: Boolean,
    val baseline: Boolean,
    val verify: Boolean
) {
    val phases: List<X2cPhase> = X2cPhase.entries.filter {
        when (it) {
            X2cPhase.THEME -> theme
            X2cPhase.WIRING -> wiring
            X2cPhase.BASELINE -> baseline
            X2cPhase.VERIFY -> verify
            else -> true
        }
    }
}

/**
 * Unveränderlicher Verlauf der Phasen. Eine neue Phase markiert alle davor liegenden als erledigt, auch wenn
 * zwischendurch kein Fortschrittswert ankam (der Fluss ist konfliert, schnelle Phasen können fehlen).
 */
@Immutable
data class X2cPhaseTracker(
    val plan: X2cPhasePlan,
    val active: X2cPhase? = null,
    val rolledBack: Boolean = false,
    val failed: Boolean = false
) {
    fun onProgress(progress: X2cProgress?): X2cPhaseTracker = when (progress) {
        null, X2cProgress.Idle -> this
        is X2cProgress.Analyzing -> advance(X2cPhase.ANALYSE)
        is X2cProgress.GeneratingComposables -> advance(X2cPhase.LAYOUTS)
        is X2cProgress.GeneratingTheme -> advance(X2cPhase.THEME)
        is X2cProgress.WiringActivities -> advance(X2cPhase.WIRING)
        is X2cProgress.CreatingBackup -> advance(X2cPhase.BACKUP)
        is X2cProgress.ApplyingChanges, is X2cProgress.PreparingBuild -> advance(X2cPhase.APPLY)
        is X2cProgress.VerifyingBuild ->
            // Der Lauf „Vorher bauen“ kommt vor dem Anwenden und hat eine eigene Phase.
            if (plan.baseline && (active == null || active.ordinal < X2cPhase.APPLY.ordinal)) advance(X2cPhase.BASELINE)
            else advance(X2cPhase.VERIFY)
        is X2cProgress.RollingBack -> copy(rolledBack = true)
        is X2cProgress.Failed -> copy(failed = true)
        is X2cProgress.Completed -> this
    }

    private fun advance(phase: X2cPhase): X2cPhaseTracker {
        val current = active
        return if (current != null && phase.ordinal < current.ordinal) this else copy(active = phase)
    }

    fun steps(): List<X2cPhaseStep> {
        val current = active
        return plan.phases.map { phase ->
            val state = when {
                current == null -> X2cStepState.PENDING
                phase == current -> X2cStepState.ACTIVE
                phase.ordinal < current.ordinal -> X2cStepState.DONE
                else -> X2cStepState.PENDING
            }
            X2cPhaseStep(phase, state)
        }
    }
}

/** Anteil innerhalb der aktuellen Phase (nur dort, wo eine Zählung vorliegt). */
fun X2cProgress?.phaseFraction(): Float? = when (this) {
    is X2cProgress.GeneratingComposables -> if (total > 0) current.toFloat() / total else null
    is X2cProgress.WiringActivities -> if (total > 0) current.toFloat() / total else null
    else -> null
}

/** Gesamtanteil für die Benachrichtigung, 0..100; `null` = unbestimmt. */
fun X2cPhaseTracker.overallPercent(progress: X2cProgress?): Int? {
    val phases = plan.phases
    val current = active ?: return null
    val index = phases.indexOf(current)
    if (index < 0) return null
    val inner = progress.phaseFraction() ?: 0f
    return (((index + inner) / phases.size) * 100f).toInt().coerceIn(0, 99)
}
