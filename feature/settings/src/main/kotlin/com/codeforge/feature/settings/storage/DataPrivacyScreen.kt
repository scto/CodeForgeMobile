/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 *
 * Daten & Datenschutz: Hinweis auf fehlende Telemetrie, Absturzberichte, Caches leeren, Protokolle,
 * Systeminformationen und Sicherung aller Projekte als ZIP.
 */
package com.codeforge.feature.settings.storage

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.codeforge.core.ui.SettingsDivider
import com.codeforge.core.ui.SettingsGroup
import com.codeforge.core.ui.SettingsScaffold
import com.codeforge.core.ui.SettingsSectionLabel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Route der Seite „Daten & Datenschutz“. */
@Composable
fun DataPrivacyRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenSystemInfo: () -> Unit = {},
    onOpenStorage: () -> Unit = {},
    viewModel: StorageViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.onEvent(StorageEvent.Backup(uri))
    }
    val backupName = remember { "codeforge-projects-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())}.zip" }

    SettingsScaffold(
        title = stringRes(R.string.data_privacy_title),
        onBack = onBack,
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            SettingsSectionLabel(stringRes(R.string.data_privacy_section_privacy))
            SettingsGroup {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(stringRes(R.string.data_privacy_no_tracking), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringRes(R.string.data_privacy_no_tracking_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                SettingsDivider()
                ActionRow(
                    title = stringRes(R.string.data_privacy_crash_reports),
                    summary = stringRes(R.string.data_privacy_crash_reports_desc, state.crashReports),
                    action = stringRes(R.string.common_loeschen),
                    enabled = state.crashReports > 0,
                    onClick = { viewModel.onEvent(StorageEvent.ClearCrashReports) }
                )
            }
            SettingsSectionLabel(stringRes(R.string.data_privacy_section_storage))
            SettingsGroup {
                ActionRow(
                    title = stringRes(R.string.data_privacy_clear_caches),
                    summary = stringRes(R.string.data_privacy_clear_caches_desc),
                    action = stringRes(R.string.storage_clear),
                    busy = state.busy == StorageUiState.BUSY_CACHES,
                    enabled = state.busy == null,
                    onClick = { viewModel.onEvent(StorageEvent.ClearCaches) }
                )
                SettingsDivider()
                ActionRow(
                    title = stringRes(R.string.storage_title),
                    summary = stringRes(R.string.settings_hub_storage_summary),
                    action = stringRes(R.string.data_privacy_open),
                    onClick = onOpenStorage
                )
                SettingsDivider()
                ActionRow(
                    title = stringRes(R.string.data_privacy_logs),
                    summary = stringRes(R.string.data_privacy_logs_desc),
                    action = stringRes(R.string.data_privacy_open),
                    onClick = onOpenLogs
                )
                SettingsDivider()
                ActionRow(
                    title = stringRes(R.string.data_privacy_system_info),
                    summary = stringRes(R.string.data_privacy_system_info_desc),
                    action = stringRes(R.string.data_privacy_open),
                    onClick = onOpenSystemInfo
                )
                SettingsDivider()
                ActionRow(
                    title = stringRes(R.string.data_privacy_backup),
                    summary = stringRes(R.string.data_privacy_backup_desc),
                    action = stringRes(R.string.data_privacy_backup_action),
                    busy = state.busy == StorageUiState.BUSY_BACKUP,
                    enabled = state.busy == null,
                    onClick = { backupLauncher.launch(backupName) }
                )
            }
        }
    }
}

@Composable
private fun ActionRow(
    title: String,
    summary: String,
    action: String,
    onClick: () -> Unit,
    busy: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        if (busy) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            FilledTonalButton(onClick = onClick, enabled = enabled) { Text(action) }
        }
    }
}
