// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.codeforge.core.domain.model.GitBlameLine
import com.codeforge.core.domain.model.GitChangeKind
import com.codeforge.core.domain.model.GitChangedFile
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import java.text.DateFormat
import java.util.Date

private fun fullScreen() = DialogProperties(usePlatformDefaultWidth = false)

/** Kopfzeile mit Titel und Schließen-Knopf für Vollbild-Dialoge. */
@Composable
private fun DialogHeader(title: String, subtitle: String?, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onClose) { Icon(Icons.Filled.Close, stringRes(R.string.common_schliessen)) }
    }
}

/** Commit-Details: Nachricht, Aktionen (Cherry-Pick, Revert, Reset, Branch, Tag, Checkout) und geänderte Dateien. */
@Composable
internal fun CommitDetailDialog(
    state: CommitDetailState,
    busy: Boolean,
    onEvent: (GitUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    Dialog(onDismissRequest = { onEvent(GitUiEvent.CloseCommit) }, properties = fullScreen()) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                val detail = state.detail
                DialogHeader(
                    title = detail?.info?.message ?: Res.string(R.string.git_commit_short, state.hash.take(7)),
                    subtitle = state.hash.take(7),
                    onClose = { onEvent(GitUiEvent.CloseCommit) },
                )
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.error != null || detail == null ->
                        Text(state.error ?: stringRes(R.string.git_commit_nicht_gefunden), color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    else -> {
                        val label = "${detail.info.shortHash} ${detail.info.message}"
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "${detail.info.authorName} <${detail.info.authorEmail}> · ${dateFormat.format(Date(detail.info.timestampEpochMillis))}",
                                style = MaterialTheme.typography.labelMedium,
                            )
                            if (detail.committerName != detail.info.authorName) {
                                Text(stringRes(R.string.git_committer, detail.committerName), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (detail.info.refs.isNotEmpty()) {
                                Text(detail.info.refs.joinToString("  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                (if (detail.isMerge) "Merge · " else "") + "Eltern: " + detail.info.parents.joinToString { it.take(7) }.ifEmpty { "–" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            detail.fullMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                        // Stash-Commits sind nur zum Ansehen da (keine Cherry-Pick/Reset-Aktionen)
                        if (!state.readOnly) {
                            Row(
                                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ActionChip("Cherry-Pick", Icons.Filled.ContentCopy, !busy && !detail.isMerge) { onEvent(GitUiEvent.CherryPick(state.hash)) }
                                ActionChip(Res.string(R.string.git_revert), Icons.Filled.Undo, !busy && !detail.isMerge) { onEvent(GitUiEvent.RevertRequested(state.hash, label)) }
                                ActionChip(Res.string(R.string.git_reset_hierhin), Icons.Filled.RestartAlt, !busy) { onEvent(GitUiEvent.ResetRequested(state.hash, label)) }
                                ActionChip(Res.string(R.string.git_branch_hier), Icons.Filled.CallSplit, !busy) { onEvent(GitUiEvent.NewBranchRequested(state.hash)) }
                                ActionChip(Res.string(R.string.git_tag_hier), Icons.Filled.Sell, !busy) { onEvent(GitUiEvent.NewTagRequested(state.hash)) }
                                ActionChip(Res.string(R.string.git_auschecken), Icons.Filled.CheckCircle, !busy) { onEvent(GitUiEvent.CheckoutCommit(state.hash)) }
                            }
                        }
                        HorizontalDivider(Modifier.padding(top = 8.dp))
                        Text(
                            stringRes(R.string.git_geaenderte_dateien, detail.files.size) + if (detail.isMerge) stringRes(R.string.git_gegen_ersten_elternteil) else "",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                        )
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(detail.files, key = { it.path }) { file -> ChangedFileRow(file) { onEvent(GitUiEvent.OpenCommitFile(file.path)) } }
                        }
                    }
                }
            }
        }
    }
    state.fileDiff?.let { DiffViewerDialog(diff = it, onClose = { onEvent(GitUiEvent.CloseCommitFile) }) }
}

@Composable
private fun ActionChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(16.dp)) },
    )
}

@Composable
private fun ChangedFileRow(file: GitChangedFile, onClick: () -> Unit) {
    val (letter, color) = when (file.kind) {
        GitChangeKind.ADDED -> "A" to Color(0xFF2E9E4F)
        GitChangeKind.DELETED -> "D" to Color(0xFFD64545)
        GitChangeKind.RENAMED -> "R" to Color(0xFF8A63D2)
        GitChangeKind.COPIED -> "C" to Color(0xFF8A63D2)
        GitChangeKind.MODIFIED -> "M" to Color(0xFFD9A400)
    }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp), color = color.copy(alpha = 0.18f), modifier = Modifier.size(22.dp)) {
            Box(contentAlignment = Alignment.Center) { Text(letter, color = color, style = MaterialTheme.typography.labelMedium) }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(file.path.substringAfterLast('/'), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val dir = file.path.substringBeforeLast('/', "")
            val sub = buildString {
                if (dir.isNotEmpty()) append(dir)
                if (file.oldPath != null) { if (isNotEmpty()) append("  ·  "); append("von ${file.oldPath}") }
            }
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Verlauf einer Datei: Commits, die sie verändert haben; Tippen öffnet den Commit. */
@Composable
internal fun FileHistoryDialog(state: HistoryState, onEvent: (GitUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
    Dialog(onDismissRequest = { onEvent(GitUiEvent.CloseHistory) }, properties = fullScreen()) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                DialogHeader(Res.string(R.string.git_verlauf_2, state.path.substringAfterLast('/')), state.path, onClose = { onEvent(GitUiEvent.CloseHistory) })
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    state.commits.isEmpty() -> Text(stringRes(R.string.git_diese_datei_hat_noch_keine), modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(state.commits, key = { it.hash }) { c ->
                            Column(Modifier.fillMaxWidth().clickable { onEvent(GitUiEvent.OpenCommit(c.hash)) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(c.message, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${c.shortHash} · ${c.authorName} · ${dateFormat.format(Date(c.timestampEpochMillis))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val BLAME_COLORS = listOf(
    Color(0xFF4C8DFF), Color(0xFF2E9E4F), Color(0xFFE07B39), Color(0xFFB45EE5),
    Color(0xFFD64545), Color(0xFF1FB5B5), Color(0xFFD9A400), Color(0xFF8A8F98),
)

/** Blame: je Zeile Commit, Autor und Datum (Stand: letzter Commit); Tippen auf die Zeile öffnet den Commit. */
@Composable
internal fun BlameDialog(state: BlameState, onEvent: (GitUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val dateFormat = DateFormat.getDateInstance(DateFormat.SHORT)
    Dialog(onDismissRequest = { onEvent(GitUiEvent.CloseBlame) }, properties = fullScreen()) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize()) {
                DialogHeader(Res.string(R.string.git_blame_2, state.path.substringAfterLast('/')), Res.string(R.string.git_stand_des_letzten_commits, state.path), onClose = { onEvent(GitUiEvent.CloseBlame) })
                when {
                    state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.error != null -> Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
                    state.lines.isEmpty() -> Text(stringRes(R.string.git_leere_datei), modifier = Modifier.padding(16.dp))
                    else -> {
                        val colorFor = remember(state.lines) {
                            state.lines.map { it.commitHash }.distinct().withIndex().associate { (i, h) -> h to BLAME_COLORS[i % BLAME_COLORS.size] }
                        }
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(state.lines.size, key = { it }) { i ->
                                val line = state.lines[i]
                                val showInfo = i == 0 || state.lines[i - 1].commitHash != line.commitHash
                                BlameRow(line, showInfo, colorFor[line.commitHash] ?: Color.Gray, dateFormat) { onEvent(GitUiEvent.OpenCommit(line.commitHash)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlameRow(line: GitBlameLine, showInfo: Boolean, color: Color, dateFormat: DateFormat, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = line.commitHash.isNotEmpty(), onClick = onClick), verticalAlignment = Alignment.Top) {
        Box(Modifier.background(color).size(width = 3.dp, height = 18.dp))
        Text(
            text = if (showInfo) "${line.shortHash} ${line.author.take(10).padEnd(10)} ${dateFormat.format(Date(line.timestampEpochMillis))}" else "",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.width(190.dp).padding(start = 4.dp, top = 3.dp),
        )
        Text(
            text = line.lineNumber.toString(),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(30.dp).padding(end = 4.dp, top = 3.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
        Text(
            text = line.text,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}
