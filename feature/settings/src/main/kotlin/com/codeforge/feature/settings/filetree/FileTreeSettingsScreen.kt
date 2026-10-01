@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.codeforge.feature.settings.filetree

import com.codeforge.core.resources.ResGetter

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

import com.codeforge.core.datastore.proto.FileTreeSortByProto
import com.codeforge.core.datastore.proto.FileTreeSortOrderProto
import com.codeforge.core.datastore.proto.FileTreeViewModeProto

@Composable
fun FileTreeSettingsRoute(
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FileTreeSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    FileTreeSettingsScreen(
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        uiState = uiState,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun FileTreeSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    uiState: FileTreeSettingsUiState,
    onEvent: (FileTreeSettingsUiEvent) -> Unit
) {
    val config = uiState.fileTreeConfig
    val uiScale = if (config.fontSize > 0) config.fontSize / 10f else 1.2f

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_back)
                        )
                    }
                },
                title = { Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_settings_title)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- ABSCHNITT 1: Schriftgröße ---
            SectionHeader(title = ResGetter.get(com.codeforge.core.resources.R.string.filetree_section_fontsize))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_fontsize_label), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_filetree_sp_fmt, (12 * uiScale).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = uiScale,
                        onValueChange = { newScale ->
                            onEvent(FileTreeSettingsUiEvent.FontSizeChanged((newScale * 10).toInt()))
                        },
                        valueRange = 0.8f..2.0f,
                        steps = 12
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = ResGetter.get(com.codeforge.core.resources.R.string.feature_settings_filetree_preview_sample),
                                    fontSize = (12 * uiScale).sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = "1.4 KB • 24.09.26 17:00",
                                fontSize = (10 * uiScale).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 28.dp)
                            )
                        }
                    }
                }
            }

            // --- ABSCHNITT 2: Anzeige-Optionen ---
            SectionHeader(title = ResGetter.get(com.codeforge.core.resources.R.string.filetree_section_display))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingSwitchRow(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.filetree_show_indent_lines),
                        subtitle = ResGetter.get(com.codeforge.core.resources.R.string.filetree_show_indent_lines_sub),
                        checked = config.showIndentLines,
                        onCheckedChange = { onEvent(FileTreeSettingsUiEvent.ShowIndentLinesToggled(it)) }
                    )

                    HorizontalDivider()

                    SettingSwitchRow(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.filetree_show_file_details),
                        subtitle = ResGetter.get(com.codeforge.core.resources.R.string.filetree_show_file_details_sub),
                        checked = config.showFileDetails,
                        onCheckedChange = { onEvent(FileTreeSettingsUiEvent.ShowFileDetailsToggled(it)) }
                    )

                    HorizontalDivider()

                    SettingSwitchRow(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.filetree_show_hidden),
                        subtitle = ResGetter.get(com.codeforge.core.resources.R.string.filetree_show_hidden_sub),
                        checked = config.showHiddenFiles,
                        onCheckedChange = { onEvent(FileTreeSettingsUiEvent.ShowHiddenFilesToggled(it)) }
                    )
                }
            }

            // --- ABSCHNITT 3: Sortierung ---
            SectionHeader(title = ResGetter.get(com.codeforge.core.resources.R.string.filetree_section_sort))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_sort_order), style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = config.sortOrder == FileTreeSortOrderProto.SORT_ORDER_ASCENDING,
                            onClick = { onEvent(FileTreeSettingsUiEvent.SortOrderChanged(FileTreeSortOrderProto.SORT_ORDER_ASCENDING)) },
                            label = { Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_sort_asc)) }
                        )
                        FilterChip(
                            selected = config.sortOrder == FileTreeSortOrderProto.SORT_ORDER_DESCENDING,
                            onClick = { onEvent(FileTreeSettingsUiEvent.SortOrderChanged(FileTreeSortOrderProto.SORT_ORDER_DESCENDING)) },
                            label = { Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_sort_desc)) }
                        )
                    }

                    Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_sort_by), style = MaterialTheme.typography.titleSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = config.sortBy == FileTreeSortByProto.SORT_BY_NAME,
                            onClick = { onEvent(FileTreeSettingsUiEvent.SortByChanged(FileTreeSortByProto.SORT_BY_NAME)) },
                            label = { Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_sort_name)) }
                        )
                        FilterChip(
                            selected = config.sortBy == FileTreeSortByProto.SORT_BY_TYPE,
                            onClick = { onEvent(FileTreeSettingsUiEvent.SortByChanged(FileTreeSortByProto.SORT_BY_TYPE)) },
                            label = { Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_sort_type)) }
                        )
                        FilterChip(
                            selected = config.sortBy == FileTreeSortByProto.SORT_BY_SIZE,
                            onClick = { onEvent(FileTreeSettingsUiEvent.SortByChanged(FileTreeSortByProto.SORT_BY_SIZE)) },
                            label = { Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_sort_size)) }
                        )
                        FilterChip(
                            selected = config.sortBy == FileTreeSortByProto.SORT_BY_DATE,
                            onClick = { onEvent(FileTreeSettingsUiEvent.SortByChanged(FileTreeSortByProto.SORT_BY_DATE)) },
                            label = { Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_sort_date)) }
                        )
                    }
                }
            }

            // --- ABSCHNITT 4: Ansichtsmodi (FileTree View Mode) ---
            SectionHeader(title = ResGetter.get(com.codeforge.core.resources.R.string.filetree_section_viewmode))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ViewModeOption(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.filetree_viewmode_module),
                        description = ResGetter.get(com.codeforge.core.resources.R.string.filetree_viewmode_module_sub),
                        selected = config.viewMode == FileTreeViewModeProto.VIEW_MODE_MODULE,
                        onClick = { onEvent(FileTreeSettingsUiEvent.ViewModeChanged(FileTreeViewModeProto.VIEW_MODE_MODULE)) }
                    )

                    ViewModeOption(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.filetree_viewmode_project),
                        description = ResGetter.get(com.codeforge.core.resources.R.string.filetree_viewmode_project_sub),
                        selected = config.viewMode == FileTreeViewModeProto.VIEW_MODE_PROJECT,
                        onClick = { onEvent(FileTreeSettingsUiEvent.ViewModeChanged(FileTreeViewModeProto.VIEW_MODE_PROJECT)) }
                    )

                    ViewModeOption(
                        title = ResGetter.get(com.codeforge.core.resources.R.string.filetree_viewmode_file),
                        description = ResGetter.get(com.codeforge.core.resources.R.string.filetree_viewmode_file_sub),
                        selected = config.viewMode == FileTreeViewModeProto.VIEW_MODE_FILE,
                        onClick = { onEvent(FileTreeSettingsUiEvent.ViewModeChanged(FileTreeViewModeProto.VIEW_MODE_FILE)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun ViewModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 32.dp)
            )
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}
