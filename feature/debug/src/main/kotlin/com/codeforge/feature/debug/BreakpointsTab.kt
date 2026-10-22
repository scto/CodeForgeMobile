package com.codeforge.feature.debug

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
fun BreakpointsTab(
    state: DebugUiState,
    onIntent: (DebugUiIntent) -> Unit,
    onNavigateToSource: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteAllDialog by remember { mutableStateOf(false) }

    val allBreakpoints = state.allBreakpoints.flatMap { it.value }

    if (allBreakpoints.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringRes(R.string.debug_breakpoints_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Toolbar oben: "Alle entfernen"
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(
                onClick = { showDeleteAllDialog = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
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

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            state.allBreakpoints.forEach { (path, bps) ->
                if (bps.isNotEmpty()) {
                    item(key = "header_$path") {
                        val fileName = File(path).name
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = fileName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }

                    items(bps, key = { it.id }) { bp ->
                        BreakpointRow(
                            breakpoint = bp,
                            onClick = { onNavigateToSource(bp.path, bp.line) },
                            onDelete = { onIntent(DebugUiIntent.RemoveBreakpoint(bp.id)) }
                        )
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
                        onIntent(DebugUiIntent.RemoveAllBreakpoints)
                        showDeleteAllDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
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

@Composable
private fun BreakpointRow(
    breakpoint: Breakpoint,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Roter Punkt oder Hohlkreis wenn unverified
            val dotColor = if (breakpoint.enabled) Color(0xFFE53935) else MaterialTheme.colorScheme.outline
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .then(
                        if (breakpoint.verified || !breakpoint.enabled) {
                            Modifier.padding(0.dp)
                        } else {
                            Modifier
                        }
                    )
            )

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringRes(R.string.debug_breakpoint_line, breakpoint.line),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (!breakpoint.verified) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "(${stringRes(R.string.debug_breakpoint_unverified)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                if (!breakpoint.condition.isNullOrBlank()) {
                    Text(
                        text = "${stringRes(R.string.debug_breakpoint_condition)}: ${breakpoint.condition}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!breakpoint.logMessage.isNullOrBlank()) {
                    Text(
                        text = "${stringRes(R.string.debug_breakpoint_log_message)}: ${breakpoint.logMessage}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onDelete) {
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
