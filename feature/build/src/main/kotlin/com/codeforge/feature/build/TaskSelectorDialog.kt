/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSelectorDialog(
    currentTask: String,
    availableTasks: List<String>,
    onDismiss: () -> Unit,
    onTaskSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf(currentTask) }
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(query, availableTasks) {
        val q = query.trim()
        availableTasks.filter { q.isEmpty() || it.contains(q, ignoreCase = true) }.take(100)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringRes(R.string.build_task_auswaehlen),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        expanded = true
                    },
                    label = { Text(stringRes(R.string.build_projekt_tasks, availableTasks.size)) },
                    singleLine = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    filtered.forEach { task ->
                        DropdownMenuItem(
                            text = { Text(task, style = MaterialTheme.typography.bodyMedium) },
                            onClick = {
                                query = task
                                expanded = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onTaskSelected(query.trim().ifEmpty { currentTask })
                },
                enabled = query.isNotBlank()
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
