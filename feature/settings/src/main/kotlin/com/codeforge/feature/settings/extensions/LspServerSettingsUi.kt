// Modul: :feature:settings
package com.codeforge.feature.settings.extensions

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatIndentIncrease
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.LspFeature
import com.codeforge.core.domain.model.LspServerSettings
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

private data class FeatureRow(val feature: LspFeature, @StringRes val title: Int, @StringRes val summary: Int, val icon: ImageVector)

private val FEATURE_ROWS = listOf(
    FeatureRow(LspFeature.COMPLETION, R.string.lspfeat_completion, R.string.lspfeat_completion_sum, Icons.Filled.Code),
    FeatureRow(LspFeature.DIAGNOSTICS, R.string.lspfeat_diagnostics, R.string.lspfeat_diagnostics_sum, Icons.Filled.BugReport),
    FeatureRow(LspFeature.HOVER, R.string.lspfeat_hover, R.string.lspfeat_hover_sum, Icons.Filled.Info),
    FeatureRow(LspFeature.SIGNATURE_HELP, R.string.lspfeat_signature, R.string.lspfeat_signature_sum, Icons.Filled.Functions),
    FeatureRow(LspFeature.CODE_ACTIONS, R.string.lspfeat_actions, R.string.lspfeat_actions_sum, Icons.Filled.Lightbulb),
    FeatureRow(LspFeature.FORMATTING, R.string.lspfeat_formatting, R.string.lspfeat_formatting_sum, Icons.AutoMirrored.Filled.FormatIndentIncrease),
    FeatureRow(LspFeature.INLAY_HINTS, R.string.lspfeat_inlay, R.string.lspfeat_inlay_sum, Icons.Filled.Visibility),
)

/** Zeile eines Language-Servers: Name, Plugin bzw. fehlende Programme, Einstellungen und Ein/Aus-Schalter. */
@Composable
internal fun LanguageServerRow(
    item: LanguageServerItem,
    settings: LspServerSettings,
    shape: RoundedCornerShape,
    installing: InstallProgressUi?,
    onOpenSettings: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSettings)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(24.dp))
                Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    Text(item.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ServerSubtitle(item)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Tune, contentDescription = stringRes(R.string.lspfeat_open_settings))
                }
                Switch(checked = settings.enabled, onCheckedChange = onEnabledChange)
            }
            RequirementsLine(item, installing, onInstall)
        }
    }
}

@Composable
private fun ServerSubtitle(item: LanguageServerItem) {
    val missing = item.missing.isNotEmpty()
    val text = if (missing) {
        stringRes(R.string.lsp_missing_programs, item.missing.joinToString(", "))
    } else {
        item.languages.joinToString(", ")
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (missing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RequirementsLine(item: LanguageServerItem, installing: InstallProgressUi?, onInstall: () -> Unit) {
    when {
        installing != null -> Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            val percent = installing.percent
            if (percent != null) {
                LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                installing.lastLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.StartEllipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        item.missing.isNotEmpty() -> FilledTonalButton(
            onClick = onInstall,
            modifier = Modifier.padding(start = 54.dp, bottom = 12.dp),
        ) { Text(stringRes(R.string.lsp_install_requirements)) }
    }
}

/** Bottom-Sheet mit den Fähigkeiten eines Servers, den JVM-Optionen und der Info. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LspFeatureSheet(
    item: LanguageServerItem,
    settings: LspServerSettings,
    onFeatureChange: (LspFeature, Boolean) -> Unit,
    onOpenJvm: () -> Unit,
    onInfo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp),
        ) {
            SheetHeader(item)
            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
            FeatureRows(settings, onFeatureChange)
            if (!settings.enabled) {
                Text(
                    stringRes(R.string.lspfeat_server_off),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (item.acceptsJvmOptions) JvmRow(onOpenJvm)
            TextButton(onClick = onInfo, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) {
                Text(stringRes(R.string.lspinfo_button))
            }
        }
    }
}

@Composable
private fun SheetHeader(item: LanguageServerItem) {
    Row(modifier = Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.padding(8.dp).size(20.dp))
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(stringRes(R.string.lspfeat_sheet_title, item.label), style = MaterialTheme.typography.titleLarge)
            Text(
                stringRes(R.string.lsp_from_plugin, listOf(item.pluginName, item.pluginVersion).filter { it.isNotBlank() }.joinToString(" ")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FeatureRows(settings: LspServerSettings, onFeatureChange: (LspFeature, Boolean) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FEATURE_ROWS.forEachIndexed { index, row ->
            val checked = row.feature !in settings.disabledFeatures
            Surface(shape = groupShape(index, FEATURE_ROWS.size), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = settings.enabled) { onFeatureChange(row.feature, !checked) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(row.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(stringRes(row.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringRes(row.summary), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = checked, enabled = settings.enabled, onCheckedChange = { onFeatureChange(row.feature, it) })
                }
            }
        }
    }
}

@Composable
private fun JvmRow(onOpenJvm: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenJvm).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(22.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(stringRes(R.string.jvm_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringRes(R.string.jvm_applied_next_start), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

/** Ecken einer Gruppenzeile: außen stark, innen schwach gerundet (zusammenhängender Block). */
internal fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) 20.dp else 4.dp
    val bottom = if (index == count - 1) 20.dp else 4.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}
