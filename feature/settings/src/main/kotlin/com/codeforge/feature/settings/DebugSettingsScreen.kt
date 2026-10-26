/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar

@Composable
fun DebugSettingsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: DebugSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    DebugSettingsScreen(modifier = modifier, uiState = uiState, onBack = onBack, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DebugSettingsScreen(
    uiState: DebugSettingsUiState,
    onEvent: (DebugSettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val message = uiState.lastMessage?.let { msg ->
        when (msg) {
            is DebugMessage.LogsCleared -> stringRes(R.string.debug_logs_geloescht, msg.count)
        }
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onEvent(DebugSettingsUiEvent.MessageShown)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { SettingsTopBar(title = stringRes(R.string.debug_settings_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringRes(R.string.debug_log_enabled), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringRes(R.string.debug_log_enabled_beschreibung),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.logEnabled,
                        onCheckedChange = { onEvent(DebugSettingsUiEvent.LogEnabledChanged(it)) }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringRes(R.string.debug_log_verzeichnis), style = MaterialTheme.typography.titleSmall)
                    Text(
                        uiState.logDirectory.ifBlank { stringRes(R.string.debug_log_verzeichnis_nicht_verfuegbar) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringRes(R.string.debug_log_dateien, uiState.logFileCount, formatSize(uiState.logSizeBytes)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { onEvent(DebugSettingsUiEvent.ClearLogs) },
                        enabled = uiState.logFileCount > 0
                    ) {
                        Text(stringRes(R.string.debug_logs_loeschen))
                    }
                }
            }

            Text(
                stringRes(R.string.debug_crash_hinweis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.1f KiB".format(bytes / 1024.0)
    else -> "%.1f MiB".format(bytes / (1024.0 * 1024.0))
}
