package com.codeforge.feature.x2c.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.X2cProgress
import com.codeforge.core.resources.R
import com.codeforge.feature.x2c.X2cIntent
import com.codeforge.feature.x2c.X2cPhase
import com.codeforge.feature.x2c.X2cPhasePlan
import com.codeforge.feature.x2c.X2cPhaseStep
import com.codeforge.feature.x2c.X2cPhaseTracker
import com.codeforge.feature.x2c.X2cStepState
import com.codeforge.feature.x2c.X2cUiState
import com.codeforge.feature.x2c.phaseFraction

@Composable
fun X2cProgressScreen(
    state: X2cUiState,
    onIntent: (X2cIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isConverting) return

    val tracker = state.phaseTracker
    val steps = tracker.steps()
    val progress = state.progress
    val active = tracker.active
    val remaining = steps.count { it.state != X2cStepState.DONE }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // Mittig mit begrenzter Breite: im Querformat und auf Tablets nicht über den ganzen Bildschirm gezogen.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.x2c_progress_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                steps.forEach { step -> PhaseRow(step = step, detail = if (step.state == X2cStepState.ACTIVE) phaseDetail(progress) else null) }

                val fraction = progress.phaseFraction()
                // Statusänderungen werden von Screenreadern vorgelesen.
                Column(modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.x2c_progress_remaining, remaining),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(
                            when {
                                tracker.rolledBack -> R.string.x2c_progress_rolling_back
                                active == null || active.ordinal < X2cPhase.APPLY.ordinal -> R.string.x2c_progress_nothing_written
                                else -> R.string.x2c_progress_applying
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedButton(
                    onClick = { onIntent(X2cIntent.CancelMigration) },
                    enabled = !tracker.rolledBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.x2c_progress_cancel))
                }
            }
        }
    }
}

@Composable
private fun phaseDetail(progress: X2cProgress?): String? = when (progress) {
    is X2cProgress.GeneratingComposables ->
        stringResource(R.string.x2c_phase_detail_layouts, progress.current, progress.total, progress.currentLayout)
    is X2cProgress.WiringActivities ->
        stringResource(R.string.x2c_phase_detail_wiring, progress.current, progress.total, progress.currentClass)
    is X2cProgress.VerifyingBuild -> stringResource(R.string.x2c_phase_detail_build, progress.task)
    else -> null
}

@Composable
private fun PhaseRow(
    step: X2cPhaseStep,
    detail: String?,
    modifier: Modifier = Modifier
) {
    val label = stringResource(phaseLabel(step.phase))
    val stateLabel = stringResource(
        when (step.state) {
            X2cStepState.DONE -> R.string.x2c_step_done
            X2cStepState.ACTIVE -> R.string.x2c_step_active
            X2cStepState.PENDING -> R.string.x2c_step_pending
        }
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$label, $stateLabel" },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            when (step.state) {
                X2cStepState.DONE -> Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                X2cStepState.ACTIVE -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp).clearAndSetSemantics { },
                    strokeWidth = 2.dp
                )
                X2cStepState.PENDING -> Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (step.state == X2cStepState.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
                color = if (step.state == X2cStepState.PENDING) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun phaseLabel(phase: X2cPhase): Int = when (phase) {
    X2cPhase.ANALYSE -> R.string.x2c_phase_analyse
    X2cPhase.LAYOUTS -> R.string.x2c_phase_layouts
    X2cPhase.THEME -> R.string.x2c_phase_theme
    X2cPhase.WIRING -> R.string.x2c_phase_wiring
    X2cPhase.BACKUP -> R.string.x2c_phase_backup
    X2cPhase.BASELINE -> R.string.x2c_phase_baseline
    X2cPhase.APPLY -> R.string.x2c_phase_apply
    X2cPhase.VERIFY -> R.string.x2c_phase_verify
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun X2cProgressScreenPreview() {
    val plan = X2cPhasePlan(theme = true, wiring = true, baseline = false, verify = true)
    val progress = X2cProgress.GeneratingComposables(current = 3, total = 12, currentLayout = "activity_main")
    MaterialTheme {
        X2cProgressScreen(
            state = X2cUiState(
                isConverting = true,
                progress = progress,
                phaseTracker = X2cPhaseTracker(plan).onProgress(progress)
            ),
            onIntent = {}
        )
    }
}
