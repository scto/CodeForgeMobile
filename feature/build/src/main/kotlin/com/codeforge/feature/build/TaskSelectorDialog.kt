/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Task
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

/** Passendes Symbol je Task anhand von Name/Gruppe. */
internal fun taskIcon(task: GradleTaskInfo): ImageVector {
    val key = "${task.name.substringAfterLast(':')} ${task.group}".lowercase()
    return when {
        "clean" in key -> Icons.Filled.CleaningServices
        "install" in key -> Icons.Filled.InstallMobile
        "bundle" in key -> Icons.Filled.Widgets
        "compile" in key -> Icons.Filled.Memory
        "generate" in key -> Icons.Filled.AutoAwesome
        "test" in key || "verification" in key || "lint" in key || "check" in key -> Icons.Filled.CheckCircle
        "assemble" in key || "build" in key -> Icons.Filled.Build
        else -> Icons.Filled.Task
    }
}

/** Filtert nach Name, Gruppe und Beschreibung (Teilstring, Groß-/Kleinschreibung egal). */
internal fun filterTasks(tasks: List<GradleTaskInfo>, query: String): List<GradleTaskInfo> {
    val q = query.trim()
    if (q.isEmpty()) return tasks
    return tasks.filter {
        it.name.contains(q, ignoreCase = true) ||
            it.group.contains(q, ignoreCase = true) ||
            it.description.contains(q, ignoreCase = true)
    }
}

/** Dialog zur Auswahl des Gradle-Tasks. */
@Composable
fun TaskSelectorDialog(
    currentTask: String,
    availableTasks: List<String>,
    onDismiss: () -> Unit,
    onTaskSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    taskInfos: List<GradleTaskInfo> = emptyList(),
    isLoading: Boolean = false,
) {
    val tasks = remember(taskInfos, availableTasks) {
        taskInfos.ifEmpty { availableTasks.map { GradleTaskInfo(it) } }
    }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(currentTask) }
    val filtered = remember(query, tasks) { filterTasks(tasks, query) }
    val otherLabel = stringRes(R.string.build_tasks_sonstige)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringRes(R.string.build_projekt_tasks, tasks.size),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringRes(R.string.build_tasks_suchen)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            stringRes(R.string.build_tasks_laden),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (filtered.isEmpty() && !isLoading) {
                    Text(
                        stringRes(R.string.build_tasks_keine_treffer),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it.name }) { task ->
                        TaskRow(
                            task = task,
                            otherLabel = otherLabel,
                            isSelected = task.name == selected,
                            onClick = { selected = task.name }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onTaskSelected(selected.trim().ifEmpty { currentTask }) },
                enabled = selected.isNotBlank()
            ) {
                Text(stringRes(R.string.build_starten))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringRes(R.string.common_abbrechen))
            }
        },
        modifier = modifier
    )
}

@Composable
private fun TaskRow(
    task: GradleTaskInfo,
    otherLabel: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = taskIcon(task),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = task.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = task.description.ifBlank { task.group.ifBlank { otherLabel } },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
