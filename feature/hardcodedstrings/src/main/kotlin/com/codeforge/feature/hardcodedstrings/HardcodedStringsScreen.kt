package com.codeforge.feature.hardcodedstrings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.libs.hardcodedstrings.model.ExtractionPlan
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringFinding
import com.codeforge.libs.hardcodedstrings.model.ReplacementContextType
import com.codeforge.libs.hardcodedstrings.model.StringConfidence

@Composable
fun HardcodedStringsRoute(
    targetPath: String,
    projectRoot: String,
    onNavigateBack: () -> Unit,
    onOpenEditorAt: (String, Int, Int) -> Unit = { _, _, _ -> },
    viewModel: HardcodedStringsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(targetPath, projectRoot) {
        viewModel.onEvent(HardcodedStringsUiEvent.ScanRequested(targetPath, projectRoot))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is HardcodedStringsUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
                is HardcodedStringsUiEffect.OpenEditorAt -> {
                    onOpenEditorAt(effect.filePath, effect.line, effect.column)
                }
            }
        }
    }

    HardcodedStringsScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        onNavigateBack = onNavigateBack,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardcodedStringsScreen(
    uiState: HardcodedStringsUiState,
    onEvent: (HardcodedStringsUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.hardcoded_strings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringRes(R.string.common_zurueck)
                        )
                    }
                },
                actions = {
                    if (uiState.isUndoAvailable) {
                        IconButton(onClick = { onEvent(HardcodedStringsUiEvent.RequestUndo) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = stringRes(R.string.hardcoded_strings_rueckgaengig)
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            onEvent(HardcodedStringsUiEvent.ScanRequested(uiState.targetPath, uiState.projectRoot))
                        }
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringRes(R.string.hardcoded_strings_neu_scannen)
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringRes(R.string.hardcoded_strings_ausgewaehlt, uiState.selectedCount),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Button(
                        onClick = { onEvent(HardcodedStringsUiEvent.RequestPreview) },
                        enabled = uiState.selectedCount > 0 && !uiState.isApplying
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringRes(R.string.hardcoded_strings_vorschau_und_anwenden))
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search & Filter Row
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { onEvent(HardcodedStringsUiEvent.SearchQueryChanged(it)) },
                        placeholder = { Text(stringRes(R.string.hardcoded_strings_suche_platzhalter)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onEvent(HardcodedStringsUiEvent.SearchQueryChanged("")) }) {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(8.dp))

                    // Filter chips row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = uiState.confidenceFilter == null,
                            onClick = { onEvent(HardcodedStringsUiEvent.ConfidenceFilterChanged(null)) },
                            label = { Text(stringRes(R.string.hardcoded_strings_alle)) }
                        )
                        FilterChip(
                            selected = uiState.confidenceFilter == StringConfidence.HIGH,
                            onClick = { onEvent(HardcodedStringsUiEvent.ConfidenceFilterChanged(StringConfidence.HIGH)) },
                            label = { Text(stringRes(R.string.hardcoded_strings_sicher)) }
                        )
                        FilterChip(
                            selected = uiState.confidenceFilter == StringConfidence.MEDIUM,
                            onClick = { onEvent(HardcodedStringsUiEvent.ConfidenceFilterChanged(StringConfidence.MEDIUM)) },
                            label = { Text(stringRes(R.string.hardcoded_strings_wahrscheinlich)) }
                        )
                        FilterChip(
                            selected = uiState.confidenceFilter == StringConfidence.LOW,
                            onClick = { onEvent(HardcodedStringsUiEvent.ConfidenceFilterChanged(StringConfidence.LOW)) },
                            label = { Text(stringRes(R.string.hardcoded_strings_unsicher)) }
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Select all row
                    val filtered = uiState.filteredFindings
                    val allFilteredSelected = filtered.isNotEmpty() && filtered.all { uiState.selectedIds.contains(it.id) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = allFilteredSelected,
                            onCheckedChange = { checked ->
                                onEvent(HardcodedStringsUiEvent.SelectAllToggled(checked))
                            }
                        )
                        Text(
                            text = stringRes(R.string.hardcoded_strings_alle_auswaehlen),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = stringRes(R.string.hardcoded_strings_strings_gefunden, filtered.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                // Findings list
                if (uiState.filteredFindings.isEmpty() && !uiState.isScanning) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = stringRes(R.string.hardcoded_strings_keine_treffer),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.filteredFindings, key = { it.id }) { finding ->
                            FindingCard(
                                finding = finding,
                                isSelected = uiState.selectedIds.contains(finding.id),
                                customKey = uiState.customKeys[finding.id] ?: finding.suggestedKey,
                                onToggle = { onEvent(HardcodedStringsUiEvent.FindingToggled(finding.id)) },
                                onKeyChange = { newKey -> onEvent(HardcodedStringsUiEvent.KeyChanged(finding.id, newKey)) },
                                onJumpToSource = { onEvent(HardcodedStringsUiEvent.JumpToSource(finding)) },
                                onIgnore = { onEvent(HardcodedStringsUiEvent.IgnoreFinding(finding)) }
                            )
                        }
                    }
                }
            }

            // Scanning / Applying indicator overlay
            if (uiState.isScanning || uiState.isApplying) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = if (uiState.isScanning) {
                                    stringRes(R.string.hardcoded_strings_scanne)
                                } else {
                                    stringRes(R.string.hardcoded_strings_anwenden_laeuft)
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.isPreviewDialogOpen && uiState.pendingPlan != null) {
        DiffPreviewDialog(
            plan = uiState.pendingPlan,
            onConfirm = { onEvent(HardcodedStringsUiEvent.ConfirmApply) },
            onDismiss = { onEvent(HardcodedStringsUiEvent.DismissPreview) }
        )
    }
}

@Composable
private fun FindingCard(
    finding: HardcodedStringFinding,
    isSelected: Boolean,
    customKey: String,
    onToggle: () -> Unit,
    onKeyChange: (String) -> Unit,
    onJumpToSource: () -> Unit,
    onIgnore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header Row: Checkbox, File path, Confidence badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() }
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${finding.file.name}:${finding.line}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = finding.relativePath,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                ConfidenceBadge(finding.confidence)
            }

            Spacer(Modifier.height(8.dp))

            // Raw literal display
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = finding.rawValue,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(8.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Editable Key Input
            OutlinedTextField(
                value = customKey,
                onValueChange = onKeyChange,
                label = { Text(stringRes(R.string.hardcoded_strings_res_key)) },
                prefix = {
                    Text(
                        text = when (finding.contextType) {
                            ReplacementContextType.XML_ATTRIBUTE -> "@string/"
                            else -> "R.string."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // Footer row: Context Type and Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val contextLabel = when (finding.contextType) {
                    ReplacementContextType.COMPOSE -> stringRes(R.string.hardcoded_strings_type_compose)
                    ReplacementContextType.ANDROID_CONTEXT -> stringRes(R.string.hardcoded_strings_type_context)
                    ReplacementContextType.XML_ATTRIBUTE -> stringRes(R.string.hardcoded_strings_type_xml)
                    ReplacementContextType.MANUAL -> stringRes(R.string.hardcoded_strings_type_manual)
                }

                SuggestionChip(
                    onClick = {},
                    label = { Text(contextLabel, style = MaterialTheme.typography.labelSmall) }
                )

                Row {
                    IconButton(onClick = onJumpToSource) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = stringRes(R.string.hardcoded_strings_in_editor_oeffnen)
                        )
                    }
                    IconButton(onClick = onIgnore) {
                        Icon(
                            Icons.Default.VisibilityOff,
                            contentDescription = stringRes(R.string.hardcoded_strings_ignorieren)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: StringConfidence) {
    val (labelRes, containerColor, contentColor) = when (confidence) {
        StringConfidence.HIGH -> Triple(
            R.string.hardcoded_strings_sicher,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        StringConfidence.MEDIUM -> Triple(
            R.string.hardcoded_strings_wahrscheinlich,
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        StringConfidence.LOW -> Triple(
            R.string.hardcoded_strings_unsicher,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = stringRes(labelRes),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DiffPreviewDialog(
    plan: ExtractionPlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringRes(R.string.hardcoded_strings_diff_titel)) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(350.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringRes(
                            R.string.hardcoded_strings_diff_summary,
                            plan.totalStringsCount,
                            plan.filePlans.size + plan.stringsXmlUpdates.size
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Strings.xml updates
                items(plan.stringsXmlUpdates.keys.toList()) { xmlFile ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = xmlFile.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = xmlFile.parentFile?.name ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // File code diffs
                items(plan.filePlans) { filePlan ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = filePlan.file.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (filePlan.diff.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = filePlan.diff,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        modifier = Modifier.padding(6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(stringRes(R.string.hardcoded_strings_diff_anwenden))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringRes(R.string.common_abbrechen))
            }
        }
    )
}
