// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import com.codeforge.core.ui.embeddedAwareInsets

/** Route der Erweiterungen. */
@Composable
fun ExtensionsRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    embedded: Boolean = false,
    installedOnly: Boolean = false,
    onOpenStore: (() -> Unit)? = null,
    viewModel: ExtensionsViewModel = hiltViewModel(),
    userToolsViewModel: com.codeforge.feature.settings.tools.UserToolsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val userTools by userToolsViewModel.uiState.collectAsState()
    ExtensionsScreen(
        userTools = userTools,
        onUserToolsEvent = userToolsViewModel::onEvent,
        modifier = modifier,
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        embedded = embedded,
        installedOnly = installedOnly,
        onOpenStore = onOpenStore
    )
}

/** Bildschirm der Erweiterungen: Liste, Details, Info und Installation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionsScreen(
    modifier: Modifier = Modifier,
    uiState: ExtensionsUiState,
    onEvent: (ExtensionsUiEvent) -> Unit,
    onBack: () -> Unit,
    embedded: Boolean = false,
    installedOnly: Boolean = false,
    onOpenStore: (() -> Unit)? = null,
    userTools: com.codeforge.feature.settings.tools.UserToolsUiState = com.codeforge.feature.settings.tools.UserToolsUiState(),
    onUserToolsEvent: (com.codeforge.feature.settings.tools.UserToolsEvent) -> Unit = {}
) {
    var addingLsp by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (addingLsp) com.codeforge.feature.settings.tools.ExternalLspDialog(
        onDismiss = { addingLsp = false },
        onSave = { addingLsp = false; onUserToolsEvent(com.codeforge.feature.settings.tools.UserToolsEvent.SaveLsp(it)) }
    )
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message.resolve())
            onEvent(ExtensionsUiEvent.DismissSnackbar)
        }
    }

    uiState.detailItem?.let { item ->
        ExtensionDetailDialog(
            item = item,
            lspMaxSessions = uiState.lspMaxSessions,
            lspIdleTimeoutMinutes = uiState.lspIdleTimeoutMinutes,
            onCheckUpdate = { onEvent(ExtensionsUiEvent.CheckUpdate(item.pkg)) },
            onDismiss = { onEvent(ExtensionsUiEvent.CloseDetails) }
        )
    }

    uiState.infoItem?.let { item ->
        com.codeforge.core.ui.LspInfoDialog(
            model = ExtensionInfoModel.build(item, LspServerInfoCatalog.of(item.pkg.id)),
            runtime = uiState.infoRuntime,
            onDismiss = { onEvent(ExtensionsUiEvent.CloseInfo) }
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = embeddedAwareInsets(embedded),
        topBar = {
            if (!embedded) {
                SettingsTopBar(
                    title = stringRes(if (installedOnly) R.string.lsp_manage_title else R.string.extensions_title),
                    onBack = onBack
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (installedOnly) androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = { addingLsp = true },
                icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Add, null) },
                text = { Text(stringRes(R.string.user_lsp_add)) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val shown = if (installedOnly) {
                uiState.extensions.filter { it.isInstalled || it.status != ExtensionStatus.NOT_INSTALLED }
            } else uiState.extensions
            items(shown, key = { it.pkg.id }) { item ->
                ExtensionCard(
                    item = item,
                    onInstall = { onEvent(ExtensionsUiEvent.InstallClicked(item.pkg)) },
                    onUninstall = { onEvent(ExtensionsUiEvent.UninstallClicked(item.pkg)) },
                    onCheckUpdate = { onEvent(ExtensionsUiEvent.CheckUpdate(item.pkg)) },
                    onDetails = { onEvent(ExtensionsUiEvent.OpenDetails(item.pkg)) },
                    onInfo = { onEvent(ExtensionsUiEvent.OpenInfo(item.pkg)) }
                )
            }
            if (installedOnly) {
                items(userTools.lspServers, key = { "user-" + it.id }) { server ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(server.label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringRes(R.string.user_lsp_summary, server.languages.joinToString(), server.command.joinToString(" ")),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            androidx.compose.material3.TextButton(onClick = {
                                onUserToolsEvent(com.codeforge.feature.settings.tools.UserToolsEvent.RemoveLsp(server.id))
                            }) { Text(stringRes(R.string.common_entfernen)) }
                        }
                    }
                }
            }
            if (installedOnly && onOpenStore != null) {
                item(key = "store-hint") {
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
            }
        }
    }
}

@Composable
private fun ExtensionCard(
    item: ExtensionUiItem,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDetails: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringRes(item.pkg.nameRes),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringRes(R.string.extensions_version_label, item.pkg.version),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (item.isInstalled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.isInstalled) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringRes(R.string.extensions_installed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = stringRes(R.string.extensions_not_installed),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = stringRes(R.string.extensions_language_label, item.pkg.language),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (item.pkg.isTermuxPackage) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = stringRes(R.string.extensions_termux_package),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringRes(item.pkg.descriptionRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (item.installedPath != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringRes(R.string.extensions_path_label, item.installedPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.missingRequirements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringRes(
                            R.string.extensions_missing_prerequisite,
                            item.missingRequirements.joinToString(", ")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (item.missingOptionalRequirements.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringRes(
                            R.string.extensions_optional_prerequisite_missing,
                            item.missingOptionalRequirements.joinToString(", ")
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val isBusy = item.status == ExtensionStatus.DOWNLOADING ||
                item.status == ExtensionStatus.VERIFYING ||
                item.status == ExtensionStatus.EXTRACTING ||
                item.status == ExtensionStatus.INSTALLING_DEPENDENCIES

            if (isBusy) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                val statusText = when (item.status) {
                    ExtensionStatus.DOWNLOADING -> stringRes(
                        R.string.extensions_downloading,
                        (item.progress * 100).toInt()
                    )
                    ExtensionStatus.VERIFYING -> stringRes(R.string.extensions_verifying)
                    ExtensionStatus.EXTRACTING -> stringRes(R.string.extensions_extracting)
                    ExtensionStatus.INSTALLING_DEPENDENCIES -> stringRes(R.string.extensions_installing_dependencies)
                    else -> ""
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            UpdateStatusLine(item.update)

            Spacer(modifier = Modifier.height(12.dp))

            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(onClick = onInfo, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringRes(R.string.lspinfo_button))
                }
                TextButton(onClick = onDetails) { Text(stringRes(R.string.ext_detail_button)) }
                TextButton(onClick = onCheckUpdate, enabled = item.update != UpdateUiState.Checking) {
                    Text(
                        if (item.update == UpdateUiState.Checking) stringRes(R.string.ext_checking_update)
                        else stringRes(R.string.ext_check_update)
                    )
                }
                if (item.pkg.isTermuxPackage) {
                    if (!item.isInstalled) {
                        val termuxSource = item.pkg.source as ExtensionSource.TermuxPackage
                        Text(
                            text = stringRes(R.string.extensions_termux_install_hint, termuxSource.packageName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else if (item.isInstalled) {
                    OutlinedButton(
                        onClick = onUninstall,
                        enabled = !isBusy
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringRes(R.string.extensions_uninstall))
                    }
                } else {
                    Button(
                        onClick = onInstall,
                        enabled = !isBusy
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringRes(R.string.extensions_install))
                    }
                }
            }
        }
    }
}

/** Zeigt das Ergebnis der Update-Prüfung als eine Zeile; leer, solange nichts geprüft wurde. */
@Composable
internal fun UpdateStatusLine(update: UpdateUiState, modifier: Modifier = Modifier) {
    val done = (update as? UpdateUiState.Done)?.result ?: return
    val (text, color) = when (done) {
        is UpdateCheckResult.UpToDate ->
            stringRes(R.string.ext_update_uptodate, done.current) to MaterialTheme.colorScheme.onSurfaceVariant
        is UpdateCheckResult.UpdateAvailable ->
            stringRes(R.string.ext_update_available, done.latest, done.current) to MaterialTheme.colorScheme.primary
        is UpdateCheckResult.Failed ->
            stringRes(R.string.ext_update_failed, done.reason) to MaterialTheme.colorScheme.error
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = FontWeight.Medium,
        modifier = modifier.padding(top = 8.dp)
    )
}
