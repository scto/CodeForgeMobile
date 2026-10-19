/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.codeforge.core.domain.model.BuildProblem
import com.codeforge.core.domain.model.ProblemSeverity
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import java.io.File

@Composable
fun ProblemsTab(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.problemFilter == ProblemFilter.ALL,
                onClick = { onIntent(BuildUiIntent.SelectProblemFilter(ProblemFilter.ALL)) },
                label = { Text(stringRes(R.string.build_filter_alle, state.problems.size)) }
            )
            FilterChip(
                selected = state.problemFilter == ProblemFilter.ERRORS_ONLY,
                onClick = { onIntent(BuildUiIntent.SelectProblemFilter(ProblemFilter.ERRORS_ONLY)) },
                label = { Text(stringRes(R.string.build_filter_fehler, state.errorCount)) }
            )
            FilterChip(
                selected = state.problemFilter == ProblemFilter.WARNINGS_ONLY,
                onClick = { onIntent(BuildUiIntent.SelectProblemFilter(ProblemFilter.WARNINGS_ONLY)) },
                label = { Text(stringRes(R.string.build_filter_warnungen, state.warningCount)) }
            )
        }

        if (state.filteredProblems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringRes(R.string.build_keine_probleme),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Gruppiert nach Datei (oder null für globale Build-Probleme)
            val groupedByFile = remember(state.filteredProblems) {
                state.filteredProblems.groupBy { it.path }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedByFile.forEach { (filePath, problems) ->
                    item(key = filePath ?: "general") {
                        FileProblemGroup(
                            filePath = filePath,
                            problems = problems,
                            onProblemClick = { onIntent(BuildUiIntent.ClickProblem(it)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileProblemGroup(
    filePath: String?,
    problems: List<BuildProblem>,
    onProblemClick: (BuildProblem) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // Header der Datei
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                val fileName = filePath?.let { File(it).name } ?: stringRes(R.string.build_datei_ohne_position)
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Badge(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(
                        text = "${problems.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Liste der Probleme in dieser Datei
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                problems.forEach { problem ->
                    ProblemItem(
                        problem = problem,
                        onClick = { onProblemClick(problem) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProblemItem(
    problem: BuildProblem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    val hasPosition = problem.path != null && problem.line != null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                if (hasPosition) {
                    onClick()
                } else {
                    isExpanded = !isExpanded
                }
            },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.small
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Severity Icon
                when (problem.severity) {
                    ProblemSeverity.ERROR -> Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = stringRes(R.string.common_fehler),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    ProblemSeverity.WARNING -> Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = Color(0xFFF57C00),
                        modifier = Modifier.size(16.dp)
                    )
                    ProblemSeverity.INFO -> Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                // Zeile / Spalte Badge (falls vorhanden)
                if (problem.line != null) {
                    val posText = if (problem.column != null) "${problem.line}:${problem.column}" else "${problem.line}"
                    Text(
                        text = posText,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Kurznachricht
                Text(
                    text = problem.message,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (isExpanded || !hasPosition) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = if (!hasPosition) FontFamily.Monospace else FontFamily.Default,
                    modifier = Modifier.weight(1f)
                )

                if (hasPosition) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
