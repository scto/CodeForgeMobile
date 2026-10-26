package com.codeforge.feature.x2c.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.UndoConflictPolicy
import com.codeforge.core.domain.model.X2cBuildProblem
import com.codeforge.core.domain.model.X2cFileResult
import com.codeforge.core.domain.model.X2cFileStatus
import com.codeforge.core.domain.model.X2cReport
import com.codeforge.core.resources.R
import com.codeforge.feature.x2c.X2cBuildBadge
import com.codeforge.feature.x2c.X2cIntent
import com.codeforge.feature.x2c.X2cResultFilter
import com.codeforge.feature.x2c.X2cUiState
import com.codeforge.feature.x2c.buildBadge
import com.codeforge.feature.x2c.buildErrors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun X2cResultScreen(
    state: X2cUiState,
    onIntent: (X2cIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val report = state.report
    if (!state.showResultScreen || report == null) return

    val badge = buildBadge(report, state.rerunBuildResult)
    val errors = buildErrors(report, state.rerunBuildResult)
    val busy = state.isRerunningBuild || state.isUndoing

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.x2c_result_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = stringResource(
                                R.string.x2c_result_summary,
                                report.generatedCount,
                                report.generatedWithNotesCount,
                                report.skippedCount,
                                report.failedCount
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (report.totalTodos > 0) {
                            Text(
                                text = stringResource(R.string.x2c_result_todos, report.totalTodos),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (report.addedDependencies.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.x2c_result_added_deps, report.addedDependencies.joinToString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                item { BuildCard(badge = badge, errors = errors, running = state.isRerunningBuild) }
                state.undoResult?.let { undo ->
                    item {
                        Text(
                            text = stringResource(
                                R.string.x2c_undo_summary,
                                undo.restored.size,
                                undo.deleted.size,
                                undo.keptByUser.size,
                                undo.savedAsOrig.size
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (state.isUndoing) {
                    item {
                        Column {
                            Text(stringResource(R.string.x2c_undo_running), style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { onIntent(X2cIntent.RunBuildAgain) }, enabled = !busy) {
                            Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.x2c_result_action_build))
                        }
                        OutlinedButton(
                            onClick = { onIntent(X2cIntent.OpenReport) },
                            enabled = report.reportPath != null
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.x2c_result_action_report))
                        }
                        OutlinedButton(onClick = { onIntent(X2cIntent.OpenFirstComposeFile) }) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.x2c_result_action_preview))
                        }
                        OutlinedButton(onClick = { onIntent(X2cIntent.ShowBackupFolder) }) {
                            Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.x2c_result_action_backup))
                        }
                        OutlinedButton(onClick = { onIntent(X2cIntent.RestoreBackup) }, enabled = !busy) {
                            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                            Text(stringResource(R.string.x2c_result_action_undo))
                        }
                    }
                }
                item {
                    Text(
                        text = stringResource(R.string.x2c_result_files),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        X2cResultFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = state.resultFilter == filter,
                                onClick = { onIntent(X2cIntent.SetResultFilter(filter)) },
                                label = { Text(stringResource(filterLabel(filter))) }
                            )
                        }
                    }
                }
                val files = state.filteredResults
                if (files.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.x2c_result_files_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(files, key = { it.sourceXmlPath }) { file ->
                        FileRow(file = file, onOpen = { onIntent(X2cIntent.OpenFile(it)) })
                    }
                }
                item {
                    TextButton(onClick = { onIntent(X2cIntent.DismissResult) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.x2c_result_close))
                    }
                }
            }
        }
    }

    state.undoConflictPath?.let { path ->
        UndoConflictDialog(path = path, onResolve = { onIntent(X2cIntent.ResolveUndoConflict(it)) })
    }
}

private fun filterLabel(filter: X2cResultFilter): Int = when (filter) {
    X2cResultFilter.ALL -> R.string.x2c_result_filter_all
    X2cResultFilter.PROBLEMS -> R.string.x2c_result_filter_problems
    X2cResultFilter.TODOS -> R.string.x2c_result_filter_todos
}

@Composable
private fun BuildCard(
    badge: X2cBuildBadge,
    errors: List<X2cBuildProblem>,
    running: Boolean,
    modifier: Modifier = Modifier
) {
    val (container, content) = when (badge) {
        X2cBuildBadge.SUCCESS -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        X2cBuildBadge.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(
                    when {
                        running -> R.string.x2c_result_build_running
                        badge == X2cBuildBadge.SUCCESS -> R.string.x2c_result_build_ok
                        badge == X2cBuildBadge.FAILED -> R.string.x2c_result_build_failed
                        badge == X2cBuildBadge.SKIPPED -> R.string.x2c_result_build_skipped
                        else -> R.string.x2c_result_build_not_run
                    }
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (running) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            if (errors.isNotEmpty()) {
                Text(stringResource(R.string.x2c_result_build_problems, errors.size), style = MaterialTheme.typography.labelMedium)
                errors.forEach { problem ->
                    val where = listOfNotNull(problem.path, problem.line?.toString()).joinToString(":")
                    val suffix = if (problem.preExisting) " (${stringResource(R.string.x2c_result_build_pre_existing)})" else ""
                    Text(
                        text = (if (where.isNotEmpty()) "$where  " else "") + problem.message + suffix,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FileRow(
    file: X2cFileResult,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val target = file.outputComposePath ?: file.sourceXmlPath
    val name = target.substringAfterLast('/')
    val openLabel = stringResource(R.string.x2c_result_open_file, name)
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = openLabel, role = Role.Button) { onOpen(target) }
            .semantics { contentDescription = openLabel }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(statusLabel(file.status)),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (file.status == X2cFileStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = file.sourceXmlPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (file.todoCount > 0) {
                Text(stringResource(R.string.x2c_result_todo_count, file.todoCount), style = MaterialTheme.typography.labelSmall)
            }
            file.notes.take(3).forEach { note ->
                Text(note, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            file.errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun statusLabel(status: X2cFileStatus): Int = when (status) {
    X2cFileStatus.GENERATED -> R.string.x2c_result_status_generated
    X2cFileStatus.GENERATED_WITH_NOTES -> R.string.x2c_result_status_notes
    X2cFileStatus.SKIPPED -> R.string.x2c_result_status_skipped
    X2cFileStatus.FAILED -> R.string.x2c_result_status_failed
}

/** Lässt sich nicht wegwischen: ohne Antwort bleibt „Rückgängig“ stehen, und die Datei bliebe in unklarem Zustand. */
@Composable
private fun UndoConflictDialog(
    path: String,
    onResolve: (UndoConflictPolicy) -> Unit
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.x2c_undo_conflict_title)) },
        text = { Text(stringResource(R.string.x2c_undo_conflict_message, path)) },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onResolve(UndoConflictPolicy.KEEP) }) { Text(stringResource(R.string.x2c_undo_keep)) }
                TextButton(onClick = { onResolve(UndoConflictPolicy.SAVE_AS_ORIG) }) { Text(stringResource(R.string.x2c_undo_save_orig)) }
                TextButton(onClick = { onResolve(UndoConflictPolicy.OVERWRITE) }) { Text(stringResource(R.string.x2c_undo_overwrite)) }
            }
        }
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable
private fun X2cResultScreenPreview() {
    val report = X2cReport(
        projectPath = "/project",
        timestampEpochMillis = 0L,
        generatedCount = 2,
        generatedWithNotesCount = 1,
        skippedCount = 0,
        failedCount = 1,
        totalTodos = 3,
        stage2WiredCount = 0,
        stage2SkippedCount = 0,
        themeGenerated = true,
        removedXmlCount = 0,
        backupPath = "/project/Backup/backup.zip",
        journalPath = "/project/Backup/journal.json",
        fileResults = listOf(
            X2cFileResult("res/layout/activity_main.xml", "ui/MainScreen.kt", X2cFileStatus.GENERATED),
            X2cFileResult("res/layout/item_user.xml", "ui/UserItemScreen.kt", X2cFileStatus.GENERATED_WITH_NOTES, todoCount = 3, notes = listOf("Custom View ersetzt")),
            X2cFileResult("res/layout/broken.xml", null, X2cFileStatus.FAILED, errorMessage = "Ungültiges XML")
        ),
        buildVerificationSuccess = true,
        reportPath = "/project/Backup/x2c-report.md"
    )
    MaterialTheme {
        X2cResultScreen(
            state = X2cUiState(report = report, showResultScreen = true),
            onIntent = {}
        )
    }
}
