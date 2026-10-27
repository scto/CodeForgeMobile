// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.TooltipIconButton

/**
 * Detailansicht eines Sprachservers: Kopf (Icon, Kurzbeschreibung, Autor, Homepage/GitHub),
 * darunter Tabs „Beschreibung“, „Einstellungen“ und „Info“.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionDetailDialog(
    item: ExtensionUiItem,
    lspMaxSessions: Int,
    lspIdleTimeoutMinutes: Int,
    onCheckUpdate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val info = LspServerInfoCatalog.of(item.pkg.id)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val uriHandler = LocalUriHandler.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringRes(item.pkg.nameRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    actions = {
                        TooltipIconButton(onClick = onDismiss, label = stringRes(R.string.ext_detail_close)) {
                            Icon(Icons.Filled.Close, contentDescription = stringRes(R.string.ext_detail_close))
                        }
                    }
                )
            }
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Kopf
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Filled.Extension,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringRes(item.pkg.descriptionRes), style = MaterialTheme.typography.bodyMedium)
                        if (info != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringRes(R.string.ext_detail_author, info.author), style = MaterialTheme.typography.bodySmall)
                            LinkLine(stringRes(R.string.ext_detail_homepage, info.homepageUrl.removePrefix("https://"))) {
                                runCatching { uriHandler.openUri(info.homepageUrl) }
                            }
                            LinkLine(stringRes(R.string.ext_detail_repo, info.repoSlug)) {
                                runCatching { uriHandler.openUri(info.repoUrl) }
                            }
                        }
                        UpdateStatusLine(item.update)
                        if (info != null && (item.update as? UpdateUiState.Done)?.result is UpdateCheckResult.UpdateAvailable) {
                            val page = UpdateParsers.releasePageUrl(info.update, info.repoUrl)
                            if (page != null) {
                                OutlinedButton(onClick = { runCatching { uriHandler.openUri(page) } }) {
                                    Text(stringRes(R.string.ext_update_open_release))
                                }
                            }
                        }
                        if (item.update !is UpdateUiState.Checking) {
                            TextButton(onClick = onCheckUpdate) { Text(stringRes(R.string.ext_check_update)) }
                        }
                    }
                }
                TabRow(selectedTabIndex = tab) {
                    listOf(
                        R.string.ext_detail_tab_description,
                        R.string.ext_detail_tab_settings,
                        R.string.ext_detail_tab_about
                    ).forEachIndexed { index, label ->
                        Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringRes(label), maxLines = 1) })
                    }
                }
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    when (tab) {
                        0 -> DescriptionTab(info)
                        1 -> SettingsTab(item, lspMaxSessions, lspIdleTimeoutMinutes)
                        else -> AboutTab(item, info)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkLine(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().clickableNoRipple(onClick)
    )
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick).padding(vertical = 2.dp)

@Composable
private fun DescriptionTab(info: LspServerInfo?) {
    if (info == null) return
    Text(stringRes(info.longDescriptionRes), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringRes(R.string.ext_detail_capabilities), style = MaterialTheme.typography.titleSmall)
    Text(stringRes(info.capabilitiesRes), style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(16.dp))
    Text(stringRes(R.string.ext_detail_limitations), style = MaterialTheme.typography.titleSmall)
    Text(stringRes(info.limitationsRes), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun SettingsTab(item: ExtensionUiItem, maxSessions: Int, idleMinutes: Int) {
    Text(
        stringRes(R.string.ext_detail_settings_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(12.dp))
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringRes(R.string.ext_detail_setting_max_sessions, maxSessions), style = MaterialTheme.typography.bodyMedium)
        Text(stringRes(R.string.ext_detail_setting_idle, idleMinutes), style = MaterialTheme.typography.bodyMedium)
        Text(
            stringRes(R.string.ext_detail_setting_launch, item.pkg.launchCommand.joinToString(" ")),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
        Text(
            stringRes(
                R.string.ext_detail_setting_path,
                item.installedPath ?: stringRes(R.string.ext_detail_setting_not_installed)
            ),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun AboutTab(item: ExtensionUiItem, info: LspServerInfo?) {
    val pkg = item.pkg
    val source = when (val s = pkg.source) {
        is ExtensionSource.Download -> s.url
        is ExtensionSource.TermuxPackage -> stringRes(R.string.ext_detail_about_termux, s.packageName)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringRes(R.string.ext_detail_about_version, pkg.version), style = MaterialTheme.typography.bodyMedium)
        Text(stringRes(R.string.ext_detail_about_language, pkg.language), style = MaterialTheme.typography.bodyMedium)
        if (info != null) Text(stringRes(R.string.ext_detail_about_license, info.license), style = MaterialTheme.typography.bodyMedium)
        Text(stringRes(R.string.ext_detail_about_source, source), style = MaterialTheme.typography.bodySmall)
        pkg.expectedSha256?.let {
            Text(stringRes(R.string.ext_detail_about_sha, it), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        Text(
            stringRes(
                R.string.ext_detail_about_requires,
                pkg.requires.joinToString(", ").ifEmpty { stringRes(R.string.ext_detail_about_none) }
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringRes(R.string.ext_update_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
