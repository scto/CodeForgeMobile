// Modul: :feature:editor
package com.codeforge.feature.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codeforge.core.domain.ai.AiProposedChange
import com.codeforge.core.domain.model.DiffLineType
import com.codeforge.core.domain.model.UnifiedDiffParser
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes

private val ADDED_BG = Color(0x3329A329)
private val REMOVED_BG = Color(0x33D32F2F)
private val HUNK_BG = Color(0x224C8DFF)

@Composable
fun AiDiffDialog(
    proposedChange: AiProposedChange,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rawDiff = proposedChange.unifiedDiff
    val diffFiles = if (rawDiff != null) {
        UnifiedDiffParser.parse(rawDiff)
    } else {
        emptyList()
    }

    Dialog(
        onDismissRequest = onDiscard,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringRes(R.string.ai_diff_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = proposedChange.filePath,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDiscard) {
                        Icon(Icons.Default.Close, contentDescription = stringRes(R.string.common_schliessen))
                    }
                }

                // Diff body
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    if (diffFiles.isNotEmpty()) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            for (file in diffFiles) {
                                for (hunk in file.hunks) {
                                    item {
                                        Text(
                                            text = hunk.header,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp
                                            ),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(HUNK_BG)
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    items(hunk.lines) { line ->
                                        val bg = when (line.type) {
                                            DiffLineType.ADDED -> ADDED_BG
                                            DiffLineType.REMOVED -> REMOVED_BG
                                            else -> Color.Transparent
                                        }
                                        val prefix = when (line.type) {
                                            DiffLineType.ADDED -> "+ "
                                            DiffLineType.REMOVED -> "- "
                                            else -> "  "
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(bg)
                                                .padding(horizontal = 8.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = (line.newNumber ?: line.oldNumber ?: "").toString().padStart(4),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                modifier = Modifier.width(36.dp)
                                            )
                                            Text(
                                                text = prefix + line.text,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 11.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Display whole replacement preview
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            items(proposedChange.proposedContent.lines()) { line ->
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ADDED_BG)
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDiscard,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringRes(R.string.ai_diff_discard))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = onAccept,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringRes(R.string.ai_diff_accept))
                    }
                }
            }
        }
    }
}
