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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.InstalledPlugin
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import kotlinx.coroutines.flow.collectLatest
import java.io.File

@Composable
fun PluginsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: PluginsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val cachedFile = copyToCacheFile(context, uri) ?: return@rememberLauncherForActivityResult
        viewModel.onEvent(PluginsUiEvent.InstallFromFile(cachedFile.path))
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is PluginsUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    PluginsScreen(
        modifier = modifier,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onEvent = viewModel::onEvent,
        onImportClicked = { filePickerLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
    )
}

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
private fun PluginsScreen(
    modifier: Modifier = Modifier,
    uiState: PluginsUiState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit = {},
    onEvent: (PluginsUiEvent) -> Unit,
    onImportClicked: () -> Unit
) {
    uiState.requirementDialog?.let { RequirementDialogView(it, onEvent) }
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            SettingsTopBar(
                title = stringRes(R.string.plugins_plugins),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onImportClicked) {
                        Icon(Icons.Filled.Add, contentDescription = stringRes(R.string.plugins_plugin_installieren))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            uiState.installProgressPercent?.let { percent ->
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (uiState.isLoading) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
                return@Scaffold
            }

            if (uiState.plugins.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringRes(R.string.plugins_keine_plugins_installiert_tippe_oben),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                return@Scaffold
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(uiState.plugins, key = { it.id }) { plugin ->
                    PluginRow(
                        plugin = plugin,
                        isLoaded = plugin.id in uiState.loadedPluginIds,
                        missing = uiState.missingRequirements[plugin.id].orEmpty(),
                        onEvent = onEvent
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginRow(
    plugin: InstalledPlugin,
    isLoaded: Boolean,
    missing: List<com.codeforge.core.domain.extension.RequirementContribution>,
    onEvent: (PluginsUiEvent) -> Unit
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
                            if (isLoaded) stringRes(R.string.plugins_geladen) else stringRes(R.string.plugins_nicht_geladen)
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
                    if (plugin.contributionKinds.isEmpty() || plugin.permissions.isNotEmpty()) R.string.plugins_kind_code
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
            if (plugin.description.isNotBlank()) {
                Text(
                    plugin.description,
                    style = MaterialTheme.typography.bodySmall,
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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (missing.isNotEmpty()) {
                    TextButton(onClick = { onEvent(PluginsUiEvent.RequirementsClicked(plugin.id)) }) {
                        Text(stringRes(R.string.plugins_voraussetzungen_installieren))
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
