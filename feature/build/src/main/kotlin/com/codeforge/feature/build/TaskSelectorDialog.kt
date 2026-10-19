/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@Composable
fun TaskSelectorDialog(
    currentTask: String,
    availableTasks: List<String>,
    onDismiss: () -> Unit,
    onTaskSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTask by remember { mutableStateOf(currentTask) }
    var customTask by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(!availableTasks.contains(currentTask)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringRes(R.string.build_task_auswaehlen),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                availableTasks.forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isCustomSelected = false
                                selectedTask = task
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !isCustomSelected && selectedTask == task,
                            onClick = {
                                isCustomSelected = false
                                selectedTask = task
                            }
                        )
                        Text(
                            text = task,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCustomSelected = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isCustomSelected,
                        onClick = { isCustomSelected = true }
                    )
                    Text(
                        text = stringRes(R.string.build_benutzerdefinierter_task),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                if (isCustomSelected) {
                    OutlinedTextField(
                        value = customTask,
                        onValueChange = { customTask = it },
                        placeholder = { Text(stringRes(R.string.build_benutzerdefinierter_task)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val taskToRun = if (isCustomSelected && customTask.isNotBlank()) {
                        customTask.trim()
                    } else {
                        selectedTask
                    }
                    onTaskSelected(taskToRun)
                }
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
