package com.codeforge.feature.filetree

import com.codeforge.core.resources.ResGetter

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Expand
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeforge.core.datastore.proto.FileTreeSortByProto
import com.codeforge.core.datastore.proto.FileTreeSortOrderProto
import com.codeforge.core.datastore.proto.FileTreeViewModeProto

@Composable
internal fun FileTreeToolbar(
    onCollapseAll: () -> Unit,
    onExpandAll: () -> Unit,
    onToggleSearch: () -> Unit,
    isCompactMode: Boolean,
    onToggleCompact: () -> Unit,
    searchRegex: Boolean,
    onToggleRegex: () -> Unit,
    searchCaseSensitive: Boolean,
    onToggleCaseSensitive: () -> Unit,
    sortOrder: FileTreeSortOrderProto = FileTreeSortOrderProto.SORT_ORDER_ASCENDING,
    onSortOrderChanged: (FileTreeSortOrderProto) -> Unit = {},
    sortBy: FileTreeSortByProto = FileTreeSortByProto.SORT_BY_NAME,
    onSortByChanged: (FileTreeSortByProto) -> Unit = {},
    showHiddenFiles: Boolean = false,
    onToggleShowHidden: () -> Unit = {},
    showIndentLines: Boolean = true,
    onToggleShowIndentLines: () -> Unit = {},
    showFileDetails: Boolean = true,
    onToggleShowFileDetails: () -> Unit = {},
    uiScale: Float = 1.2f,
    onUiScaleChanged: (Float) -> Unit = {},
    viewMode: FileTreeViewModeProto = FileTreeViewModeProto.VIEW_MODE_MODULE,
    onViewModeChanged: (FileTreeViewModeProto) -> Unit = {},
    menuExpanded: Boolean,
    onToggleMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    searchOpen: Boolean = false,
    searchQuery: String = "",
    onQueryChange: (String) -> Unit = {},
    onCloseDrawer: (() -> Unit)? = null
) {
    var viewModeMenuExpanded by remember { mutableStateOf(false) }
    var settingsDialogOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onCloseDrawer != null) {
            IconButton(onClick = onCloseDrawer) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_close_drawer),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(onClick = onCollapseAll) {
            Icon(Icons.Filled.Compress, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.cd_collapse_all), tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onExpandAll) {
            Icon(Icons.Filled.Expand, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.cd_expand_all), tint = MaterialTheme.colorScheme.onSurface)
        }
        
        // Dedicated View Mode Switcher Button (Android Studio parity)
        Box {
            IconButton(onClick = { viewModeMenuExpanded = true }) {
                val modeIcon = when (viewMode) {
                    FileTreeViewModeProto.VIEW_MODE_MODULE -> Icons.Filled.Layers
                    FileTreeViewModeProto.VIEW_MODE_PROJECT -> Icons.Filled.Folder
                    else -> Icons.Filled.InsertDriveFile
                }
                Icon(modeIcon, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_switch_viewmode), tint = MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(
                expanded = viewModeMenuExpanded,
                onDismissRequest = { viewModeMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_viewmode_module)) },
                    trailingIcon = { if (viewMode == FileTreeViewModeProto.VIEW_MODE_MODULE) Text("✓") },
                    onClick = {
                        onViewModeChanged(FileTreeViewModeProto.VIEW_MODE_MODULE)
                        viewModeMenuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_viewmode_project)) },
                    trailingIcon = { if (viewMode == FileTreeViewModeProto.VIEW_MODE_PROJECT) Text("✓") },
                    onClick = {
                        onViewModeChanged(FileTreeViewModeProto.VIEW_MODE_PROJECT)
                        viewModeMenuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_viewmode_file)) },
                    trailingIcon = { if (viewMode == FileTreeViewModeProto.VIEW_MODE_FILE) Text("✓") },
                    onClick = {
                        onViewModeChanged(FileTreeViewModeProto.VIEW_MODE_FILE)
                        viewModeMenuExpanded = false
                    }
                )
            }
        }

        IconButton(onClick = onToggleSearch) {
            Icon(Icons.Filled.Search, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_search), tint = MaterialTheme.colorScheme.onSurface)
        }

        if (searchOpen) {
            BasicTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(
                    MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(3.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(3.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                decorationBox = { innerTextField ->
                    Box {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Suchen...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        innerTextField()
                    }
                }
            )
            IconButton(onClick = onToggleSearch) {
                Icon(Icons.Filled.Close, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.action_close), tint = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // Dedicated Settings Button
        IconButton(onClick = { settingsDialogOpen = true }) {
            Icon(Icons.Filled.Settings, contentDescription = ResGetter.get(com.codeforge.core.resources.R.string.filetree_settings_title), tint = MaterialTheme.colorScheme.onSurface)
        }

        Box {
            IconButton(onClick = onToggleMenu) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Optionen", tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onDismissMenu
            ) {
                DropdownMenuItem(
                    text = { Text("Versteckte Dateien anzeigen") },
                    trailingIcon = { Checkbox(checked = showHiddenFiles, onCheckedChange = null) },
                    onClick = {
                        onToggleShowHidden()
                        onDismissMenu()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Einrückungslinien anzeigen") },
                    trailingIcon = { Checkbox(checked = showIndentLines, onCheckedChange = null) },
                    onClick = {
                        onToggleShowIndentLines()
                        onDismissMenu()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Dateidetails (Größe & Datum)") },
                    trailingIcon = { Checkbox(checked = showFileDetails, onCheckedChange = null) },
                    onClick = {
                        onToggleShowFileDetails()
                        onDismissMenu()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Kompakte Ansicht") },
                    trailingIcon = { Checkbox(checked = isCompactMode, onCheckedChange = null) },
                    onClick = {
                        onToggleCompact()
                        onDismissMenu()
                    }
                )

                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("⚙️ Dateibaum Einstellungen...") },
                    onClick = {
                        onDismissMenu()
                        settingsDialogOpen = true
                    }
                )
            }
        }
    }

    // FileTree Dedicated Settings Dialog
    if (settingsDialogOpen) {
        AlertDialog(
            onDismissRequest = { settingsDialogOpen = false },
            title = { Text("Dateibaum Einstellungen", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Font Size / UI Scale slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Schriftgröße", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${(12 * uiScale).toInt()} sp",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = uiScale,
                            onValueChange = onUiScaleChanged,
                            valueRange = 0.8f..2.0f,
                            steps = 12
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Vorschau: SampleClass.kt  [1.4 KB • 24.09 09:48]",
                                    fontSize = (12 * uiScale).sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Display Toggles
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Anzeige-Optionen", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleShowIndentLines() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = showIndentLines, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Einrückungslinien anzeigen", style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleShowFileDetails() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = showFileDetails, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Dateidetails (Größe & Datum)", style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleShowHidden() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = showHiddenFiles, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Versteckte Dateien anzeigen", style = MaterialTheme.typography.bodyMedium)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleCompact() },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = isCompactMode, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Kompakte Ordneransicht", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    HorizontalDivider()

                    // View Modes Selection
                    Column {
                        Text("Ansichtsmodus (Android Studio)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onViewModeChanged(FileTreeViewModeProto.VIEW_MODE_MODULE) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = viewMode == FileTreeViewModeProto.VIEW_MODE_MODULE, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(ResGetter.get(com.codeforge.core.resources.R.string.feature_filetree_viewmode_module), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onViewModeChanged(FileTreeViewModeProto.VIEW_MODE_PROJECT) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = viewMode == FileTreeViewModeProto.VIEW_MODE_PROJECT, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_viewmode_project), style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onViewModeChanged(FileTreeViewModeProto.VIEW_MODE_FILE) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = viewMode == FileTreeViewModeProto.VIEW_MODE_FILE, onClick = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(ResGetter.get(com.codeforge.core.resources.R.string.filetree_viewmode_file), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { settingsDialogOpen = false }) {
                    Text("Fertig")
                }
            }
        )
    }
}
