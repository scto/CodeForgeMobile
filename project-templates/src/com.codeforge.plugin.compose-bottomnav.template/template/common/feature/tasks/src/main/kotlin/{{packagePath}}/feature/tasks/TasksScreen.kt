package {{packageName}}.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import {{packageName}}.core.domain.model.Task

/** Route: verbindet ViewModel und zustandslosen Bildschirm. */
@Composable
fun TasksRoute(modifier: Modifier = Modifier, viewModel: TasksViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    TasksScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

/** Filter, Aufgabenliste mit Abhaken/Löschen und Dialog für neue Aufgaben. */
@Composable
fun TasksScreen(state: TasksUiState, onEvent: (TasksUiEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize()) {
        Column {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { onEvent(TasksUiEvent.FilterSelected(filter)) },
                        label = { Text(stringResource(filter.label())) }
                    )
                }
            }
            if (state.tasks.isEmpty()) {
                Text(
                    stringResource(R.string.tasks_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(32.dp).align(Alignment.CenterHorizontally)
                )
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskRow(task = task, onEvent = onEvent)
                        HorizontalDivider()
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { onEvent(TasksUiEvent.AddClicked) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.tasks_add))
        }
    }
    state.newTitle?.let { title -> NewTaskDialog(title = title, showError = state.showTitleError, onEvent = onEvent) }
}

@Composable
private fun TaskRow(task: Task, onEvent: (TasksUiEvent) -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        headlineContent = {
            Text(task.title, textDecoration = if (task.done) TextDecoration.LineThrough else null)
        },
        leadingContent = {
            Checkbox(checked = task.done, onCheckedChange = { onEvent(TasksUiEvent.DoneChanged(task.id, it)) })
        },
        trailingContent = {
            IconButton(onClick = { onEvent(TasksUiEvent.DeleteClicked(task.id)) }) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.tasks_delete))
            }
        },
        modifier = modifier
    )
}

@Composable
private fun NewTaskDialog(title: String, showError: Boolean, onEvent: (TasksUiEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(TasksUiEvent.DialogDismissed) },
        title = { Text(stringResource(R.string.tasks_new)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { onEvent(TasksUiEvent.TitleChanged(it)) },
                label = { Text(stringResource(R.string.tasks_title)) },
                isError = showError,
                supportingText = if (showError) { { Text(stringResource(R.string.tasks_title_missing)) } } else null,
                singleLine = true
            )
        },
        confirmButton = { TextButton(onClick = { onEvent(TasksUiEvent.SaveClicked) }) { Text(stringResource(R.string.tasks_save)) } },
        dismissButton = { TextButton(onClick = { onEvent(TasksUiEvent.DialogDismissed) }) { Text(stringResource(R.string.tasks_cancel)) } }
    )
}

private fun TaskFilter.label(): Int = when (this) {
    TaskFilter.ALL -> R.string.tasks_filter_all
    TaskFilter.OPEN -> R.string.tasks_filter_open
    TaskFilter.DONE -> R.string.tasks_filter_done
}

@Preview(showBackground = true)
@Composable
private fun TasksScreenPreview() {
    TasksScreen(state = TasksUiState(tasks = listOf(Task(1, "Einkaufen", false), Task(2, "Sport", true))), onEvent = {})
}
