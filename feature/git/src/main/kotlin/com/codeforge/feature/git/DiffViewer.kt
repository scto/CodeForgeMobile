// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codeforge.core.domain.model.DiffFile
import com.codeforge.core.domain.model.DiffLine
import com.codeforge.core.domain.model.DiffLineType
import com.codeforge.core.domain.model.GitHunkAction
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes

private val ADDED_BG = Color(0x3329A329)
private val REMOVED_BG = Color(0x33D32F2F)
private val HUNK_BG = Color(0x224C8DFF)

/**
 * Vollbild-Diff (zeilenweise, mit Zeilennummern, Hunk-Köpfen und +/- Zusammenfassung).
 * [onHunkAction] != `null` und [DiffViewState.editable]: pro Hunk Vormerken/Zurücknehmen/Verwerfen.
 */
@Composable
internal fun DiffViewerDialog(
    diff: DiffViewState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onHunkAction: ((hunkIndex: Int, action: GitHunkAction) -> Unit)? = null,
) {
    var confirmDiscard by remember { mutableStateOf<Int?>(null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(diff.path.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (when {
                                diff.commit != null -> "Commit ${diff.commit.take(7)}"
                                diff.staged -> Res.string(R.string.git_vorgemerkt)
                                else -> Res.string(R.string.git_arbeitsverzeichnis)
                            }) + " · " + diff.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringRes(R.string.common_schliessen)) }
                }
                when {
                    diff.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    diff.error != null -> Text(diff.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    diff.files.isEmpty() -> Text(stringRes(R.string.git_keine_unterschiede), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> DiffBody(
                        files = diff.files,
                        staged = diff.staged,
                        onHunkAction = onHunkAction?.takeIf { diff.editable }?.let { callback ->
                            { index: Int, action: GitHunkAction ->
                                if (action == GitHunkAction.DISCARD) confirmDiscard = index else callback(index, action)
                            }
                        },
                    )
                }
            }
        }
    }
    confirmDiscard?.let { index ->
        AlertDialog(
            onDismissRequest = { confirmDiscard = null },
            title = { Text(stringRes(R.string.git_hunk_verwerfen)) },
            text = { Text(stringRes(R.string.git_diese_aenderung_wird_in_der)) },
            confirmButton = { TextButton(onClick = { confirmDiscard = null; onHunkAction?.invoke(index, GitHunkAction.DISCARD) }) { Text(stringRes(R.string.git_verwerfen)) } },
            dismissButton = { TextButton(onClick = { confirmDiscard = null }) { Text(stringRes(R.string.common_abbrechen)) } },
        )
    }
}

@Composable
private fun DiffBody(
    files: List<DiffFile>,
    staged: Boolean,
    onHunkAction: ((hunkIndex: Int, action: GitHunkAction) -> Unit)?,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        files.forEach { file ->
            item(key = "f-" + file.displayPath) {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(file.displayPath, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (file.isBinary) Text(stringRes(R.string.git_binaerdatei), style = MaterialTheme.typography.labelSmall)
                    else {
                        Text("+${file.added}", color = Color(0xFF2E9E4F), style = MaterialTheme.typography.labelMedium)
                        Text("  −${file.removed}", color = Color(0xFFD64545), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            file.hunks.forEachIndexed { hunkIndex, hunk ->
                item(key = "h-${file.displayPath}-$hunkIndex") {
                    Row(Modifier.fillMaxWidth().background(HUNK_BG).padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            hunk.header,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(vertical = 2.dp),
                        )
                        if (onHunkAction != null && !file.isBinary) {
                            if (staged) {
                                HunkButton(Icons.Filled.Remove, Res.string(R.string.git_hunk_zuruecknehmen)) { onHunkAction(hunkIndex, GitHunkAction.UNSTAGE) }
                            } else {
                                HunkButton(Icons.Filled.Undo, Res.string(R.string.git_hunk_verwerfen)) { onHunkAction(hunkIndex, GitHunkAction.DISCARD) }
                                HunkButton(Icons.Filled.Add, Res.string(R.string.git_hunk_vormerken)) { onHunkAction(hunkIndex, GitHunkAction.STAGE) }
                            }
                        }
                    }
                }
                items(hunk.lines.size, key = { "l-${file.displayPath}-$hunkIndex-$it" }) { i -> DiffLineRow(hunk.lines[i]) }
            }
        }
    }
}

@Composable
private fun HunkButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) { Icon(icon, description, Modifier.size(18.dp)) }
}

@Composable
private fun DiffLineRow(line: DiffLine) {
    val bg = when (line.type) {
        DiffLineType.ADDED -> ADDED_BG
        DiffLineType.REMOVED -> REMOVED_BG
        else -> Color.Transparent
    }
    val sign = when (line.type) {
        DiffLineType.ADDED -> "+"
        DiffLineType.REMOVED -> "−"
        DiffLineType.NO_NEWLINE -> "⏎"
        DiffLineType.CONTEXT -> " "
    }
    Row(Modifier.fillMaxWidth().background(bg), horizontalArrangement = Arrangement.Start) {
        LineNo(line.oldNumber)
        LineNo(line.newNumber)
        Text(
            text = "$sign ${line.text}",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun LineNo(n: Int?) {
    Text(
        text = n?.toString().orEmpty(),
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.width(34.dp).padding(end = 4.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.End,
    )
}
