// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.GitConflictSide
import com.codeforge.core.domain.model.GitFileStatus
import com.codeforge.core.domain.model.GitRepoState
import com.codeforge.core.domain.model.GitStatusEntry
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes

@Composable
internal fun ChangesTab(state: GitUiState, onEvent: (GitUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val status = state.status
    val staged = status?.staged.orEmpty()
    val unstaged = status?.unstaged.orEmpty()
    val conflicts = status?.conflicts.orEmpty()

    LazyColumn(modifier = modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
        if (!state.rebasing) item(key = "commit") { CommitBox(state, staged.size, onEvent) }

        if (conflicts.isNotEmpty()) {
            item(key = "h-conflicts") { SectionHeader(Res.string(R.string.git_konflikte), conflicts.size, MaterialTheme.colorScheme.error) }
            items(conflicts, key = { "c-" + it.path }) { entry ->
                ChangeRow(entry, onEvent, actions = {
                    RowAction(Icons.Filled.Add, Res.string(R.string.git_als_geloest_vormerken)) { onEvent(GitUiEvent.Stage(listOf(entry.path))) }
                    FileMenu(entry, state, onEvent)
                })
            }
        }

        item(key = "h-staged") {
            SectionHeader(Res.string(R.string.git_vorgemerkt), staged.size) {
                if (staged.isNotEmpty()) TextButton(onClick = { onEvent(GitUiEvent.UnstageAll) }) { Text(stringRes(R.string.git_alle_zurueck)) }
            }
        }
        if (staged.isEmpty()) item(key = "e-staged") { EmptyHint(Res.string(R.string.git_nichts_vorgemerkt)) }
        items(staged, key = { "s-" + it.path }) { entry ->
            ChangeRow(entry, onEvent, actions = {
                RowAction(Icons.Filled.Remove, Res.string(R.string.git_zuruecknehmen)) { onEvent(GitUiEvent.Unstage(listOf(entry.path))) }
                FileMenu(entry, state, onEvent)
            })
        }

        item(key = "h-unstaged") {
            SectionHeader(Res.string(R.string.git_aenderungen), unstaged.size) {
                if (unstaged.isNotEmpty()) TextButton(onClick = { onEvent(GitUiEvent.StageAll) }) { Text(stringRes(R.string.git_alle_vormerken)) }
            }
        }
        if (unstaged.isEmpty()) item(key = "e-unstaged") { EmptyHint(if (staged.isEmpty() && conflicts.isEmpty()) Res.string(R.string.git_arbeitsverzeichnis_sauber) else Res.string(R.string.git_keine_weiteren_aenderungen)) }
        items(unstaged, key = { "u-" + it.path }) { entry ->
            ChangeRow(entry, onEvent, actions = {
                RowAction(Icons.Filled.Undo, Res.string(R.string.git_aenderungen_verwerfen)) { onEvent(GitUiEvent.DiscardRequested(listOf(entry.path))) }
                RowAction(Icons.Filled.Add, Res.string(R.string.git_vormerken)) { onEvent(GitUiEvent.Stage(listOf(entry.path))) }
                FileMenu(entry, state, onEvent)
            })
        }
    }
}

@Composable
private fun CommitBox(state: GitUiState, stagedCount: Int, onEvent: (GitUiEvent) -> Unit) {
    val operation = state.status?.state ?: GitRepoState.NORMAL
    val inOperation = operation.completesWithCommit
    val label = when (operation) {
        GitRepoState.MERGING -> "Merge-Nachricht"
        GitRepoState.CHERRY_PICKING -> "Cherry-Pick-Nachricht"
        GitRepoState.REVERTING -> "Revert-Nachricht"
        else -> if (state.amend) Res.string(R.string.git_neue_nachricht_amend) else "Commit-Nachricht"
    }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = state.commitMessage,
            onValueChange = { onEvent(GitUiEvent.CommitMessageChanged(it)) },
            label = { Text(label) },
            minLines = 2,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!inOperation && state.status?.hasCommits == true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(state.amend, { onEvent(GitUiEvent.AmendChanged(it)) }, enabled = state.busy == null)
                Column {
                    Text(stringRes(R.string.git_letzten_commit_aendern_amend), style = MaterialTheme.typography.bodyMedium)
                    val status = state.status
                    if (state.amend && status != null && status.upstream != null && status.ahead == 0) {
                        Text(
                            stringRes(R.string.git_achtung_dieser_commit_ist_vermutlich),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        Button(
            onClick = { onEvent(GitUiEvent.CommitClicked) },
            enabled = state.canCommit,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.DoneAll, null, Modifier.size(18.dp))
            Text(
                text = when {
                    inOperation -> Res.string(R.string.git_abschliessen_commit)
                    state.amend -> Res.string(R.string.git_commit_aendern_datei_neu, stagedCount, if (stagedCount == 1) "" else "en")
                    else -> Res.string(R.string.git_commit_datei, stagedCount, if (stagedCount == 1) "" else "en")
                },
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int, color: Color = MaterialTheme.colorScheme.primary, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$title ($count)",
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun ChangeRow(entry: GitStatusEntry, onEvent: (GitUiEvent) -> Unit, actions: @Composable () -> Unit) {
    val name = entry.path.substringAfterLast('/')
    val dir = entry.path.substringBeforeLast('/', "")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // Konflikte im Konflikt-Editor lösen, alles andere als Diff ansehen
                if (entry.status == GitFileStatus.CONFLICTING) onEvent(GitUiEvent.OpenConflict(entry.path))
                else onEvent(GitUiEvent.OpenDiff(entry.path, entry.staged))
            }
            .padding(start = 12.dp, end = 0.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(entry.status)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (dir.isNotEmpty()) Text(dir, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
private fun RowAction(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) { Icon(icon, description, Modifier.size(20.dp)) }
}

/** Überlauf-Menü je Datei: Öffnen, Verlauf, Blame, Ignorieren; bei Konflikten die Seitenwahl für die ganze Datei. */
@Composable
private fun FileMenu(entry: GitStatusEntry, state: GitUiState, onEvent: (GitUiEvent) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val rebase = state.status?.state == GitRepoState.REBASING
    val conflict = entry.status == GitFileStatus.CONFLICTING
    val untracked = entry.status == GitFileStatus.UNTRACKED
    val deleted = entry.status == GitFileStatus.DELETED
    Box {
        RowAction(Icons.Filled.MoreVert, Res.string(R.string.git_mehr)) { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            fun item(text: String, enabled: Boolean = true, event: GitUiEvent) =
                DropdownMenuItem(text = { Text(text) }, enabled = enabled, onClick = { open = false; onEvent(event) })
            if (conflict) {
                // Beim Rebase sind die Seiten vertauscht: „ours“ = Ziel-Branch, „theirs“ = der eigene Commit
                val ours = if (rebase) "Ziel-Branch-Version" else Res.string(R.string.git_aktuelle_version_head)
                val theirs = if (rebase) Res.string(R.string.git_eigene_version_commit) else Res.string(R.string.git_eingehende_version)
                item(ours, event = GitUiEvent.ResolveConflictFile(entry.path, GitConflictSide.OURS))
                item(theirs, event = GitUiEvent.ResolveConflictFile(entry.path, GitConflictSide.THEIRS))
                item("Im Editor öffnen", event = GitUiEvent.OpenFile(entry.path))
            } else {
                item("Im Editor öffnen", enabled = !deleted, event = GitUiEvent.OpenFile(entry.path))
            }
            if (!untracked) {
                item(Res.string(R.string.git_verlauf), event = GitUiEvent.OpenHistory(entry.path))
                item(Res.string(R.string.git_blame), enabled = !deleted && entry.status != GitFileStatus.ADDED, event = GitUiEvent.OpenBlame(entry.path))
            }
            if (untracked) item(Res.string(R.string.git_zu_gitignore_hinzufuegen), event = GitUiEvent.AddToGitignore(entry.path, untracked = true))
        }
    }
}

@Composable
internal fun StatusBadge(status: GitFileStatus) {
    val (letter, color) = when (status) {
        GitFileStatus.ADDED -> "A" to Color(0xFF2E9E4F)
        GitFileStatus.MODIFIED -> "M" to Color(0xFFD9A400)
        GitFileStatus.DELETED -> "D" to Color(0xFFD64545)
        GitFileStatus.UNTRACKED -> "U" to Color(0xFF3D8FD6)
        GitFileStatus.CONFLICTING -> "!" to Color(0xFFE5484D)
    }
    Surface(shape = RoundedCornerShape(4.dp), color = color.copy(alpha = 0.18f), modifier = Modifier.size(22.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text(letter, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}
