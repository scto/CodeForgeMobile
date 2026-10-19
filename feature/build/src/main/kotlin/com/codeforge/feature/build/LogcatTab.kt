/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeforge.core.domain.model.LogEntry
import com.codeforge.core.domain.model.LogLevel
import com.codeforge.core.domain.model.LogcatSource
import com.codeforge.core.domain.model.StackFrame
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@Composable
fun LogcatTab(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    // Automatisches Scrollen ans Ende, wenn nicht pausiert und autoScroll aktiv ist
    LaunchedEffect(state.filteredLogcatEntries.size, state.autoScroll, state.isLogcatPaused) {
        if (state.autoScroll && !state.isLogcatPaused && state.filteredLogcatEntries.isNotEmpty()) {
            listState.animateScrollToItem(state.filteredLogcatEntries.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Filter- und Steuerleiste
        LogcatFilterBar(
            state = state,
            onIntent = onIntent,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // Aktionsleiste (Pause, Clear, Source, Share, Count)
        LogcatActionBar(
            state = state,
            onIntent = onIntent,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // Log-Liste
        if (state.filteredLogcatEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringRes(R.string.logcat_keine_eintraege),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = state.filteredLogcatEntries,
                    key = { it.id }
                ) { entry ->
                    LogcatEntryItem(
                        entry = entry,
                        onCopy = { onIntent(BuildUiIntent.CopyLogcatEntry(entry)) },
                        onStackFrameClick = { onIntent(BuildUiIntent.ClickStackFrame(it)) }
                    )
                }
            }
        }
    }

    if (state.isLogcatSourceDialogVisible) {
        LogcatSourceDialog(
            state = state,
            onDismiss = { onIntent(BuildUiIntent.ShowLogcatSourceDialog(false)) },
            onSelectSource = { source ->
                onIntent(BuildUiIntent.SelectLogcatSource(source))
                onIntent(BuildUiIntent.ShowLogcatSourceDialog(false))
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogcatFilterBar(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    var isPackageMenuExpanded by remember { mutableStateOf(false) }
    var isLevelMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Obere Zeile: Suchfeld und Tag-Filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.logcatFilter.textQuery,
                onValueChange = { onIntent(BuildUiIntent.UpdateLogcatFilter(state.logcatFilter.copy(textQuery = it))) },
                placeholder = { Text(stringRes(R.string.logcat_filter_search), fontSize = 11.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringRes(R.string.logcat_filter_search),
                        modifier = Modifier.size(14.dp)
                    )
                },
                trailingIcon = {
                    if (state.logcatFilter.textQuery.isNotEmpty()) {
                        IconButton(onClick = { onIntent(BuildUiIntent.UpdateLogcatFilter(state.logcatFilter.copy(textQuery = ""))) }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringRes(R.string.common_loeschen),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )

            OutlinedTextField(
                value = state.logcatFilter.tagQuery,
                onValueChange = { onIntent(BuildUiIntent.UpdateLogcatFilter(state.logcatFilter.copy(tagQuery = it))) },
                placeholder = { Text(stringRes(R.string.logcat_filter_tag), fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier
                    .width(110.dp)
                    .height(44.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp)
            )
        }

        // Untere Zeile: Paket-Wähler, Level-Wähler, Abstürze-Toggle-Chip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Paket-Dropdown
            Box {
                FilterChip(
                    selected = state.logcatFilter.packageName.isNotBlank(),
                    onClick = { isPackageMenuExpanded = true },
                    label = {
                        val pkgLabel = if (state.logcatFilter.packageName.isBlank()) {
                            stringRes(R.string.logcat_filter_all_packages)
                        } else {
                            state.logcatFilter.packageName.substringAfterLast('.')
                        }
                        Text(
                            text = pkgLabel,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                DropdownMenu(
                    expanded = isPackageMenuExpanded,
                    onDismissRequest = { isPackageMenuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringRes(R.string.logcat_filter_all_packages)) },
                        onClick = {
                            onIntent(BuildUiIntent.UpdateLogcatFilter(state.logcatFilter.copy(packageName = "", targetPid = null)))
                            isPackageMenuExpanded = false
                        }
                    )
                    state.recentPackages.forEach { pkg ->
                        DropdownMenuItem(
                            text = { Text(pkg) },
                            onClick = {
                                onIntent(BuildUiIntent.UpdateLogcatFilter(state.logcatFilter.copy(packageName = pkg, targetPid = null)))
                                isPackageMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // LogLevel-Dropdown
            Box {
                FilterChip(
                    selected = state.logcatFilter.minLevel != LogLevel.VERBOSE,
                    onClick = { isLevelMenuExpanded = true },
                    label = {
                        Text(
                            text = "${stringRes(R.string.logcat_filter_level)}: ${state.logcatFilter.minLevel.letter}",
                            fontSize = 11.sp
                        )
                    },
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )

                DropdownMenu(
                    expanded = isLevelMenuExpanded,
                    onDismissRequest = { isLevelMenuExpanded = false }
                ) {
                    LogLevel.entries.forEach { level ->
                        DropdownMenuItem(
                            text = {
                                Text("${level.letter} - ${level.name}")
                            },
                            onClick = {
                                onIntent(BuildUiIntent.UpdateLogcatFilter(state.logcatFilter.copy(minLevel = level)))
                                isLevelMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Nur Abstürze FilterChip
            FilterChip(
                selected = state.logcatFilter.crashesOnly,
                onClick = {
                    onIntent(BuildUiIntent.UpdateLogcatFilter(state.logcatFilter.copy(crashesOnly = !state.logcatFilter.crashesOnly)))
                },
                label = {
                    Text(
                        text = stringRes(R.string.logcat_filter_crashes_only),
                        fontSize = 11.sp,
                        color = if (state.logcatFilter.crashesOnly) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                },
                leadingIcon = {
                    if (state.logcatFilter.crashesOnly) {
                        Icon(
                             imageVector = Icons.Default.Error,
                             contentDescription = null,
                             tint = MaterialTheme.colorScheme.error,
                             modifier = Modifier.size(14.dp)
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun LogcatActionBar(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Linke Steuerung: Pause, Clear, AutoScroll, Quelle-Badge
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Pause / Resume
            IconButton(
                onClick = { onIntent(BuildUiIntent.ToggleLogcatPause) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = if (state.isLogcatPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (state.isLogcatPaused) stringRes(R.string.logcat_aktion_resume) else stringRes(R.string.logcat_aktion_pause),
                    tint = if (state.isLogcatPaused) Color(0xFFF57C00) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Clear
            IconButton(
                onClick = { onIntent(BuildUiIntent.ClearLogcat) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringRes(R.string.logcat_aktion_clear),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Auto-Scroll Toggle
            IconToggleButton(
                checked = state.autoScroll,
                onCheckedChange = { onIntent(BuildUiIntent.ToggleAutoScroll) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.VerticalAlignBottom,
                    contentDescription = stringRes(R.string.logcat_auto_scroll),
                    tint = if (state.autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Quell-Button / Badge
            val sourceText = when (state.logcatSource) {
                LogcatSource.OWN_PROCESS -> stringRes(R.string.logcat_quelle_own_process)
                LogcatSource.READ_LOGS_PERMISSION -> stringRes(R.string.logcat_quelle_read_logs)
                LogcatSource.ADB_LOOPBACK -> stringRes(R.string.logcat_quelle_adb)
            }
            Surface(
                onClick = { onIntent(BuildUiIntent.ShowLogcatSourceDialog(true)) },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.height(26.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = sourceText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Rechte Steuerung: Anzahl, Copy All, Share
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = stringRes(R.string.logcat_eintraege_anzahl, state.filteredLogcatEntries.size),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )

            IconButton(
                onClick = { onIntent(BuildUiIntent.CopyAllLogcat) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringRes(R.string.logcat_aktion_copy_all),
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = { onIntent(BuildUiIntent.ShareLogcat) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = stringRes(R.string.logcat_aktion_share),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun LogcatEntryItem(
    entry: LogEntry,
    onCopy: () -> Unit,
    onStackFrameClick: (StackFrame) -> Unit,
    modifier: Modifier = Modifier
) {
    val levelColor = when (entry.level) {
        LogLevel.VERBOSE -> Color(0xFF9E9E9E)
        LogLevel.DEBUG -> Color(0xFF1976D2)
        LogLevel.INFO -> Color(0xFF388E3C)
        LogLevel.WARN -> Color(0xFFF57C00)
        LogLevel.ERROR -> Color(0xFFD32F2F)
        LogLevel.FATAL -> Color(0xFF7B1FA2)
    }

    val cardColor = if (entry.isCrash) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (entry.isCrash) Modifier.border(1.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(8.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header-Zeile: Level-Badge, Tag, PID/TID, Timestamp, Copy-Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Level-Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = levelColor
                    ) {
                        Text(
                            text = "${entry.level.letter}",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    // Tag
                    Text(
                        text = entry.tag,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // PID / TID & Timestamp
                    Text(
                        text = "${entry.pid}/${entry.tid}  ${entry.timestamp}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = stringRes(R.string.logcat_aktion_copy_entry),
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Crash-Banner falls vorhanden
            if (entry.isCrash) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringRes(R.string.logcat_absturz_erkannt, entry.tag),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Message-Text
            Text(
                text = entry.message,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = if (entry.isCrash) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp)
            )

            // Clickable Stack Trace Frames
            if (entry.stackFrames.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    entry.stackFrames.forEach { frame ->
                        StackFrameRow(
                            frame = frame,
                            onClick = { onStackFrameClick(frame) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StackFrameRow(
    frame: StackFrame,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val frameText = stringRes(
            R.string.logcat_stacktrace_zeile,
            "${frame.className}.${frame.methodName}",
            frame.fileName,
            frame.lineNumber
        )

        Text(
            text = frameText,
            fontFamily = FontFamily.Monospace,
            fontSize = 9.5.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.Launch,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(13.dp)
                .padding(start = 2.dp)
        )
    }
}

@Composable
private fun LogcatSourceDialog(
    state: BuildUiState,
    onDismiss: () -> Unit,
    onSelectSource: (LogcatSource) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringRes(R.string.logcat_quelle_titel),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                state.availableLogcatSources.forEach { item ->
                    val isSelected = state.logcatSource == item.source
                    val titleText = when (item.source) {
                        LogcatSource.OWN_PROCESS -> stringRes(R.string.logcat_quelle_own_process)
                        LogcatSource.READ_LOGS_PERMISSION -> stringRes(R.string.logcat_quelle_read_logs)
                        LogcatSource.ADB_LOOPBACK -> stringRes(R.string.logcat_quelle_adb)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = item.isAvailable) { onSelectSource(item.source) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        ),
                        shape = RoundedCornerShape(8.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val radioIcon = if (isSelected) {
                                        Icons.Default.RadioButtonChecked
                                    } else {
                                        Icons.Default.RadioButtonUnchecked
                                    }
                                    val radioTint = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Icon(
                                        imageVector = radioIcon,
                                        contentDescription = null,
                                        tint = radioTint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = titleText,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                if (item.isAvailable) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = Color(0xFF2E7D32),
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            Text(
                                text = stringRes(item.explanationResId),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            val helpRes = item.helpInstructionResId
                            if (helpRes != null) {
                                Text(
                                    text = stringRes(helpRes),
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                        .padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringRes(R.string.logcat_schliessen))
            }
        }
    )
}
