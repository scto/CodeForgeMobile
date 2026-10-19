/**
 * Modul: :feature:build
 * @author Thomas Schmid
 */
package com.codeforge.feature.build

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

@Composable
fun OutputTab(
    state: BuildUiState,
    onIntent: (BuildUiIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.outputLines.size, state.autoScroll) {
        if (state.autoScroll && state.filteredLines.isNotEmpty()) {
            listState.animateScrollToItem(state.filteredLines.size - 1)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Such- und Aktionsleiste
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { onIntent(BuildUiIntent.UpdateSearchQuery(it)) },
                placeholder = { Text(stringRes(R.string.build_suchen), fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringRes(R.string.build_suchen),
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onIntent(BuildUiIntent.UpdateSearchQuery("")) }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringRes(R.string.common_loeschen),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
            )

            // Auto-Scroll Toggle
            IconToggleButton(
                checked = state.autoScroll,
                onCheckedChange = { onIntent(BuildUiIntent.ToggleAutoScroll) }
            ) {
                Icon(
                    imageVector = Icons.Default.VerticalAlignBottom,
                    contentDescription = stringRes(R.string.build_auto_scroll),
                    tint = if (state.autoScroll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Kopieren
            IconButton(onClick = { onIntent(BuildUiIntent.CopyOutput) }) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringRes(R.string.build_log_kopieren),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Löschen
            IconButton(onClick = { onIntent(BuildUiIntent.ClearOutput) }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringRes(R.string.build_log_loeschen),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Konsolenausgabe
        if (state.filteredLines.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringRes(R.string.build_keine_ausgabe),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                itemsIndexed(state.filteredLines) { _, line ->
                    OutputLineItem(line = line)
                }
            }
        }
    }
}

@Composable
private fun OutputLineItem(
    line: String,
    modifier: Modifier = Modifier
) {
    val hScroll = rememberScrollState()

    val textColor = when {
        line.startsWith("> Task") -> MaterialTheme.colorScheme.primary
        line.contains("error:", ignoreCase = true) || line.startsWith("e:") || line.startsWith("ERROR:") || line.contains("BUILD FAILED") -> MaterialTheme.colorScheme.error
        line.contains("warning:", ignoreCase = true) || line.startsWith("w:") -> Color(0xFFF57C00) // Orange/Warning
        line.contains("BUILD SUCCESSFUL") -> Color(0xFF2E7D32) // Grün
        else -> MaterialTheme.colorScheme.onSurface
    }

    val isBold = line.startsWith("> Task") || line.contains("BUILD ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(hScroll)
            .padding(vertical = 1.dp)
    ) {
        Text(
            text = line,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            fontWeight = if (isBold) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor
        )
    }
}
