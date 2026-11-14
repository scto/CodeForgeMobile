/**
 * Modul: :feature:projectsettings
 * @author Thomas Schmid
 */
package com.codeforge.feature.projectsettings.projectinfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.CreationSource
import com.codeforge.core.domain.model.ProjectStats
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import com.codeforge.core.ui.SettingsTopBar
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

/** Route „Projektinfo“ für das geöffnete Projekt. */
@Composable
fun ProjectInfoRoute(
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    viewModel: ProjectInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    ProjectInfoScreen(state = state, onBack = onBack, onRefresh = viewModel::refresh, modifier = modifier)
}

/** Zustandslose Oberfläche von „Projektinfo“. */
@Composable
fun ProjectInfoScreen(
    state: ProjectInfoState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            SettingsTopBar(
                title = stringRes(R.string.projectinfo_title),
                onBack = onBack,
                actions = {
                    IconButton(onClick = onRefresh, enabled = state !is ProjectInfoState.Loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringRes(R.string.common_aktualisieren))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is ProjectInfoState.Loading -> Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        if (state.filesRead > 0) stringRes(R.string.projectinfo_reading, state.filesRead) else stringRes(R.string.projectinfo_analyzing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is ProjectInfoState.Failed -> Text(
                    state.message, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                is ProjectInfoState.Loaded -> StatsContent(state.stats)
            }
        }
    }
}

/** Formatierer für Zahlen und Datumsangaben der Anzeige. */
private class Formats(locale: Locale) {
    private val numbers = NumberFormat.getIntegerInstance(locale)
    private val dates = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    fun number(value: Long): String = numbers.format(value)
    fun date(millis: Long?): String = millis?.let { dates.format(Date(it)) } ?: "–"
}

@Composable
private fun StatsContent(s: ProjectStats, modifier: Modifier = Modifier) {
    val f = remember { Formats(Locale.getDefault()) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HeaderBlock(s)
        KeyTiles(s, f)
        LanguageChartSection(s, f)
        CompositionSection(s, f)
        ModulesSection(s, f)
        GeneralSection(s, f)
        GradleSection(s, f)
        GitSection(s, f)
        LargestFilesSection(s, f)
        Text(
            stringRes(R.string.projectinfo_analyzed_at, f.date(s.analyzedAtMillis)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderBlock(s: ProjectStats) {
    Block {
        Text(s.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(s.rootPath, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.StartEllipsis)
        if (s.projectKinds.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.projectKinds.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
            }
        }
        if (s.truncated) {
            Text(stringRes(R.string.projectinfo_truncated), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyTiles(s: ProjectStats, f: Formats) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2,
    ) {
        Tile(stringRes(R.string.projectinfo_language), s.primaryLanguage ?: "–", Modifier.weight(1f))
        Tile(stringRes(R.string.projectinfo_code_lines), f.number(s.codeLines), Modifier.weight(1f))
        Tile(stringRes(R.string.projectinfo_source_files), f.number(s.sourceFiles.toLong()), Modifier.weight(1f))
        Tile(stringRes(R.string.projectinfo_modules), f.number(s.modules.size.toLong()), Modifier.weight(1f))
    }
}

@Composable
private fun LanguageChartSection(s: ProjectStats, f: Formats) {
    val other = stringRes(R.string.projectinfo_other)
    val slices = remember(s, other) { toSlices(s.languages.map { it.language to it.codeLines }, max = 7, otherLabel = other) }
    if (slices.isEmpty()) return
    Section(stringRes(R.string.projectinfo_chart_languages)) {
        DonutChart(
            slices = slices,
            centerValue = compact(s.languages.sumOf { it.codeLines }),
            centerLabel = stringRes(R.string.projectinfo_lines_short),
            otherLabel = other,
            formatValue = f::number,
        )
        Text(stringRes(R.string.projectinfo_chart_languages_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CompositionSection(s: ProjectStats, f: Formats) {
    if (s.totalLines <= 0) return
    Section(stringRes(R.string.projectinfo_chart_composition)) {
        StackedBar(
            parts = listOf(
                Triple(stringRes(R.string.projectinfo_part_code), s.codeLines, SLICE_COLORS[0]),
                Triple(stringRes(R.string.projectinfo_part_comments), s.commentLines, SLICE_COLORS[2]),
                Triple(stringRes(R.string.projectinfo_part_blank), s.blankLines, OTHER_COLOR),
            ),
            formatValue = f::number,
        )
    }
}

@Composable
private fun ModulesSection(s: ProjectStats, f: Formats) {
    if (s.modules.size <= 1) return
    Section(stringRes(R.string.projectinfo_chart_modules)) {
        HorizontalBars(entries = s.modules.take(MAX_BARS).map { it.path to it.codeLines }, formatValue = f::number)
        if (s.modules.size > MAX_BARS) {
            Text(
                stringRes(R.string.projectinfo_more_modules, s.modules.size - MAX_BARS),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GeneralSection(s: ProjectStats, f: Formats) {
    val source = when (s.createdSource) {
        CreationSource.FIRST_COMMIT -> " · " + stringRes(R.string.projectinfo_created_commit)
        CreationSource.OLDEST_FILE -> " · " + stringRes(R.string.projectinfo_created_oldest)
        else -> ""
    }
    Section(stringRes(R.string.projectinfo_section_general)) {
        KeyValue(stringRes(R.string.projectinfo_build_system), s.buildSystems.joinToString().ifEmpty { "–" })
        KeyValue(stringRes(R.string.projectinfo_created), f.date(s.createdMillis) + source)
        KeyValue(stringRes(R.string.projectinfo_modified), f.date(s.lastModifiedMillis))
        KeyValue(stringRes(R.string.projectinfo_all_files), f.number(s.totalFiles.toLong()))
        KeyValue(stringRes(R.string.projectinfo_size), formatBytes(s.sizeBytes))
        KeyValue(stringRes(R.string.projectinfo_total_lines), f.number(s.totalLines))
        KeyValue(stringRes(R.string.projectinfo_compose), stringRes(if (s.usesCompose) R.string.projectinfo_yes else R.string.projectinfo_no))
    }
}

@Composable
private fun GradleSection(s: ProjectStats, f: Formats) {
    val rows = listOfNotNull(
        s.gradleWrapper?.let { stringRes(R.string.projectinfo_gradle_wrapper) to it },
        s.agpVersion?.let { stringRes(R.string.projectinfo_agp) to it },
        s.kotlinVersion?.let { stringRes(R.string.projectinfo_kotlin) to it },
        s.compileSdk?.let { "compileSdk" to it.toString() },
        s.targetSdk?.let { "targetSdk" to it.toString() },
        s.minSdk?.let { "minSdk" to it.toString() },
        s.dependencyCount?.let { stringRes(R.string.projectinfo_libraries) to f.number(it.toLong()) },
    )
    if (rows.isEmpty()) return
    Section(stringRes(R.string.projectinfo_section_gradle)) { rows.forEach { (k, v) -> KeyValue(k, v) } }
}

@Composable
private fun GitSection(s: ProjectStats, f: Formats) {
    val g = s.git ?: return
    Section(stringRes(R.string.projectinfo_section_git)) {
        KeyValue(stringRes(R.string.projectinfo_branch), g.branch ?: "–")
        KeyValue(stringRes(R.string.projectinfo_commits), f.number(g.commits.toLong()) + if (g.commitsCapped) "+" else "")
        KeyValue(stringRes(R.string.projectinfo_authors), f.number(g.authors.toLong()))
        KeyValue(stringRes(R.string.projectinfo_first_commit), f.date(g.firstCommitMillis))
        KeyValue(stringRes(R.string.projectinfo_last_commit), f.date(g.lastCommitMillis))
    }
}

@Composable
private fun LargestFilesSection(s: ProjectStats, f: Formats) {
    if (s.largestFiles.isEmpty()) return
    Section(stringRes(R.string.projectinfo_section_largest)) {
        s.largestFiles.forEach { file ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    file.relativePath, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                    maxLines = 1, overflow = TextOverflow.StartEllipsis, modifier = Modifier.weight(1f),
                )
                Text(
                    stringRes(R.string.projectinfo_lines_value, f.number(file.lines)),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

private const val MAX_BARS = 8

@Composable
private fun Block(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Block {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        HorizontalDivider()
        content()
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = modifier.widthIn(min = 120.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.45f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.55f))
    }
}

/** 275920 → „276k“, 1_250_000 → „1,3M“ (für die Ringmitte). */
internal fun compact(value: Long, locale: Locale = Locale.getDefault()): String = when {
    value < 10_000 -> NumberFormat.getIntegerInstance(locale).format(value)
    value < 1_000_000 -> "${Math.round(value / 1000.0)}k"
    else -> String.format(locale, "%.1fM", value / 1_000_000.0)
}

internal fun formatBytes(bytes: Long, locale: Locale = Locale.getDefault()): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024L * 1024 -> String.format(locale, "%.1f KiB", bytes / 1024.0)
    bytes < 1024L * 1024 * 1024 -> String.format(locale, "%.1f MiB", bytes / (1024.0 * 1024))
    else -> String.format(locale, "%.2f GiB", bytes / (1024.0 * 1024 * 1024))
}
