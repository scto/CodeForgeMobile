@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
/**
 * Modul: :feature:settings:debug
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.debug

import com.codeforge.core.resources.ResGetter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeforge.core.datastore.proto.LogLevelProto

@Composable
fun DebugSettingsRoute(
    viewModel: DebugSettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DebugSettingsScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
fun DebugSettingsScreen(
    uiState: DebugSettingsUiState,
    onEvent: (DebugSettingsUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_back)
                        )
                    }
                },
                title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.settings_category_debug)) }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_debug_logging_title), style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_debug_enable_logging), style = MaterialTheme.typography.titleSmall)
                            Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_debug_enable_logging_sub), style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = uiState.loggingEnabled,
                            onCheckedChange = { onEvent(DebugSettingsUiEvent.LoggingToggled(it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Erweitertes Tracing (Excessive Tracing)", style = MaterialTheme.typography.titleSmall)
                            Text("Ausführliches Event- und Flow-Tracing für Fehlersuche", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = uiState.excessiveTracingEnabled,
                            onCheckedChange = { onEvent(DebugSettingsUiEvent.ExcessiveTracingToggled(it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }
            }

            Text("Protokollierstufe (Log Level)", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val levels = listOf(
                        LogLevelProto.LOG_LEVEL_VERBOSE to "VERBOSE (Alle Details)",
                        LogLevelProto.LOG_LEVEL_DEBUG to "DEBUG (Entwickler-Infos)",
                        LogLevelProto.LOG_LEVEL_INFO to "INFO (Standard-Informationen)",
                        LogLevelProto.LOG_LEVEL_WARN to "WARN (Warnungen)",
                        LogLevelProto.LOG_LEVEL_ERROR to "ERROR (Nur Fehler)"
                    )
                    levels.forEach { (level, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = uiState.logLevel == level,
                                    onClick = { onEvent(DebugSettingsUiEvent.LogLevelSelected(level)) }
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = uiState.logLevel == level,
                                onClick = { onEvent(DebugSettingsUiEvent.LogLevelSelected(level)) }
                            )
                            Text(label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Text("Onboarding & App-Start Status", style = MaterialTheme.typography.titleMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Onboarding abgeschlossen", style = MaterialTheme.typography.titleSmall)
                            Text("Aktiviert = Direktstart auf Welcome Screen. Deaktiviert = Onboarding beim nächsten App-Start erzwingen.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(
                            checked = uiState.onboardingCompleted,
                            onCheckedChange = { onEvent(DebugSettingsUiEvent.OnboardingCompletedToggled(it)) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                uncheckedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = { onEvent(DebugSettingsUiEvent.ResetOnboardingClicked) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_debug_reset_onboarding))
                    }

                    uiState.onboardingMessage?.let { msg ->
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text("Terminal & Guest OS Wartung (Wipe)", style = MaterialTheme.typography.titleMedium)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ubuntu System zurücksetzen (Terminal Wipe)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Löscht das installierte Ubuntu-Rootfs vollständig vom Gerät. Beim nächsten Aufruf des Terminals wird das System frisch installiert.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    androidx.compose.material3.Button(
                        onClick = { onEvent(DebugSettingsUiEvent.WipeTerminalClicked) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isWipingTerminal
                    ) {
                        Text(if (uiState.isWipingTerminal) "System wird gelöscht..." else "Ubuntu System zurücksetzen (Wipe)")
                    }

                    uiState.wipeMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
