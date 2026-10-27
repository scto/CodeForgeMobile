package com.codeforge.feature.x2c.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.codeforge.core.resources.stringRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.LayoutClassification
import com.codeforge.core.domain.model.LayoutPlanEntry
import com.codeforge.core.resources.R
import com.codeforge.feature.x2c.X2cIntent
import com.codeforge.feature.x2c.X2cUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun X2cDialog(
    state: X2cUiState,
    onIntent: (X2cIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.showDialog) return

    val plan = state.plan
    val scan = state.scanResult
    val totalLayouts = plan?.totalLayouts ?: scan?.layoutCount ?: 0
    val moduleCount = scan?.moduleCount ?: 1
    val autoCount = plan?.automaticCount ?: 0
    val notesCount = plan?.withNotesCount ?: 0
    val notConvCount = plan?.notConvertibleCount ?: 0
    val stage2Count = plan?.stage2CandidatesCount ?: 0
    val hasCatalog = plan?.hasVersionCatalog ?: false

    AlertDialog(
        onDismissRequest = { onIntent(X2cIntent.DismissDialog(dontAskAgain = false)) },
        // Plattform-Standardbreite (~280 dp) ist für die Layout-Liste und drei Buttons zu schmal.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        icon = {
            Icon(
                imageVector = Icons.Default.Transform,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringRes(R.string.x2c_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Zusammenfassung
                Text(
                    text = stringRes(
                        R.string.x2c_summary,
                        totalLayouts,
                        moduleCount,
                        autoCount,
                        notesCount,
                        notConvCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                // Backup-Hinweis
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringRes(R.string.x2c_backup_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                HorizontalDivider()

                // Suchleiste für Layouts
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { onIntent(X2cIntent.SetSearchQuery(it)) },
                    placeholder = { Text(stringRes(R.string.x2c_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.searchQuery.isNotBlank()) {
                            IconButton(onClick = { onIntent(X2cIntent.SetSearchQuery("")) }) {
                                Icon(Icons.Default.Clear, contentDescription = null)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Auswahlschaltflächen
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = stringRes(
                            R.string.x2c_selected_count,
                            state.selectedLayouts.size,
                            totalLayouts
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onIntent(X2cIntent.SelectAllLayouts) }) {
                            Text(stringRes(R.string.x2c_select_all), style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick = { onIntent(X2cIntent.DeselectAllLayouts) }) {
                            Text(stringRes(R.string.x2c_deselect_all), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Layout-Liste
                val displayLayouts = state.filteredLayouts
                if (displayLayouts.isEmpty()) {
                    Text(
                        text = stringRes(R.string.x2c_no_layouts_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(displayLayouts, key = { it.relativePath }) { entry ->
                                LayoutItemRow(
                                    entry = entry,
                                    isSelected = state.selectedLayouts.contains(entry.relativePath),
                                    onToggle = { onIntent(X2cIntent.ToggleLayoutSelection(entry.relativePath)) },
                                    onOpenFile = { onIntent(X2cIntent.OpenFile(entry.relativePath)) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Optionen / Schalter
                Text(
                    text = "Optionen",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )

                // Schalter 1: Activities / Fragments umstellen
                X2cSwitchRow(
                    title = stringRes(R.string.x2c_option_wire_activities),
                    description = stringRes(R.string.x2c_option_wire_activities_desc, stage2Count),
                    checked = state.wireActivities,
                    enabled = stage2Count > 0,
                    onCheckedChange = { onIntent(X2cIntent.SetWireActivities(it)) }
                )

                // Schalter 2: Theme erzeugen
                X2cSwitchRow(
                    title = stringRes(R.string.x2c_option_generate_theme),
                    description = stringRes(R.string.x2c_option_generate_theme_desc),
                    checked = state.generateTheme,
                    enabled = true,
                    onCheckedChange = { onIntent(X2cIntent.SetGenerateTheme(it)) }
                )

                // Schalter 3: XML nach Erfolg entfernen
                X2cSwitchRow(
                    title = stringRes(R.string.x2c_option_remove_xml),
                    description = stringRes(R.string.x2c_option_remove_xml_desc),
                    checked = state.removeXmlAfterSuccess,
                    enabled = true,
                    onCheckedChange = { onIntent(X2cIntent.SetRemoveXmlAfterSuccess(it)) }
                )

                // Schalter 4: Versionskatalog
                X2cSwitchRow(
                    title = stringRes(R.string.x2c_option_version_catalog),
                    description = stringRes(R.string.x2c_option_version_catalog_desc),
                    checked = state.useVersionCatalog,
                    enabled = hasCatalog,
                    onCheckedChange = { onIntent(X2cIntent.SetUseVersionCatalog(it)) }
                )

                // Schalter 5: Build-Prüfung
                X2cSwitchRow(
                    title = stringRes(R.string.x2c_option_build_check),
                    description = stringRes(R.string.x2c_option_build_check_desc),
                    checked = state.runBuildCheck,
                    enabled = true,
                    onCheckedChange = { onIntent(X2cIntent.SetRunBuildCheck(it)) }
                )

                // Schalter 5b: vorher auch bauen (nur sinnvoll mit Build-Prüfung)
                X2cSwitchRow(
                    title = stringRes(R.string.x2c_option_baseline),
                    description = stringRes(R.string.x2c_option_baseline_desc),
                    checked = state.buildBaselineFirst,
                    enabled = state.runBuildCheck,
                    onCheckedChange = { onIntent(X2cIntent.SetBuildBaselineFirst(it)) }
                )

                // Schalter 6: Git-Zweig
                X2cSwitchRow(
                    title = stringRes(R.string.x2c_option_git_branch),
                    description = stringRes(R.string.x2c_option_git_branch_desc),
                    checked = state.createGitBranch,
                    enabled = true,
                    onCheckedChange = { onIntent(X2cIntent.SetCreateGitBranch(it)) }
                )

                // Schalter 7: .git in Backup
                X2cSwitchRow(
                    title = stringRes(R.string.x2c_option_backup_git),
                    description = null,
                    checked = state.includeGitInBackup,
                    enabled = true,
                    onCheckedChange = { onIntent(X2cIntent.SetIncludeGitInBackup(it)) }
                )

                // Compose Build-Hinweis
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = stringRes(R.string.x2c_compose_build_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringRes(R.string.x2c_disclaimer),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        },
        // Alle Aktionen in einem umbrechenden Block: bei großer Schrift/schmalem Display
        // wurden "Speichern"/"Später" sonst abgeschnitten.
        confirmButton = {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(onClick = { onIntent(X2cIntent.DismissDialog(dontAskAgain = true)) }) {
                    Text(text = stringRes(R.string.x2c_action_dont_ask))
                }
                OutlinedButton(onClick = { onIntent(X2cIntent.DismissDialog(dontAskAgain = false)) }) {
                    Text(text = stringRes(R.string.x2c_action_later))
                }
                Button(
                    onClick = { onIntent(X2cIntent.StartMigration) },
                    enabled = state.selectedLayouts.isNotEmpty()
                ) {
                    Text(text = stringRes(R.string.x2c_action_convert))
                }
            }
        }
    )
}

@Composable
private fun LayoutItemRow(
    entry: LayoutPlanEntry,
    isSelected: Boolean,
    onToggle: () -> Unit,
    onOpenFile: () -> Unit
) {
    val isNotConvertible = entry.classification == LayoutClassification.NOT_CONVERTIBLE

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isNotConvertible, onClick = onToggle)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected && !isNotConvertible,
            onCheckedChange = { onToggle() },
            enabled = !isNotConvertible
        )

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${entry.layoutName}.xml",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                ClassificationBadge(classification = entry.classification)
            }

            Text(
                text = entry.relativePath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = entry.reason,
                style = MaterialTheme.typography.labelSmall,
                color = if (isNotConvertible) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onOpenFile, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ClassificationBadge(classification: LayoutClassification) {
    val (bgColor, textColor, textRes) = when (classification) {
        LayoutClassification.AUTOMATIC -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            R.string.x2c_status_automatic
        )
        LayoutClassification.WITH_NOTES -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            R.string.x2c_status_with_notes
        )
        LayoutClassification.NOT_CONVERTIBLE -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            R.string.x2c_status_not_convertible
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringRes(textRes),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun X2cSwitchRow(
    title: String,
    description: String?,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
