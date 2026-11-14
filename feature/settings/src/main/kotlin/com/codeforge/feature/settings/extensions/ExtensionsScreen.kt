// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.JvmOptions
import com.codeforge.core.domain.model.LspServerDefaults
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.LspInfoDialog
import com.codeforge.core.ui.SettingsTopBar
import com.codeforge.core.ui.embeddedAwareInsets
import com.codeforge.feature.settings.common.JvmOptionsSheet
import com.codeforge.feature.settings.tools.ExternalLspDialog
import com.codeforge.feature.settings.tools.UserToolsEvent
import com.codeforge.feature.settings.tools.UserToolsUiState
import com.codeforge.feature.settings.tools.UserToolsViewModel

/** Route der Sprachserver-Verwaltung. */
@Composable
fun ExtensionsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    onOpenStore: (() -> Unit)? = null,
    viewModel: ExtensionsViewModel = hiltViewModel(),
    userToolsViewModel: UserToolsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userTools by userToolsViewModel.uiState.collectAsState()
    ExtensionsScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        embedded = embedded,
        onOpenStore = onOpenStore,
        userTools = userTools,
        onUserToolsEvent = userToolsViewModel::onEvent
    )
}

/**
 * Sprachserver-Verwaltung: Server aus Plugins mit Schalter, Fähigkeiten, JVM-Optionen und Voraussetzungen,
 * eigene externe Server und der Weg in den Store.
 */
@Composable
fun ExtensionsScreen(
    uiState: ExtensionsUiState,
    onEvent: (ExtensionsUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    onOpenStore: (() -> Unit)? = null,
    userTools: UserToolsUiState = UserToolsUiState(),
    onUserToolsEvent: (UserToolsEvent) -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var addingLsp by remember { mutableStateOf(false) }
    var settingsFor by rememberSaveable { mutableStateOf<String?>(null) }
    var jvmFor by rememberSaveable { mutableStateOf<String?>(null) }

    if (addingLsp) {
        ExternalLspDialog(
            onDismiss = { addingLsp = false },
            onSave = {
                addingLsp = false
                onUserToolsEvent(UserToolsEvent.SaveLsp(it))
            }
        )
    }
    ServerDialogs(
        uiState = uiState,
        settingsFor = settingsFor,
        jvmFor = jvmFor,
        onSettingsFor = { settingsFor = it },
        onJvmFor = { jvmFor = it },
        onEvent = onEvent
    )
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message.resolve())
            onEvent(ExtensionsUiEvent.DismissSnackbar)
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = embeddedAwareInsets(embedded),
        topBar = { if (!embedded) SettingsTopBar(title = stringRes(R.string.lsp_manage_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addingLsp = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text(stringRes(R.string.user_lsp_add)) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.legacyBytes > 0) item(key = "legacy") { LegacyCard(uiState.legacyBytes, onEvent) }
            serverRows(uiState, onEvent, onOpenSettings = { settingsFor = it })
            userServers(userTools, onUserToolsEvent)
            if (onOpenStore != null) item(key = "store-hint") { StoreHintCard(onOpenStore) }
        }
    }
}

@Composable
private fun ServerDialogs(
    uiState: ExtensionsUiState,
    settingsFor: String?,
    jvmFor: String?,
    onSettingsFor: (String?) -> Unit,
    onJvmFor: (String?) -> Unit,
    onEvent: (ExtensionsUiEvent) -> Unit
) {
    uiState.servers.firstOrNull { it.key == settingsFor }?.let { item ->
        LspFeatureSheet(
            item = item,
            settings = uiState.settingsOf(item.key),
            onFeatureChange = { feature, enabled -> onEvent(ExtensionsUiEvent.SetFeature(item.key, feature, enabled)) },
            onOpenJvm = {
                onSettingsFor(null)
                onJvmFor(item.key)
            },
            onInfo = {
                onSettingsFor(null)
                onEvent(ExtensionsUiEvent.OpenInfo(item.key))
            },
            onDismiss = { onSettingsFor(null) }
        )
    }
    uiState.servers.firstOrNull { it.key == jvmFor }?.let { item ->
        JvmOptionsSheet(
            title = stringRes(R.string.jvm_title),
            subtitle = stringRes(R.string.jvm_applied_next_start),
            initial = uiState.settingsOf(item.key).jvm ?: LspServerDefaults.jvmFor(item.key) ?: JvmOptions(),
            onSave = {
                onJvmFor(null)
                onEvent(ExtensionsUiEvent.SaveJvm(item.key, it))
            },
            onReset = {
                onJvmFor(null)
                onEvent(ExtensionsUiEvent.SaveJvm(item.key, null))
            },
            onDismiss = { onJvmFor(null) }
        )
    }
    uiState.infoModel?.let { model ->
        LspInfoDialog(model = model, runtime = uiState.infoRuntime, onDismiss = { onEvent(ExtensionsUiEvent.CloseInfo) })
    }
}

private fun LazyListScope.serverRows(
    uiState: ExtensionsUiState,
    onEvent: (ExtensionsUiEvent) -> Unit,
    onOpenSettings: (String) -> Unit
) {
    if (uiState.servers.isEmpty()) {
        item(key = "ls-empty") {
            Text(
                stringRes(R.string.lsp_none_installed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        return
    }
    item(key = "ls-header") {
        Text(
            stringRes(R.string.lspfeat_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
        )
    }
    item(key = "ls-rows") {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            uiState.servers.forEachIndexed { index, item ->
                LanguageServerRow(
                    item = item,
                    settings = uiState.settingsOf(item.key),
                    shape = groupShape(index, uiState.servers.size),
                    installing = uiState.installing[item.pluginId],
                    onOpenSettings = { onOpenSettings(item.key) },
                    onEnabledChange = { onEvent(ExtensionsUiEvent.SetServerEnabled(item.key, it)) },
                    onInstall = { onEvent(ExtensionsUiEvent.InstallRequirements(item.pluginId)) }
                )
            }
        }
    }
}

private fun LazyListScope.userServers(userTools: UserToolsUiState, onUserToolsEvent: (UserToolsEvent) -> Unit) {
    items(userTools.lspServers, key = { "user-" + it.id }) { server ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(server.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringRes(R.string.user_lsp_summary, server.languages.joinToString(), server.command.joinToString(" ")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onUserToolsEvent(UserToolsEvent.RemoveLsp(server.id)) }) {
                    Text(stringRes(R.string.common_entfernen))
                }
            }
        }
    }
}

@Composable
private fun LegacyCard(bytes: Long, onEvent: (ExtensionsUiEvent) -> Unit) {
    val size = Formatter.formatShortFileSize(LocalContext.current, bytes)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringRes(R.string.lsp_legacy_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringRes(R.string.lsp_legacy_text, size), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onEvent(ExtensionsUiEvent.DeleteLegacy) }) { Text(stringRes(R.string.lsp_legacy_delete)) }
        }
    }
}

@Composable
private fun StoreHintCard(onOpenStore: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onOpenStore) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringRes(R.string.lsp_manage_store_hint), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                stringRes(R.string.lsp_manage_open_store),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
