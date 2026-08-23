/**
 * Modul: :feature:plugins
 * @author Thomas Schmid
 */
package com.codeforge.feature.plugins

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.TopAppBar
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
import kotlinx.coroutines.flow.collectLatest
import java.io.File

@Composable
fun PluginsRoute(
    modifier: Modifier = Modifier,
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
    onEvent: (PluginsUiEvent) -> Unit,
    onImportClicked: () -> Unit
) {
    Scaffold(
        modifier = modifier,
        snackBarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Plugins") },
                actions = {
                    IconButton(onClick = onImportClicked) {
                        Icon(Icons.Filled.Add, contentDescription = "Plugin installieren")
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
                        "Keine Plugins installiert. Tippe oben rechts, um ein Plugin-Archiv (.zip mit plugin.json) zu importieren.",
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
                        onEvent = onEvent
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginRow(plugin: InstalledPlugin, isLoaded: Boolean, onEvent: (PluginsUiEvent) -> Unit) {
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
                        "v${plugin.version} · ${if (isLoaded) "Geladen" else "Nicht geladen"}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = plugin.isEnabled,
                    onCheckedChange = { enabled -> onEvent(PluginsUiEvent.ToggleEnabled(plugin.id, enabled)) }
                )
            }

            if (plugin.description.isNotBlank()) {
                Text(
                    plugin.description,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onEvent(PluginsUiEvent.Uninstall(plugin.id)) }) {
                    Text("Entfernen")
                }
            }
        }
    }
}
