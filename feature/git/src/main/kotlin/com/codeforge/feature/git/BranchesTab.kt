// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.codeforge.core.domain.model.GitBranch
import com.codeforge.core.domain.model.GitTag
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes

@Composable
internal fun BranchesTab(state: GitUiState, onEvent: (GitUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val local = state.branches.filter { !it.isRemote }
    val remote = state.branches.filter { it.isRemote }
    val enabled = state.busy == null

    LazyColumn(modifier = modifier, contentPadding = PaddingValues(bottom = 24.dp)) {
        item(key = "remote-card") { RemoteCard(state, onEvent) }

        item(key = "new") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringRes(R.string.git_lokale_branches, local.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onEvent(GitUiEvent.NewBranchRequested()) }, enabled = enabled) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Text(stringRes(R.string.git_neu), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        items(local, key = { "l-" + it.name }) { b -> BranchRow(b, enabled, onEvent) }

        if (remote.isNotEmpty()) {
            item(key = "remote-h") { SectionTitle("Remote-Branches (${remote.size})") }
            items(remote, key = { "r-" + it.name }) { b -> BranchRow(b, enabled, onEvent) }
        }

        item(key = "tags-h") {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringRes(R.string.git_tags_count, state.tags.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { onEvent(GitUiEvent.NewTagRequested()) }, enabled = enabled && state.status?.hasCommits == true) {
                    Icon(Icons.Filled.Add, null, Modifier.size(16.dp))
                    Text(stringRes(R.string.git_tag), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        if (state.tags.isEmpty()) {
            item(key = "tags-empty") {
                Text(stringRes(R.string.git_keine_tags), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
        items(state.tags, key = { "t-" + it.name }) { t -> TagRow(t, enabled, state.remotes.isNotEmpty(), onEvent) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun RemoteCard(state: GitUiState, onEvent: (GitUiEvent) -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
        Column(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringRes(R.string.git_remote), style = MaterialTheme.typography.labelLarge)
                    if (state.remotes.isEmpty()) {
                        Text(stringRes(R.string.git_keiner_konfiguriert_zum_pushen_eine), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        state.remotes.forEach {
                            Text("${it.name}: ${it.url}", style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                IconButton(onClick = { onEvent(GitUiEvent.SetRemoteRequested) }) {
                    Icon(if (state.remotes.isEmpty()) Icons.Filled.Add else Icons.Filled.Edit, stringRes(R.string.git_remote_setzen))
                }
                if (state.remotes.isNotEmpty()) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringRes(R.string.git_mehr)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            state.remotes.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(stringRes(R.string.git_entfernen, r.name)) },
                                    onClick = { menu = false; onEvent(GitUiEvent.RemoveRemoteRequested(r.name)) },
                                )
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onEvent(GitUiEvent.PullRebaseChanged(!state.pullRebase)) }) {
                Checkbox(state.pullRebase, { onEvent(GitUiEvent.PullRebaseChanged(it)) })
                Text(stringRes(R.string.git_pull_mit_rebase_statt_merge), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun BranchRow(branch: GitBranch, enabled: Boolean, onEvent: (GitUiEvent) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !branch.isCurrent) { onEvent(GitUiEvent.Checkout(branch.name)) }
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (branch.isCurrent) Icon(Icons.Filled.Check, "aktuell", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        else Spacer(Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                branch.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (branch.isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(branch.shortHash, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.MoreVert, "Branch-Aktionen", Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                fun item(text: String, event: GitUiEvent) = DropdownMenuItem(text = { Text(text) }, onClick = { menu = false; onEvent(event) })
                if (!branch.isCurrent) {
                    item(Res.string(R.string.git_auschecken), GitUiEvent.Checkout(branch.name))
                    item(Res.string(R.string.git_in_aktuellen_branch_mergen), GitUiEvent.MergeRequested(branch.name))
                    item(Res.string(R.string.git_aktuellen_branch_darauf_rebasen), GitUiEvent.RebaseRequested(branch.name))
                }
                if (!branch.isRemote) {
                    item(Res.string(R.string.common_umbenennen), GitUiEvent.RenameBranchRequested(branch.name))
                    if (!branch.isCurrent) item(Res.string(R.string.common_loeschen), GitUiEvent.DeleteBranchRequested(branch.name))
                } else {
                    item(Res.string(R.string.git_auf_dem_remote_loeschen), GitUiEvent.DeleteRemoteBranchRequested(branch.name))
                }
            }
        }
    }
}

@Composable
private fun TagRow(tag: GitTag, enabled: Boolean, hasRemote: Boolean, onEvent: (GitUiEvent) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clickable { onEvent(GitUiEvent.OpenCommit(tag.commitHash)) }.padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Sell, null, Modifier.size(18.dp), tint = androidx.compose.ui.graphics.Color(0xFFD9A400))
        Column(Modifier.weight(1f)) {
            Text(tag.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                tag.shortHash + if (tag.annotated) stringRes(R.string.git_annotiert) + if (tag.message.isNotBlank()) ": ${tag.message.lineSequence().first()}" else "" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }, enabled = enabled, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.MoreVert, "Tag-Aktionen", Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringRes(R.string.git_commit_ansehen)) }, onClick = { menu = false; onEvent(GitUiEvent.OpenCommit(tag.commitHash)) })
                DropdownMenuItem(text = { Text(stringRes(R.string.git_auschecken_loser_head)) }, onClick = { menu = false; onEvent(GitUiEvent.CheckoutCommit(tag.commitHash)) })
                DropdownMenuItem(text = { Text(stringRes(R.string.git_pushen)) }, enabled = hasRemote, onClick = { menu = false; onEvent(GitUiEvent.PushTag(tag.name)) })
                DropdownMenuItem(text = { Text(stringRes(R.string.common_loeschen)) }, onClick = { menu = false; onEvent(GitUiEvent.DeleteTagRequested(tag.name)) })
            }
        }
    }
}
