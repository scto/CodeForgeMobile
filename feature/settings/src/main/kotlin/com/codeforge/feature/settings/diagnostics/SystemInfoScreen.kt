// Modul: :feature:settings
package com.codeforge.feature.settings.diagnostics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import kotlinx.coroutines.launch

/** Route der System-Diagnose. */
@Composable
fun SystemInfoRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: SystemInfoViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    SystemInfoScreen(uiState = uiState, onRefresh = viewModel::refresh, onBack = onBack, modifier = modifier)
}

/** System-Diagnose: Gerät, Anzeige, Laufzeit, Speicherplatz, App und Bibliotheken; „Kopieren“ für Fehlerberichte. */
@Composable
internal fun SystemInfoScreen(
    uiState: SystemInfoUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copied = stringRes(R.string.sysinfo_copied)
    Scaffold(
        modifier = modifier,
        topBar = {
            SettingsTopBar(
                title = stringRes(R.string.sysinfo_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, stringRes(R.string.sysinfo_refresh)) }
                    IconButton(
                        onClick = {
                            val report = InfoFormat.report(uiState.sections, { context.getString(it.titleRes()) }, context::getString)
                            clipboard.setText(AnnotatedString(report))
                            scope.launch { snackbar.showSnackbar(copied) }
                        },
                        enabled = uiState.sections.isNotEmpty(),
                    ) { Icon(Icons.Filled.ContentCopy, stringRes(R.string.sysinfo_copy)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (uiState.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            uiState.sections.forEach { SectionCard(it) }
        }
    }
}

@Composable
private fun SectionCard(section: InfoSection) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        section.kind.icon(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                Text(stringRes(section.kind.titleRes()), style = MaterialTheme.typography.titleMedium)
            }
            if (section.tiles) TileGrid(section.items) else section.items.forEach { Line(it) }
        }
    }
}

// Zwei Spalten; lange Werte (z. B. ABIs) belegen die ganze Breite.
@Composable
private fun TileGrid(items: List<InfoItem>) {
    val (long, short) = items.partition { it.value.length > LONG_VALUE }
    short.chunked(2).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { Tile(it, Modifier.weight(1f)) }
            if (row.size == 1) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        }
    }
    long.forEach { Tile(it, Modifier.fillMaxWidth()) }
}

@Composable
private fun Tile(item: InfoItem, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(stringRes(item.label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(item.value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun Line(item: InfoItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringRes(item.label), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(item.value, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

private fun InfoSectionKind.titleRes(): Int = when (this) {
    InfoSectionKind.DEVICE -> R.string.sysinfo_section_device
    InfoSectionKind.DISPLAY -> R.string.sysinfo_section_display
    InfoSectionKind.RUNTIME -> R.string.sysinfo_section_runtime
    InfoSectionKind.STORAGE -> R.string.sysinfo_section_storage
    InfoSectionKind.APP -> R.string.sysinfo_section_app
}

private fun InfoSectionKind.icon(): ImageVector = when (this) {
    InfoSectionKind.DEVICE -> Icons.Outlined.Info
    InfoSectionKind.DISPLAY -> Icons.Outlined.PhoneAndroid
    InfoSectionKind.RUNTIME -> Icons.Outlined.Memory
    InfoSectionKind.STORAGE -> Icons.Outlined.Storage
    InfoSectionKind.APP -> Icons.Outlined.Code
}

private const val LONG_VALUE = 18
