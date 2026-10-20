// Modul: :feature:build
package com.codeforge.feature.build.analyzer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeforge.core.domain.analyzer.ApkReportFormatter
import com.codeforge.core.domain.model.ApkAnalysisResult
import com.codeforge.core.domain.model.ApkComparisonResult
import com.codeforge.core.domain.model.ApkEntryDiff
import com.codeforge.core.domain.model.ApkEntryDiffType
import com.codeforge.core.domain.model.ApkEntryInfo
import com.codeforge.core.domain.model.ApkPermission
import com.codeforge.core.domain.model.ApkSection
import com.codeforge.core.domain.model.ApkSectionSummary
import com.codeforge.core.domain.model.ApkVerificationResult
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import java.util.Locale

private fun getSectionColor(section: ApkSection): Color = when (section) {
    ApkSection.DEX -> Color(0xFF3F51B5)
    ApkSection.RES -> Color(0xFF4CAF50)
    ApkSection.ARSC -> Color(0xFF009688)
    ApkSection.LIB -> Color(0xFFFF9800)
    ApkSection.ASSETS -> Color(0xFF9C27B0)
    ApkSection.META_INF -> Color(0xFF607D8B)
    ApkSection.MANIFEST -> Color(0xFF00BCD4)
    ApkSection.OTHER -> Color(0xFF795548)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkAnalyzerScreen(
    uiState: ApkAnalyzerUiState,
    onEvent: (ApkAnalyzerUiEvent) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringRes(R.string.apk_analyzer_titel),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        uiState.analysisResult?.let {
                            Text(
                                text = it.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringRes(R.string.common_zurueck)
                        )
                    }
                },
                actions = {
                    if (uiState.analysisResult != null) {
                        IconButton(onClick = { onEvent(ApkAnalyzerUiEvent.ShareMarkdownReport) }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringRes(R.string.apk_analyzer_teilen_markdown)
                            )
                        }
                    }
                    uiState.baseApkPath?.let { path ->
                        IconButton(onClick = { onEvent(ApkAnalyzerUiEvent.LoadApk(path)) }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringRes(R.string.common_aktualisieren)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringRes(R.string.apk_analyzer_analyse_laeuft),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                uiState.errorMessage != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.errorMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                uiState.analysisResult != null -> {
                    ApkAnalyzerContent(
                        result = uiState.analysisResult,
                        uiState = uiState,
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = stringRes(R.string.apk_analyzer_keine_datei))
                    }
                }
            }
        }
    }
}

@Composable
private fun ApkAnalyzerContent(
    result: ApkAnalysisResult,
    uiState: ApkAnalyzerUiState,
    onEvent: (ApkAnalyzerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Tab-Leiste
        ScrollableTabRow(
            selectedTabIndex = uiState.activeTab.ordinal,
            edgePadding = 12.dp
        ) {
            Tab(
                selected = uiState.activeTab == ApkAnalyzerTab.OVERVIEW,
                onClick = { onEvent(ApkAnalyzerUiEvent.SelectTab(ApkAnalyzerTab.OVERVIEW)) },
                text = { Text(stringRes(R.string.apk_analyzer_tab_uebersicht)) }
            )
            Tab(
                selected = uiState.activeTab == ApkAnalyzerTab.FILES,
                onClick = { onEvent(ApkAnalyzerUiEvent.SelectTab(ApkAnalyzerTab.FILES)) },
                text = { Text(stringRes(R.string.apk_analyzer_tab_dateien)) }
            )
            Tab(
                selected = uiState.activeTab == ApkAnalyzerTab.PERMISSIONS,
                onClick = { onEvent(ApkAnalyzerUiEvent.SelectTab(ApkAnalyzerTab.PERMISSIONS)) },
                text = { Text(stringRes(R.string.apk_analyzer_tab_berechtigungen)) }
            )
            Tab(
                selected = uiState.activeTab == ApkAnalyzerTab.COMPONENTS,
                onClick = { onEvent(ApkAnalyzerUiEvent.SelectTab(ApkAnalyzerTab.COMPONENTS)) },
                text = { Text(stringRes(R.string.apk_analyzer_tab_komponenten)) }
            )
            Tab(
                selected = uiState.activeTab == ApkAnalyzerTab.SIGNATURE,
                onClick = { onEvent(ApkAnalyzerUiEvent.SelectTab(ApkAnalyzerTab.SIGNATURE)) },
                text = { Text(stringRes(R.string.apk_analyzer_tab_signatur)) }
            )
            Tab(
                selected = uiState.activeTab == ApkAnalyzerTab.COMPARE,
                onClick = { onEvent(ApkAnalyzerUiEvent.SelectTab(ApkAnalyzerTab.COMPARE)) },
                text = { Text(stringRes(R.string.apk_analyzer_tab_vergleich)) }
            )
        }

        when (uiState.activeTab) {
            ApkAnalyzerTab.OVERVIEW -> OverviewTab(result = result, onNavigateToFiles = { onEvent(ApkAnalyzerUiEvent.SelectTab(ApkAnalyzerTab.FILES)) })
            ApkAnalyzerTab.FILES -> FilesTab(result = result, uiState = uiState, onEvent = onEvent)
            ApkAnalyzerTab.PERMISSIONS -> PermissionsTab(result = result, uiState = uiState, onEvent = onEvent)
            ApkAnalyzerTab.COMPONENTS -> ComponentsTab(result = result)
            ApkAnalyzerTab.SIGNATURE -> SignatureTab(result = result)
            ApkAnalyzerTab.COMPARE -> CompareTab(result = result, uiState = uiState, onEvent = onEvent)
        }
    }
}

@Composable
private fun OverviewTab(
    result: ApkAnalysisResult,
    onNavigateToFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Zusammenfassung oben (35.4)
        ApkSummaryCard(result = result)

        // Warnungen (Debuggable, Backup, Multi-ABI) (35.2)
        if (result.analysisWarnings.isNotEmpty()) {
            WarningsCard(warnings = result.analysisWarnings)
        }

        // Balkenübersicht je Bereich (35.4)
        ApkSectionBarChart(sections = result.sections)

        // Pro-ABI-Aufschlüsselung (35.1)
        if (result.abiSummaries.isNotEmpty()) {
            AbiBreakdownCard(result = result)
        }

        // Top-Dateien (35.1)
        TopFilesCard(topEntries = result.topEntries, onMoreClick = onNavigateToFiles)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApkSummaryCard(
    result: ApkAnalysisResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = result.fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = result.apkFile.absolutePath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringRes(R.string.apk_analyzer_komprimiert),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = ApkReportFormatter.formatBytes(result.fileSizeBytes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column {
                    Text(
                        text = stringRes(R.string.apk_analyzer_unkomprimiert),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = ApkReportFormatter.formatBytes(result.totalUncompressedSize),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Column {
                    Text(
                        text = stringRes(R.string.apk_analyzer_alle_dateien),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${result.entries.size}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            result.metadata?.let { meta ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                meta.packageName?.let { pkg ->
                    Text(
                        text = stringRes(R.string.apk_analyzer_paketname, pkg),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (meta.versionName != null || meta.versionCode != null) {
                    val vName = meta.versionName ?: "-"
                    val vCode = meta.versionCode?.toString() ?: "-"
                    Text(
                        text = stringRes(R.string.apk_analyzer_version, vName, vCode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    meta.minSdk?.let {
                        ChipBadge(text = stringRes(R.string.apk_analyzer_min_sdk, it.toString()))
                    }
                    meta.targetSdk?.let {
                        ChipBadge(text = stringRes(R.string.apk_analyzer_target_sdk, it.toString()))
                    }
                    meta.compileSdk?.let {
                        ChipBadge(text = stringRes(R.string.apk_analyzer_compile_sdk, it.toString()))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun WarningsCard(
    warnings: List<String>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringRes(R.string.apk_analyzer_warnungen),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
            for (w in warnings) {
                Text(
                    text = w,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@Composable
private fun ApkSectionBarChart(
    sections: List<ApkSectionSummary>,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringRes(R.string.apk_analyzer_tab_uebersicht),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Gestapelter Größenbalken (Stacked Bar)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                for (sec in sections) {
                    if (sec.compressedPercentage > 0.1) {
                        Box(
                            modifier = Modifier
                                .weight(sec.compressedPercentage.toFloat())
                                .height(24.dp)
                                .background(getSectionColor(sec.section))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Legende
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (sec in sections) {
                    if (sec.entryCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(getSectionColor(sec.section))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = getSectionLabel(sec.section),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = " (${sec.entryCount})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = ApkReportFormatter.formatBytes(sec.compressedSize),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f%%", sec.compressedPercentage),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(48.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AbiBreakdownCard(
    result: ApkAnalysisResult,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringRes(R.string.apk_analyzer_abschnitt_lib),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (result.isMultiAbi) {
                    Text(
                        text = "Multi-ABI (${result.abiSummaries.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            for (abi in result.abiSummaries) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = abi.abi,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${abi.fileCount} Dateien • ${ApkReportFormatter.formatBytes(abi.compressedSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TopFilesCard(
    topEntries: List<ApkEntryInfo>,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringRes(R.string.apk_analyzer_top_dateien),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = onMoreClick) {
                    Text(stringRes(R.string.apk_analyzer_alle_dateien), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            for (e in topEntries) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = e.path.substringAfterLast('/'),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = e.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = ApkReportFormatter.formatBytes(e.compressedSize),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun FilesTab(
    result: ApkAnalysisResult,
    uiState: ApkAnalyzerUiState,
    onEvent: (ApkAnalyzerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Suchleiste
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { onEvent(ApkAnalyzerUiEvent.UpdateSearchQuery(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringRes(R.string.apk_analyzer_suche_dateien)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true
        )

        // Kategorie-Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.categoryFilter == FileCategoryFilter.ALL,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetCategoryFilter(FileCategoryFilter.ALL)) },
                label = { Text(stringRes(R.string.apk_analyzer_alle_dateien)) }
            )
            FilterChip(
                selected = uiState.categoryFilter == FileCategoryFilter.TOP_10,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetCategoryFilter(FileCategoryFilter.TOP_10)) },
                label = { Text(stringRes(R.string.apk_analyzer_top_dateien)) }
            )
            FilterChip(
                selected = uiState.categoryFilter == FileCategoryFilter.DEX,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetCategoryFilter(FileCategoryFilter.DEX)) },
                label = { Text(stringRes(R.string.apk_analyzer_abschnitt_dex)) }
            )
            FilterChip(
                selected = uiState.categoryFilter == FileCategoryFilter.RES,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetCategoryFilter(FileCategoryFilter.RES)) },
                label = { Text(stringRes(R.string.apk_analyzer_abschnitt_res)) }
            )
            FilterChip(
                selected = uiState.categoryFilter == FileCategoryFilter.LIB,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetCategoryFilter(FileCategoryFilter.LIB)) },
                label = { Text(stringRes(R.string.apk_analyzer_abschnitt_lib)) }
            )
            FilterChip(
                selected = uiState.categoryFilter == FileCategoryFilter.ASSETS,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetCategoryFilter(FileCategoryFilter.ASSETS)) },
                label = { Text(stringRes(R.string.apk_analyzer_abschnitt_assets)) }
            )
            FilterChip(
                selected = uiState.categoryFilter == FileCategoryFilter.META_INF,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetCategoryFilter(FileCategoryFilter.META_INF)) },
                label = { Text(stringRes(R.string.apk_analyzer_abschnitt_meta_inf)) }
            )
            FilterChip(
                selected = uiState.categoryFilter == FileCategoryFilter.OTHER,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetCategoryFilter(FileCategoryFilter.OTHER)) },
                label = { Text(stringRes(R.string.apk_analyzer_abschnitt_sonstiges)) }
            )
        }

        // Sortierung
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringRes(R.string.apk_analyzer_sortierung),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilterChip(
                selected = uiState.sortOrder == FileSortOrder.SIZE_COMPRESSED_DESC,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetSortOrder(FileSortOrder.SIZE_COMPRESSED_DESC)) },
                label = { Text(stringRes(R.string.apk_analyzer_sortierung_groesse_komp)) }
            )
            FilterChip(
                selected = uiState.sortOrder == FileSortOrder.SIZE_UNCOMPRESSED_DESC,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetSortOrder(FileSortOrder.SIZE_UNCOMPRESSED_DESC)) },
                label = { Text(stringRes(R.string.apk_analyzer_sortierung_groesse_unkomp)) }
            )
            FilterChip(
                selected = uiState.sortOrder == FileSortOrder.NAME_ASC,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetSortOrder(FileSortOrder.NAME_ASC)) },
                label = { Text(stringRes(R.string.apk_analyzer_sortierung_name)) }
            )
        }

        val files = uiState.filteredFiles
        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringRes(R.string.apk_analyzer_keine_eintraege))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(files, key = { it.path }) { entry ->
                    val isExpanded = uiState.expandedPaths.contains(entry.path)
                    ApkEntryRow(
                        entry = entry,
                        isExpanded = isExpanded,
                        onToggle = { onEvent(ApkAnalyzerUiEvent.TogglePathExpanded(entry.path)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ApkEntryRow(
    entry: ApkEntryInfo,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(getSectionColor(entry.section))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = entry.path.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ApkReportFormatter.formatBytes(entry.compressedSize),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onToggle, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = entry.path,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${stringRes(R.string.apk_analyzer_unkomprimiert)}: ${ApkReportFormatter.formatBytes(entry.uncompressedSize)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "${stringRes(R.string.apk_analyzer_anteil)}: ${String.format(Locale.US, "%.1f%%", entry.compressionRatio * 100.0)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    entry.subCategory?.let { sub ->
                        Text(
                            text = "${stringRes(R.string.apk_analyzer_sortierung_bereich)}: $sub",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionsTab(
    result: ApkAnalysisResult,
    uiState: ApkAnalyzerUiState,
    onEvent: (ApkAnalyzerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val perms = uiState.filteredPermissions
    val allPerms = result.metadata?.permissions ?: emptyList()
    val dangerousCount = allPerms.count { it.isDangerous }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = uiState.permissionFilter == PermissionFilter.ALL,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetPermissionFilter(PermissionFilter.ALL)) },
                label = { Text("${stringRes(R.string.apk_analyzer_alle_dateien)} (${allPerms.size})") }
            )
            FilterChip(
                selected = uiState.permissionFilter == PermissionFilter.DANGEROUS_ONLY,
                onClick = { onEvent(ApkAnalyzerUiEvent.SetPermissionFilter(PermissionFilter.DANGEROUS_ONLY)) },
                label = { Text(stringRes(R.string.apk_analyzer_gefaehrliche_berechtigungen, dangerousCount)) }
            )
        }

        if (perms.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringRes(R.string.apk_analyzer_keine_berechtigungen))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(perms, key = { it.name }) { perm ->
                    PermissionRow(perm = perm)
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(perm: ApkPermission, modifier: Modifier = Modifier) {
    val containerColor = if (perm.isDangerous) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (perm.isDangerous) Icons.Default.Warning else Icons.Default.Security,
                contentDescription = null,
                tint = if (perm.isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = perm.name.substringAfterLast('.'),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = perm.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                perm.maxSdkVersion?.let { max ->
                    Text(
                        text = stringRes(R.string.apk_analyzer_max_sdk, max),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (perm.isDangerous) {
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringRes(R.string.apk_analyzer_gefaehrlich_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ComponentsTab(
    result: ApkAnalysisResult,
    modifier: Modifier = Modifier
) {
    val meta = result.metadata
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (meta == null) {
            Text(text = stringRes(R.string.apk_analyzer_keine_eintraege))
            return@Column
        }

        Text(
            text = stringRes(R.string.apk_analyzer_tab_komponenten),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        ComponentCard(
            title = stringRes(R.string.apk_analyzer_aktivitaeten),
            total = meta.components.activitiesCount,
            exported = meta.components.exportedActivitiesCount
        )
        ComponentCard(
            title = stringRes(R.string.apk_analyzer_dienste),
            total = meta.components.servicesCount,
            exported = meta.components.exportedServicesCount
        )
        ComponentCard(
            title = stringRes(R.string.apk_analyzer_empfaenger),
            total = meta.components.receiversCount,
            exported = meta.components.exportedReceiversCount
        )
        ComponentCard(
            title = stringRes(R.string.apk_analyzer_provider),
            total = meta.components.providersCount,
            exported = meta.components.exportedProvidersCount
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text(
            text = stringRes(R.string.apk_analyzer_sdk_versionen),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "${stringRes(R.string.apk_analyzer_min_sdk, meta.minSdk?.toString() ?: "-")}")
                Text(text = "${stringRes(R.string.apk_analyzer_target_sdk, meta.targetSdk?.toString() ?: "-")}")
                Text(text = "${stringRes(R.string.apk_analyzer_compile_sdk, meta.compileSdk?.toString() ?: "-")}")
                meta.isDebuggable?.let {
                    Text(
                        text = if (it) stringRes(R.string.apk_analyzer_warnung_debuggable) else "Debuggable: Nein",
                        color = if (it) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                meta.allowBackup?.let {
                    Text(
                        text = if (it) stringRes(R.string.apk_analyzer_warnung_allow_backup) else "Allow Backup: Nein",
                        color = if (it) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ComponentCard(title: String, total: Int, exported: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringRes(R.string.apk_analyzer_exportiert, exported, total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SignatureTab(
    result: ApkAnalysisResult,
    modifier: Modifier = Modifier
) {
    val meta = result.metadata
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (meta == null || !meta.hasApksigner) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringRes(R.string.apk_analyzer_signatur_apksigner_fehlt),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            return@Column
        }

        val sig = meta.signatureResult
        if (sig != null) {
            SignatureStatusCard(sig = sig, context = context)
        }
    }
}

@Composable
private fun SignatureStatusCard(sig: ApkVerificationResult, context: Context, modifier: Modifier = Modifier) {
    val isValid = sig.isVerified
    val statusColor = if (isValid) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    val statusContainer = if (isValid) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isValid) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isValid) stringRes(R.string.apk_analyzer_signatur_gueltig) else stringRes(R.string.apk_analyzer_signatur_ungueltig),
                    style = MaterialTheme.typography.titleSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringRes(R.string.apk_analyzer_schemes),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))

            SchemeRow("v1 Scheme (JAR)", sig.v1SchemeVerified)
            SchemeRow("v2 Scheme (APK Signature v2)", sig.v2SchemeVerified)
            SchemeRow("v3 Scheme (APK Signature v3)", sig.v3SchemeVerified)
            SchemeRow("v4 Scheme (APK Signature v4)", sig.v4SchemeVerified)

            if (sig.certificates.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = stringRes(R.string.apk_analyzer_fingerabdruecke),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                for (cert in sig.certificates) {
                    Text(text = cert.owner, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "SHA-256: ${cert.sha256Fingerprint}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val clip = ClipData.newPlainText("SHA-256", cert.sha256Fingerprint)
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SchemeRow(name: String, verified: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = name, style = MaterialTheme.typography.bodySmall)
        Icon(
            imageVector = if (verified) Icons.Default.Check else Icons.Default.Close,
            contentDescription = null,
            tint = if (verified) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun CompareTab(
    result: ApkAnalysisResult,
    uiState: ApkAnalyzerUiState,
    onEvent: (ApkAnalyzerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var comparePathInput by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.comparisonResult == null) {
            // Keine zweite APK gewählt
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringRes(R.string.apk_analyzer_vergleich_waehlen),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = comparePathInput,
                        onValueChange = { comparePathInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringRes(R.string.apk_analyzer_vergleich_apk)) },
                        placeholder = { Text("/pfad/zur/vergleich.apk") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { onEvent(ApkAnalyzerUiEvent.CompareWithApk(comparePathInput)) },
                        enabled = comparePathInput.isNotBlank() && !uiState.isComparing,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        if (uiState.isComparing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(Icons.AutoMirrored.Filled.CompareArrows, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringRes(R.string.apk_analyzer_tab_vergleich))
                    }
                }
            }
        } else {
            // Vergleichsergebnis vorhanden (35.3)
            val comp = uiState.comparisonResult
            ComparisonHeader(comp = comp, onClear = { onEvent(ApkAnalyzerUiEvent.ClearComparison) })

            // Filter Chips für Diffs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = uiState.diffFilter == DiffFilter.ALL,
                    onClick = { onEvent(ApkAnalyzerUiEvent.SetDiffFilter(DiffFilter.ALL)) },
                    label = { Text(stringRes(R.string.apk_analyzer_vergleich_alle, comp.entryDiffs.size)) }
                )
                FilterChip(
                    selected = uiState.diffFilter == DiffFilter.ADDED,
                    onClick = { onEvent(ApkAnalyzerUiEvent.SetDiffFilter(DiffFilter.ADDED)) },
                    label = { Text(stringRes(R.string.apk_analyzer_vergleich_neu, comp.addedCount)) }
                )
                FilterChip(
                    selected = uiState.diffFilter == DiffFilter.REMOVED,
                    onClick = { onEvent(ApkAnalyzerUiEvent.SetDiffFilter(DiffFilter.REMOVED)) },
                    label = { Text(stringRes(R.string.apk_analyzer_vergleich_entfernt, comp.removedCount)) }
                )
                FilterChip(
                    selected = uiState.diffFilter == DiffFilter.CHANGED,
                    onClick = { onEvent(ApkAnalyzerUiEvent.SetDiffFilter(DiffFilter.CHANGED)) },
                    label = { Text(stringRes(R.string.apk_analyzer_vergleich_groesser, comp.changedCount)) }
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.filteredDiffs, key = { it.path }) { diff ->
                    DiffRow(diff = diff)
                }
            }
        }
    }
}

@Composable
private fun ComparisonHeader(comp: ApkComparisonResult, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringRes(R.string.apk_analyzer_vergleich_differenz),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = null)
                }
            }

            val sign = if (comp.totalSizeDeltaBytes >= 0) "+" else ""
            val deltaColor = if (comp.totalSizeDeltaBytes > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)

            Text(
                text = "$sign${ApkReportFormatter.formatBytes(comp.totalSizeDeltaBytes)} ($sign${String.format(Locale.US, "%.2f%%", comp.totalSizeDeltaPercent)})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = deltaColor
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${stringRes(R.string.apk_analyzer_basis_apk)}: ${comp.baseFileName} (${ApkReportFormatter.formatBytes(comp.baseFileSizeBytes)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${stringRes(R.string.apk_analyzer_vergleich_apk)}: ${comp.comparedFileName} (${ApkReportFormatter.formatBytes(comp.comparedFileSizeBytes)})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiffRow(diff: ApkEntryDiff, modifier: Modifier = Modifier) {
    val (statusColor, statusIcon) = when (diff.diffType) {
        ApkEntryDiffType.ADDED -> Color(0xFF2E7D32) to Icons.Default.Add
        ApkEntryDiffType.REMOVED -> MaterialTheme.colorScheme.error to Icons.Default.Remove
        ApkEntryDiffType.INCREASED -> MaterialTheme.colorScheme.error to Icons.Default.ArrowUpward
        ApkEntryDiffType.DECREASED -> Color(0xFF2E7D32) to Icons.Default.ArrowDownward
        ApkEntryDiffType.UNCHANGED -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Default.Check
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = diff.path.substringAfterLast('/'),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = diff.path,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val sign = if (diff.deltaCompressedBytes >= 0) "+" else ""
            Text(
                text = "$sign${ApkReportFormatter.formatBytes(diff.deltaCompressedBytes)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
    }
}

@Composable
private fun getSectionLabel(section: ApkSection): String = when (section) {
    ApkSection.DEX -> stringRes(R.string.apk_analyzer_abschnitt_dex)
    ApkSection.RES -> stringRes(R.string.apk_analyzer_abschnitt_res)
    ApkSection.ARSC -> stringRes(R.string.apk_analyzer_abschnitt_arsc)
    ApkSection.LIB -> stringRes(R.string.apk_analyzer_abschnitt_lib)
    ApkSection.ASSETS -> stringRes(R.string.apk_analyzer_abschnitt_assets)
    ApkSection.META_INF -> stringRes(R.string.apk_analyzer_abschnitt_meta_inf)
    ApkSection.MANIFEST -> stringRes(R.string.apk_analyzer_abschnitt_manifest)
    ApkSection.OTHER -> stringRes(R.string.apk_analyzer_abschnitt_sonstiges)
}
