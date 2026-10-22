package com.codeforge.feature.editor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.Breakpoint
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import java.io.File

@Composable
fun EditBreakpointDialog(
    breakpoint: Breakpoint,
    onSave: (Breakpoint) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var condition by remember(breakpoint) { mutableStateOf(breakpoint.condition.orEmpty()) }
    var logMessage by remember(breakpoint) { mutableStateOf(breakpoint.logMessage.orEmpty()) }
    var enabled by remember(breakpoint) { mutableStateOf(breakpoint.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${stringRes(R.string.debug_breakpoint_edit)} (Zeile ${breakpoint.line})") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringRes(R.string.debug_breakpoint_enabled),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = condition,
                    onValueChange = { condition = it },
                    label = { Text(stringRes(R.string.debug_breakpoint_condition)) },
                    placeholder = { Text(stringRes(R.string.debug_breakpoint_condition_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = logMessage,
                    onValueChange = { logMessage = it },
                    label = { Text(stringRes(R.string.debug_breakpoint_log_message)) },
                    placeholder = { Text(stringRes(R.string.debug_breakpoint_log_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        breakpoint.copy(
                            condition = condition.trim().ifEmpty { null },
                            logMessage = logMessage.trim().ifEmpty { null },
                            enabled = enabled
                        )
                    )
                }
            ) {
                Text(stringRes(R.string.common_speichern))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { onDelete(breakpoint.id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringRes(R.string.common_loeschen))
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringRes(R.string.common_abbrechen))
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakpointsOverviewSheet(
    breakpointsByPath: Map<String, List<Breakpoint>>,
    onBreakpointClick: (String, Int) -> Unit,
    onDeleteBreakpoint: (String) -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringRes(R.string.debug_tab_breakpoints),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                if (breakpointsByPath.values.any { it.isNotEmpty() }) {
                    TextButton(
                        onClick = { showDeleteAllDialog = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringRes(R.string.debug_breakpoints_remove_all))
                    }
                }
            }

            if (breakpointsByPath.values.all { it.isEmpty() }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringRes(R.string.debug_breakpoints_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    breakpointsByPath.forEach { (path, bps) ->
                        if (bps.isNotEmpty()) {
                            item(key = "hdr_$path") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Text(
                                        text = File(path).name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            items(bps, key = { it.id }) { bp ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onBreakpointClick(bp.path, bp.line) },
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val dotColor = if (bp.enabled) Color(0xFFE53935) else MaterialTheme.colorScheme.outline
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                        )

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringRes(R.string.debug_breakpoint_line, bp.line),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (!bp.verified) {
                                                Text(
                                                    text = "(${stringRes(R.string.debug_breakpoint_unverified)})",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }

                                        IconButton(onClick = { onDeleteBreakpoint(bp.id) }) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = stringRes(R.string.common_loeschen),
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringRes(R.string.debug_breakpoints_remove_all)) },
            text = { Text(stringRes(R.string.debug_breakpoints_remove_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteAll()
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringRes(R.string.common_loeschen))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringRes(R.string.common_abbrechen))
                }
            }
        )
    }
}
