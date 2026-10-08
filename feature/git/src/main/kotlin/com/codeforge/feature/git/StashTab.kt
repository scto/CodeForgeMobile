// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.GitStash
import com.codeforge.core.resources.R
import com.codeforge.core.resources.stringRes
import java.text.DateFormat
import java.util.Date

/** Stash-Liste: ablegen, anwenden, anwenden & löschen (Pop), löschen; Tippen zeigt den Inhalt. */
@Composable
internal fun StashTab(state: GitUiState, onEvent: (GitUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val enabled = state.busy == null
    val hasChanges = state.status?.entries?.isNotEmpty() == true
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "save") {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Button(
                    onClick = { onEvent(GitUiEvent.StashSaveRequested) },
                    enabled = enabled && hasChanges,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.MoveToInbox, null, Modifier.size(18.dp))
                    Text(stringRes(R.string.git_aenderungen_stashen), Modifier.padding(start = 8.dp))
                }
                Text(
                    if (hasChanges) stringRes(R.string.git_legt_alle_lokalen_aenderungen_inkl)
                    else stringRes(R.string.git_keine_lokalen_aenderungen_zum_stashen),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (state.stashes.isEmpty()) {
            item(key = "empty") {
                Text(stringRes(R.string.git_keine_stashes), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
            }
        }
        items(state.stashes, key = { it.hash }) { stash -> StashRow(stash, enabled, dateFormat, onEvent) }
    }
}

@Composable
private fun StashRow(stash: GitStash, enabled: Boolean, dateFormat: DateFormat, onEvent: (GitUiEvent) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onEvent(GitUiEvent.OpenCommit(stash.hash, readOnly = true)) }.padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Inventory2, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 10.dp, top = 6.dp, bottom = 6.dp)) {
            Text(stash.message.ifBlank { stash.ref }, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${stash.ref} · ${dateFormat.format(Date(stash.timestampEpochMillis))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { onEvent(GitUiEvent.StashApply(stash.index, drop = false)) }, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Unarchive, stringRes(R.string.git_anwenden_stash_behalten), Modifier.size(20.dp))
        }
        IconButton(onClick = { onEvent(GitUiEvent.StashApply(stash.index, drop = true)) }, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.MoveToInbox, stringRes(R.string.git_pop_anwenden_und_loeschen), Modifier.size(20.dp))
        }
        IconButton(onClick = { onEvent(GitUiEvent.StashDropRequested(stash)) }, enabled = enabled, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Filled.Delete, stringRes(R.string.git_stash_loeschen), Modifier.size(20.dp))
        }
    }
}
