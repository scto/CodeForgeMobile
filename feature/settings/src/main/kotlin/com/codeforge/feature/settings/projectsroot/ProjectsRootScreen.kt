/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.projectsroot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import kotlinx.coroutines.flow.collectLatest

/** Route „Projektverzeichnis“. */
@Composable
fun ProjectsRootRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ProjectsRootViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(ProjectsRootEvent.Refresh)
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { if (it is ProjectsRootEffect.ShowSnackbar) snackbar.showSnackbar(it.message) }
    }
    ProjectsRootScreen(modifier = modifier, state = state, snackbarHostState = snackbar, onBack = onBack, onEvent = viewModel::onEvent)
}

/** Zustandslose Oberfläche. */
@Composable
fun ProjectsRootScreen(
    state: ProjectsRootState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEvent: (ProjectsRootEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { SettingsTopBar(title = stringRes(R.string.projroot_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringRes(R.string.projroot_current), style = MaterialTheme.typography.labelLarge)
            Text(state.currentPath, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (state.customPath != null) stringRes(R.string.projroot_source_custom) else stringRes(R.string.projroot_source_default),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.externalUsable) {
                Text(stringRes(R.string.projroot_no_permission), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                value = state.input,
                onValueChange = { onEvent(ProjectsRootEvent.InputChanged(it)) },
                singleLine = true,
                label = { Text(stringRes(R.string.projroot_input_label)) },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { onEvent(ProjectsRootEvent.UseExternalDefault) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringRes(R.string.projroot_use_external, state.externalDefaultPath))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onEvent(ProjectsRootEvent.Apply) }, enabled = state.input.isNotBlank()) { Text(stringRes(R.string.projroot_apply)) }
                OutlinedButton(onClick = { onEvent(ProjectsRootEvent.ResetToDefault) }, enabled = state.customPath != null) {
                    Text(stringRes(R.string.projroot_reset))
                }
            }
            Text(stringRes(R.string.projroot_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
