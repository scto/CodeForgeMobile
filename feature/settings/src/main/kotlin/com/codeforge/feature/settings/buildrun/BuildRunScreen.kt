/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.buildrun

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import com.codeforge.core.domain.model.JvmOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.InputChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.layout.size
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
import com.codeforge.core.domain.model.GradleRunSettings
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
                // Aktive Flags: wie sie an jeden Gradle-Lauf angehängt werden; Antippen entfernt.
                val active = settings.activeFlags()
                if (active.isEmpty()) {
                    Text(
                        stringRes(R.string.buildrun_no_flags),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        active.forEach { flag ->
                            InputChip(
                                selected = true,
                                onClick = { onEvent(BuildRunEvent.RemoveFlag(flag)) },
                                label = { Text(flag, fontFamily = FontFamily.Monospace) },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, contentDescription = stringRes(R.string.buildrun_remove_flag, flag), modifier = Modifier.size(18.dp))
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(stringRes(R.string.buildrun_common_flags), style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GradleRunSettings.COMMON_FLAGS.forEach { flag ->
                        FilterChip(
                            selected = settings.isActive(flag),
                            onClick = { onEvent(BuildRunEvent.ToggleFlag(flag)) },
                            label = { Text(flag, fontFamily = FontFamily.Monospace) },
                        )
                    }
                }
                Text(stringRes(R.string.buildrun_flags_exclusive), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringRes(R.string.buildrun_custom_flag), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val input = state.customFlagInput.trim()
                    val valid = input.isEmpty() || JvmOptions.isValidFlag(input)
                    OutlinedTextField(
                        value = state.customFlagInput,
                        onValueChange = { onEvent(BuildRunEvent.CustomFlagInputChanged(it)) },
                        singleLine = true,
                        isError = !valid,
                        placeholder = { Text(stringRes(R.string.buildrun_custom_flag_hint), fontFamily = FontFamily.Monospace) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onEvent(BuildRunEvent.AddCustomFlag) }),
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalIconButton(
                        onClick = { onEvent(BuildRunEvent.AddCustomFlag) },
                        enabled = input.isNotEmpty() && valid,
                    ) { Icon(Icons.Filled.Add, contentDescription = stringRes(R.string.buildrun_add_flag)) }
                }
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
            Block {
                Text(stringRes(R.string.buildrun_idle), style = MaterialTheme.typography.titleSmall)
                Text(stringRes(R.string.buildrun_idle_sum), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradleRunSettings.IDLE_CHOICES.forEach { minutes ->
                        FilterChip(
                            selected = settings.daemonIdleMinutes == minutes,
                            onClick = { onEvent(BuildRunEvent.SetDaemonIdleMinutes(minutes)) },
                            label = { Text(if (minutes == 0) stringRes(R.string.buildrun_default) else stringRes(R.string.buildrun_idle_minutes, minutes)) },
                        )
                    }
                }
            }

            SectionTitle(R.string.memory_section)
            Block {
                Text(stringRes(R.string.memory_sum), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionButton(BuildRunAction.RELEASE_MEMORY, state, enabled = !busy, onEvent = onEvent)
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
        BuildRunAction.RELEASE_MEMORY -> R.string.memory_release
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
