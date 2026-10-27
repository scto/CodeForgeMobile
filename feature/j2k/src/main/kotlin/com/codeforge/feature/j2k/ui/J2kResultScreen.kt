package com.codeforge.feature.j2k.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.codeforge.core.resources.stringRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.J2kFileResult
import com.codeforge.core.domain.model.J2kFileStatus
import com.codeforge.core.domain.model.J2kReport
import com.codeforge.core.resources.R
import com.codeforge.feature.j2k.J2kFilter
import com.codeforge.feature.j2k.J2kIntent
import com.codeforge.feature.j2k.J2kUiState

@Composable
fun J2kResultScreen(
    state: J2kUiState,
    onIntent: (J2kIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.showResultScreen || state.report == null) return

    val report = state.report

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onIntent(J2kIntent.RestoreBackup) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringRes(R.string.j2k_action_undo))
                }
                Button(
                    onClick = { onIntent(J2kIntent.DismissResult) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringRes(R.string.j2k_action_close))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringRes(R.string.j2k_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Disclaimer
            Text(
                text = stringRes(R.string.j2k_disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Summary Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringRes(
                            R.string.j2k_result_summary,
                            report.convertedJavaCount,
                            report.notesJavaCount,
                            report.skippedJavaCount
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Backup: ${report.backupPath}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(J2kFilter.values()) { filter ->
                    val label = when (filter) {
                        J2kFilter.ALL -> stringRes(R.string.j2k_filter_all)
                        J2kFilter.CONVERTED -> stringRes(R.string.j2k_filter_converted)
                        J2kFilter.WITH_NOTES -> stringRes(R.string.j2k_filter_notes)
                        J2kFilter.FAILED -> stringRes(R.string.j2k_filter_failed)
                    }
                    FilterChip(
                        selected = state.selectedFilter == filter,
                        onClick = { onIntent(J2kIntent.SetFilter(filter)) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // File list
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.filteredResults, key = { it.filePath }) { file ->
                    FileResultRow(
                        file = file,
                        onClick = { onIntent(J2kIntent.OpenFile(file.filePath)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileResultRow(
    file: J2kFileResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val (icon, tint) = when (file.status) {
                J2kFileStatus.CONVERTED -> Icons.Default.CheckCircle to Color(0xFF2E7D32)
                J2kFileStatus.CONVERTED_WITH_NOTES -> Icons.Default.Warning to Color(0xFFED6C02)
                J2kFileStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
                J2kFileStatus.SKIPPED -> Icons.Default.Info to MaterialTheme.colorScheme.outline
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.filePath,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (file.notes.isNotEmpty()) {
                    Text(
                        text = "${file.notes.size} Hinweise",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFED6C02)
                    )
                }
                file.errorMessage?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Preview(name = "Result Screen Light Preview")
@Composable
private fun J2kResultScreenPreview() {
    MaterialTheme {
        Surface {
            J2kResultScreen(
                state = J2kUiState(
                    showResultScreen = true,
                    report = J2kReport(
                        projectPath = "/sample",
                        timestampEpochMillis = 1700000000000L,
                        convertedJavaCount = 2,
                        notesJavaCount = 1,
                        skippedJavaCount = 0,
                        failedJavaCount = 0,
                        convertedGradleCount = 1,
                        notesGradleCount = 0,
                        skippedGradleCount = 0,
                        versionCatalogCreated = true,
                        backupPath = "/sample/Backup/backup.zip",
                        journalPath = "/sample/Backup/journal.json",
                        fileResults = listOf(
                            J2kFileResult(
                                filePath = "app/src/main/java/com/example/User.java",
                                status = J2kFileStatus.CONVERTED,
                                originalLines = 50,
                                convertedLines = 35
                            ),
                            J2kFileResult(
                                filePath = "app/src/main/java/com/example/Greeter.java",
                                status = J2kFileStatus.CONVERTED_WITH_NOTES,
                                originalLines = 80,
                                convertedLines = 75,
                                notes = listOf("TODO(j2k): Nullbarkeit prüfen")
                            )
                        )
                    )
                ),
                onIntent = {}
            )
        }
    }
}
