/**
 * Modul: :feature:plugins
 * @author Thomas Schmid
 */
package com.codeforge.feature.plugins

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.InstalledPlugin
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import kotlinx.coroutines.flow.collectLatest
import java.io.File

/**
 * SAF liefert nur eine content://-Uri, aber PluginRepository.installFromFile erwartet
 * einen realen Dateisystempfad — daher Kopie in den App-Cache vor der Installation.
 */
private fun copyToCacheFile(context: android.content.Context, uri: Uri): File? = runCatching {
    val targetFile = File(context.cacheDir, "plugin_import_${System.currentTimeMillis()}.zip")
    context.contentResolver.openInputStream(uri)?.use { input ->
        targetFile.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    targetFile
}.getOrNull()

@Composable
private fun PluginRow(
    plugin: InstalledPlugin,
    isLoaded: Boolean,
    missing: List<com.codeforge.core.domain.extension.RequirementContribution>,
    issues: List<com.codeforge.core.domain.extension.PluginIssue>,
    onEvent: (PluginsUiEvent) -> Unit,
    hasSettings: Boolean = false
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(plugin.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringRes(
                            R.string.plugins_version_status,
                            plugin.version,
                            when {
                                // Deklarative Plugins haben keinen Code zu laden: Anzeige = aktiviert oder nicht.
                                plugin.isDeclarativeOnly -> stringRes(if (plugin.isEnabled) R.string.plugins_aktiv else R.string.plugins_deaktiviert)
                                isLoaded -> stringRes(R.string.plugins_geladen)
                                else -> stringRes(R.string.plugins_nicht_geladen)
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = plugin.isEnabled,
                    onCheckedChange = { enabled -> onEvent(PluginsUiEvent.ToggleEnabled(plugin.id, enabled)) }
                )
            }

            if (plugin.contributionKinds.isNotEmpty() || plugin.permissions.isNotEmpty() || plugin.packMembers.isNotEmpty()) {
                val none = stringRes(R.string.plugins_none)
                val kind = stringRes(
                    if (!plugin.isDeclarativeOnly) R.string.plugins_kind_code
                    else R.string.plugins_kind_declarative
                )
                val kinds = (plugin.contributionKinds + plugin.packMembers.map { "→ $it" })
                Text(
                    stringRes(
                        R.string.plugins_contributions_line,
                        kinds.joinToString().ifEmpty { none },
                        plugin.permissions.sorted().joinToString().ifEmpty { none },
                        kind,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // Herkunft: Signatur (TOFU) und Build-Angaben aus build-info.json.
            if (!plugin.isDeclarativeOnly || plugin.signerFingerprint.isNotBlank() || plugin.buildInfo.isNotBlank()) {
                val signer = if (plugin.signerFingerprint.isBlank()) stringRes(R.string.plugins_unsigned)
                else stringRes(R.string.plugins_signed, com.codeforge.core.domain.extension.PluginSignatures.shortFingerprint(plugin.signerFingerprint))
                Text(
                    listOf(signer, plugin.buildInfo).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (plugin.activationEvents.isNotEmpty()) {
                Text(
                    stringRes(R.string.plugins_activation, plugin.activationEvents.joinToString()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (plugin.description.isNotBlank()) {
                Text(
                    plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            issues.forEach { issue ->
                Text(
                    when (issue) {
                        is com.codeforge.core.domain.extension.PluginIssue.HostTooOld ->
                            stringRes(R.string.plugins_issue_host_too_old, issue.required, issue.actual)
                        is com.codeforge.core.domain.extension.PluginIssue.DependencyMissing ->
                            stringRes(R.string.plugins_issue_dependency_missing, issue.pluginId)
                        is com.codeforge.core.domain.extension.PluginIssue.DependencyDisabled ->
                            stringRes(R.string.plugins_issue_dependency_disabled, issue.pluginId)
                        is com.codeforge.core.domain.extension.PluginIssue.DependencyUnavailable ->
                            stringRes(R.string.plugins_issue_dependency_unavailable, issue.pluginId)
                        com.codeforge.core.domain.extension.PluginIssue.DependencyCycle ->
                            stringRes(R.string.plugins_issue_dependency_cycle)
                        is com.codeforge.core.domain.extension.PluginIssue.UnsupportedApi ->
                            stringRes(R.string.plugins_api1_unsupported, com.codeforge.core.domain.extension.PluginApiVersion.API1_REMOVED_IN)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (missing.isNotEmpty()) {
                Text(
                    stringRes(R.string.plugins_fehlt, missing.joinToString { it.name }),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                if (PluginLspInfo.isLspPlugin(plugin)) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { onEvent(PluginsUiEvent.InfoClicked(plugin.id)) },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringRes(R.string.lspinfo_button))
                    }
                }
                if (missing.isNotEmpty()) {
                    TextButton(onClick = { onEvent(PluginsUiEvent.RequirementsClicked(plugin.id)) }) {
                        Text(stringRes(R.string.plugins_voraussetzungen_installieren))
                    }
                }
                if (hasSettings) {
                    TextButton(onClick = { onEvent(PluginsUiEvent.SettingsClicked(plugin.id)) }) {
                        Text(stringRes(R.string.plugins_settings))
                    }
                }
                if (!plugin.isDeclarativeOnly) {
                    TextButton(onClick = { onEvent(PluginsUiEvent.LogClicked(plugin.id)) }) {
                        Text(stringRes(R.string.plugins_log))
                    }
                }
                TextButton(onClick = { onEvent(PluginsUiEvent.Uninstall(plugin.id)) }) {
                    Text(stringRes(R.string.common_entfernen))
                }
            }
        }
    }
}


@Composable
private fun RequirementDialogView(dialog: RequirementDialog, onEvent: (PluginsUiEvent) -> Unit) {
    val dismiss = { onEvent(PluginsUiEvent.RequirementsDismissed) }
    when (dialog) {
        is RequirementDialog.Confirm -> androidx.compose.material3.AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringRes(R.string.plugins_voraussetzungen_titel)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringRes(R.string.plugins_voraussetzungen_text))
                    dialog.missing.forEach {
                        Text("• ${it.manager.id}: ${it.name}", style = MaterialTheme.typography.bodyMedium,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { onEvent(PluginsUiEvent.RequirementsConfirmed) }) { Text(stringRes(R.string.plugins_installieren)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringRes(R.string.common_abbrechen)) } }
        )

        is RequirementDialog.Running -> androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text(stringRes(R.string.plugins_voraussetzungen_titel)) },
            text = {
                Column {
                    Text(
                        if (!dialog.done) stringRes(R.string.plugins_installiere) else stringRes(R.string.plugins_beendet, dialog.exitCode ?: -1),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (dialog.done && dialog.exitCode != 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val scroll = androidx.compose.foundation.rememberScrollState()
                    androidx.compose.runtime.LaunchedEffect(dialog.lines.size) { scroll.animateScrollTo(scroll.maxValue) }
                    Text(
                        dialog.lines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(scroll)
                    )
                }
            },
            confirmButton = { TextButton(onClick = dismiss) { Text(stringRes(if (dialog.done) R.string.common_schliessen else R.string.common_abbrechen)) } }
        )
    }
}


/**
 * Verwaltung der installierten Plugins ohne eigenes Gerüst (Aktivieren, Hinweise, Voraussetzungen, Info, Entfernen,
 * Sideload aus Datei). Wird im Store hinter dem Filter „Installiert“ eingebettet.
 */
@Composable
fun InstalledPluginsPanel(
    modifier: Modifier = Modifier,
    onMessage: (String) -> Unit = {},
    viewModel: PluginsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val cached = copyToCacheFile(context, uri) ?: return@rememberLauncherForActivityResult
        viewModel.onEvent(PluginsUiEvent.InstallFromFile(cached.path))
    }
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            if (effect is PluginsUiEffect.ShowSnackbar) onMessage(effect.message)
        }
    }
    uiState.requirementDialog?.let { RequirementDialogView(it, viewModel::onEvent) }
    uiState.logDialog?.let { PluginLogDialogView(it, viewModel::onEvent) }
    uiState.settingsSheet?.let { PluginSettingsSheetView(it, viewModel::onEvent) }
    uiState.infoDialog?.let { info ->
        com.codeforge.core.ui.LspInfoDialog(
            model = info.model,
            runtime = info.runtime,
            onDismiss = { viewModel.onEvent(PluginsUiEvent.InfoDismissed) },
        )
    }
    Column(modifier = modifier.fillMaxSize()) {
        uiState.installProgressPercent?.let { percent ->
            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
        ) {
            item(key = "sideload") {
                androidx.compose.material3.OutlinedButton(
                    onClick = { picker.launch(arrayOf("application/zip", "application/octet-stream")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringRes(R.string.plugins_plugin_installieren))
                }
            }
            if (uiState.isLoading) {
                item(key = "loading") { CircularProgressIndicator(modifier = Modifier.padding(24.dp)) }
            } else if (uiState.plugins.isEmpty()) {
                item(key = "empty") {
                    Text(
                        stringRes(R.string.plugins_keine_plugins_installiert_tippe_oben),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            } else {
                items(uiState.plugins, key = { it.id }) { plugin ->
                    PluginRow(
                        plugin = plugin,
                        isLoaded = plugin.id in uiState.loadedPluginIds,
                        missing = uiState.missingRequirements[plugin.id].orEmpty(),
                        hasSettings = plugin.id in uiState.pluginsWithSettings,
                        issues = uiState.issues[plugin.id].orEmpty(),
                        onEvent = viewModel::onEvent,
                    )
                }
            }
        }
    }
}
