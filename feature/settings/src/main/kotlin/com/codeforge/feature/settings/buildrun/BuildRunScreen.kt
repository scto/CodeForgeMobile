/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.buildrun

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.GradleLogLevel
import com.codeforge.core.domain.model.GradleRunSettings
import com.codeforge.core.domain.model.GradleStacktrace
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import com.codeforge.feature.settings.common.JvmOptionsSheet
import kotlinx.coroutines.flow.collectLatest

/** Route „Build & Run“. */
@Composable
fun BuildRunRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: BuildRunViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { if (it is BuildRunEffect.ShowSnackbar) snackbar.showSnackbar(it.message) }
    }
    BuildRunScreen(state = state, snackbarHostState = snackbar, onBack = onBack, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Zustandslose Oberfläche von „Build & Run“. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuildRunScreen(
    state: BuildRunState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEvent: (BuildRunEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = state.settings
    var jvmSheet by rememberSaveable { mutableStateOf(false) }
    val busy = state.running != null

    state.pendingConfirm?.let { action ->
        AlertDialog(
            onDismissRequest = { onEvent(BuildRunEvent.DismissConfirm) },
            title = { Text(stringRes(action.titleRes)) },
            text = {
                Text(
                    when (action) {
                        BuildRunAction.DELETE_PROJECT_GRADLE -> stringRes(R.string.buildrun_confirm_project, state.projectPath)
                        BuildRunAction.DELETE_USER_CACHES -> stringRes(R.string.buildrun_confirm_user_caches, state.gradleUserHome)
                        else -> stringRes(R.string.buildrun_confirm_build_cache, state.gradleUserHome)
                    }
                )
            },
            confirmButton = { TextButton(onClick = { onEvent(BuildRunEvent.Confirm) }) { Text(stringRes(R.string.buildrun_delete)) } },
            dismissButton = { TextButton(onClick = { onEvent(BuildRunEvent.DismissConfirm) }) { Text(stringRes(R.string.common_abbrechen)) } },
        )
    }
    if (jvmSheet) {
        JvmOptionsSheet(
            title = stringRes(R.string.buildrun_daemon_jvm),
            subtitle = stringRes(R.string.buildrun_daemon_jvm_applied),
            initial = settings.daemonJvm ?: GradleRunSettings.DEFAULT_DAEMON_JVM,
            onSave = { jvmSheet = false; onEvent(BuildRunEvent.SaveDaemonJvm(it)) },
            onReset = { jvmSheet = false; onEvent(BuildRunEvent.SaveDaemonJvm(null)) },
            onDismiss = { jvmSheet = false },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { SettingsTopBar(title = stringRes(R.string.buildrun_title), onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionTitle(R.string.buildrun_section_options)
            Block {
                Text(stringRes(R.string.buildrun_log_level), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradleLogLevel.entries.forEach { level ->
                        FilterChip(
                            selected = settings.logLevel == level,
                            onClick = { onEvent(BuildRunEvent.SetLogLevel(level)) },
                            label = { Text(level.flag ?: stringRes(R.string.buildrun_default)) },
                        )
                    }
                }
                Text(stringRes(R.string.buildrun_stacktrace), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradleStacktrace.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.stacktrace == mode,
                            onClick = { onEvent(BuildRunEvent.SetStacktrace(mode)) },
                            label = { Text(mode.flag ?: stringRes(R.string.buildrun_off)) },
                        )
                    }
                }
            }
            Block {
                SwitchRow("--offline", R.string.buildrun_offline_sum, settings.offline) { onEvent(BuildRunEvent.SetOffline(it)) }
                SwitchRow("--no-build-cache", R.string.buildrun_no_build_cache_sum, settings.noBuildCache) { onEvent(BuildRunEvent.SetNoBuildCache(it)) }
                SwitchRow("--no-daemon", R.string.buildrun_no_daemon_sum, settings.noDaemon) { onEvent(BuildRunEvent.SetNoDaemon(it)) }
                SwitchRow("--continue", R.string.buildrun_continue_sum, settings.continueOnFailure) { onEvent(BuildRunEvent.SetContinue(it)) }
            }
            Block {
                val invalid = remember(settings.extraArgs) { settings.firstInvalidExtraArg() }
                OutlinedTextField(
                    value = settings.extraArgs,
                    onValueChange = { onEvent(BuildRunEvent.SetExtraArgs(it)) },
                    label = { Text(stringRes(R.string.buildrun_extra_args)) },
                    isError = invalid != null,
                    supportingText = {
                        Text(if (invalid != null) stringRes(R.string.jvm_invalid, invalid) else stringRes(R.string.buildrun_extra_args_hint))
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringRes(R.string.buildrun_effective, settings.toArgs().joinToString(" ").ifEmpty { "–" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionTitle(R.string.buildrun_section_service)
            Block {
                Text(stringRes(R.string.buildrun_gradle_home), style = MaterialTheme.typography.titleSmall)
                Text(stringRes(R.string.buildrun_gradle_home_sum), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.gradleHomeInput,
                    onValueChange = { onEvent(BuildRunEvent.GradleHomeInputChanged(it)) },
                    singleLine = true,
                    label = { Text(stringRes(R.string.buildrun_gradle_home_path)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { onEvent(BuildRunEvent.SaveGradleHome) },
                    enabled = state.gradleHomeInput.trim() != settings.customGradleHome,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringRes(R.string.sdkmanager_uebernehmen)) }
            }
            Block {
                Text(stringRes(R.string.buildrun_daemon_jvm), style = MaterialTheme.typography.titleSmall)
                Text(
                    settings.daemonJvm?.toArgs()?.joinToString(" ")?.ifEmpty { null } ?: stringRes(R.string.buildrun_daemon_jvm_project),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { jvmSheet = true }, modifier = Modifier.fillMaxWidth()) { Text(stringRes(R.string.buildrun_setup)) }
            }

            SectionTitle(R.string.buildrun_section_maintenance)
            Block {
                Text(
                    if (state.projectPath.isBlank()) stringRes(R.string.buildrun_no_project)
                    else stringRes(R.string.buildrun_project, state.projectName, state.projectPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ActionButton(BuildRunAction.CLEAR_BUILD_CACHE, state, enabled = !busy, onEvent = onEvent)
                ActionButton(BuildRunAction.DELETE_PROJECT_GRADLE, state, enabled = !busy && state.projectPath.isNotBlank(), onEvent = onEvent)
                ActionButton(BuildRunAction.DELETE_USER_CACHES, state, enabled = !busy, onEvent = onEvent)
            }
            Block {
                Text(stringRes(R.string.buildrun_wrapper), style = MaterialTheme.typography.titleSmall)
                Text(stringRes(R.string.buildrun_wrapper_sum), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = state.wrapperVersion,
                    onValueChange = { onEvent(BuildRunEvent.WrapperVersionChanged(it)) },
                    singleLine = true,
                    isError = !state.wrapperVersionValid,
                    label = { Text(stringRes(R.string.buildrun_wrapper_version)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                ActionButton(
                    BuildRunAction.UPDATE_WRAPPER, state,
                    enabled = !busy && state.projectPath.isNotBlank() && state.wrapperVersionValid, onEvent = onEvent,
                )
            }
        }
    }
}

private val BuildRunAction.titleRes: Int
    @StringRes get() = when (this) {
        BuildRunAction.CLEAR_BUILD_CACHE -> R.string.buildrun_clear_build_cache
        BuildRunAction.DELETE_PROJECT_GRADLE -> R.string.buildrun_delete_project_gradle
        BuildRunAction.DELETE_USER_CACHES -> R.string.buildrun_delete_user_caches
        BuildRunAction.UPDATE_WRAPPER -> R.string.buildrun_wrapper_update
    }

@Composable
private fun ActionButton(action: BuildRunAction, state: BuildRunState, enabled: Boolean, onEvent: (BuildRunEvent) -> Unit) {
    OutlinedButton(onClick = { onEvent(BuildRunEvent.Request(action)) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringRes(action.titleRes))
    }
    if (state.running == action) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (state.runningOutput.isNotBlank()) {
            Text(state.runningOutput, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SectionTitle(@StringRes title: Int) {
    Text(
        stringRes(title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
    )
}

@Composable
private fun Block(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun SwitchRow(flag: String, @StringRes summary: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(flag, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
            Text(stringRes(summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
