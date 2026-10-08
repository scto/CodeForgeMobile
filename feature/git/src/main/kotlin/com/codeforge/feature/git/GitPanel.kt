// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.GitRepoState
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import kotlinx.coroutines.flow.collectLatest

/** Vollbild-Variante (Route `git/{repoPath}`), z. B. aus Deep-Links. Im Workspace wird [GitPanel] im Drawer genutzt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitRoute(
    repoPath: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringRes(R.string.common_git)) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringRes(R.string.git_zurueck))
                    }
                },
            )
        },
    ) { padding ->
        GitPanel(repoPath = repoPath, onOpenSettings = onOpenSettings, modifier = Modifier.padding(padding))
    }
}

/**
 * Git-Panel für den Workspace-Drawer: Status/Änderungen, Graph, Branches. Eigenständig (eigener ViewModel,
 * eigene Snackbar), damit es in jeden Container passt.
 */
@Composable
fun GitPanel(
    repoPath: String,
    modifier: Modifier = Modifier,
    onFileOpened: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: GitViewModel = hiltViewModel(),
) {
    LaunchedEffect(repoPath) { viewModel.initialize(repoPath) }
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is GitUiEffect.ShowSnackbar -> snackbar.showSnackbar(effect.message)
                GitUiEffect.FileOpened -> onFileOpened()
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        GitContent(state = state, onEvent = viewModel::onEvent, onOpenSettings = onOpenSettings, modifier = Modifier.fillMaxSize())
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // Reihenfolge = Z-Reihenfolge: spätere Dialoge liegen oben (Commit-Details über Verlauf/Blame, Bestätigungen ganz oben)
    state.diff?.let { diff ->
        DiffViewerDialog(
            diff = diff,
            onClose = { viewModel.onEvent(GitUiEvent.CloseDiff) },
            onHunkAction = { index, action -> viewModel.onEvent(GitUiEvent.HunkAction(diff.path, diff.staged, index, action)) },
        )
    }
    state.conflict?.let { ConflictEditorDialog(it, busy = state.busy != null, onEvent = viewModel::onEvent) }
    state.history?.let { FileHistoryDialog(it, onEvent = viewModel::onEvent) }
    state.blame?.let { BlameDialog(it, onEvent = viewModel::onEvent) }
    state.commitDetail?.let { CommitDetailDialog(it, busy = state.busy != null, onEvent = viewModel::onEvent) }
    GitDialogs(state = state, onEvent = viewModel::onEvent)
}

@Composable
private fun GitContent(
    state: GitUiState,
    onEvent: (GitUiEvent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.isRepository) {
        null -> Box(modifier, contentAlignment = Alignment.Center) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(24.dp)) }
        false -> NotARepository(state, onEvent, modifier)
        true -> Column(modifier) {
            GitHeader(state, onEvent, onOpenSettings)
            if (state.busy != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(state.busy, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
            }
            OperationBanner(state, onEvent)
            if (state.identityMissing) IdentityBanner(onOpenSettings)

            ScrollableTabRow(selectedTabIndex = state.tab.ordinal, edgePadding = 0.dp) {
                Tab(selected = state.tab == GitTab.CHANGES, onClick = { onEvent(GitUiEvent.TabSelected(GitTab.CHANGES)) },
                    icon = { Icon(Icons.Filled.Difference, null, Modifier.size(20.dp)) },
                    text = { Text(stringRes(R.string.git_aenderungen) + (state.status?.entries?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: ""), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                Tab(selected = state.tab == GitTab.GRAPH, onClick = { onEvent(GitUiEvent.TabSelected(GitTab.GRAPH)) },
                    icon = { Icon(Icons.Filled.AccountTree, null, Modifier.size(20.dp)) }, text = { Text(stringRes(R.string.git_graph)) })
                Tab(selected = state.tab == GitTab.BRANCHES, onClick = { onEvent(GitUiEvent.TabSelected(GitTab.BRANCHES)) },
                    icon = { Icon(Icons.Filled.CallSplit, null, Modifier.size(20.dp)) }, text = { Text(stringRes(R.string.git_branches)) })
                Tab(selected = state.tab == GitTab.STASH, onClick = { onEvent(GitUiEvent.TabSelected(GitTab.STASH)) },
                    icon = { Icon(Icons.Filled.Inventory2, null, Modifier.size(20.dp)) },
                    text = { Text(stringRes(R.string.git_stash) + (state.stashes.size.takeIf { it > 0 }?.let { " ($it)" } ?: "")) })
            }
            when (state.tab) {
                GitTab.CHANGES -> ChangesTab(state, onEvent, Modifier.fillMaxSize())
                GitTab.GRAPH -> GraphTab(state, onEvent, Modifier.fillMaxSize())
                GitTab.BRANCHES -> BranchesTab(state, onEvent, Modifier.fillMaxSize())
                GitTab.STASH -> StashTab(state, onEvent, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun GitHeader(state: GitUiState, onEvent: (GitUiEvent) -> Unit, onOpenSettings: () -> Unit) {
    val status = state.status
    val enabled = state.busy == null
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = status?.branch ?: if (status?.detachedHead == true) stringRes(R.string.git_head_losgeloest) else stringRes(R.string.git_noch_kein_commit),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val upstream = status?.upstream
                Text(
                    text = upstream ?: stringRes(R.string.git_kein_upstream),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (status != null && upstream != null) {
                    Icon(Icons.Filled.ArrowUpward, null, Modifier.size(12.dp).padding(start = 4.dp))
                    Text("${status.ahead}", style = MaterialTheme.typography.labelSmall)
                    Icon(Icons.Filled.ArrowDownward, null, Modifier.size(12.dp).padding(start = 4.dp))
                    Text("${status.behind}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        IconButton(onClick = { onEvent(GitUiEvent.FetchClicked) }, enabled = enabled) { Icon(Icons.Filled.Sync, stringRes(R.string.git_fetch)) }
        IconButton(onClick = { onEvent(GitUiEvent.PullClicked) }, enabled = enabled) { Icon(Icons.Filled.CloudDownload, stringRes(R.string.git_pull)) }
        IconButton(onClick = { onEvent(GitUiEvent.PushClicked) }, enabled = enabled) { Icon(Icons.Filled.CloudUpload, stringRes(R.string.git_push)) }
        IconButton(onClick = { onEvent(GitUiEvent.Refresh) }) { Icon(Icons.Filled.Refresh, stringRes(R.string.common_aktualisieren)) }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Git-Einstellungen") }
    }
}

/** Hinweis auf eine laufende Mehrschritt-Operation (Merge, Rebase, Cherry-Pick, Revert) mit passenden Aktionen. */
@Composable
private fun OperationBanner(state: GitUiState, onEvent: (GitUiEvent) -> Unit) {
    val status = state.status ?: return
    val operation = status.state
    if (operation == GitRepoState.NORMAL) return
    val conflicts = status.conflicts.size
    val enabled = state.busy == null
    val (title, hint) = when (operation) {
        GitRepoState.MERGING -> Res.string(R.string.git_merge_laeuft) to
            if (conflicts > 0) Res.string(R.string.git_konflikt_loesen_vormerken_committen, conflicts) else Res.string(R.string.git_alle_konflikte_geloest_merge_commit)
        GitRepoState.REBASING -> Res.string(R.string.git_rebase_laeuft) to
            if (conflicts > 0) Res.string(R.string.git_konflikt_loesen_und_vormerken_dann, conflicts) else Res.string(R.string.git_konflikte_geloest_fortsetzen_oder_comm)
        GitRepoState.CHERRY_PICKING -> Res.string(R.string.git_cherry_pick_laeuft) to
            if (conflicts > 0) Res.string(R.string.git_konflikt_loesen_vormerken_committen, conflicts) else Res.string(R.string.git_konflikte_geloest_commit_erstellen)
        GitRepoState.REVERTING -> Res.string(R.string.git_revert_laeuft) to
            if (conflicts > 0) Res.string(R.string.git_konflikt_loesen_vormerken_committen, conflicts) else Res.string(R.string.git_konflikte_geloest_commit_erstellen)
        else -> Res.string(R.string.git_git_vorgang_laeuft) to Res.string(R.string.git_zustand_wird_von_der_app)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, null)
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    Text(hint, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (operation == GitRepoState.REBASING) {
                    OutlinedButton(onClick = { onEvent(GitUiEvent.RebaseSkip) }, enabled = enabled) { Text(stringRes(R.string.common_ueberspringen)) }
                    Button(onClick = { onEvent(GitUiEvent.RebaseContinue) }, enabled = enabled && conflicts == 0, modifier = Modifier.padding(horizontal = 6.dp)) { Text(stringRes(R.string.git_fortsetzen)) }
                    OutlinedButton(onClick = { onEvent(GitUiEvent.RebaseAbortRequested) }, enabled = enabled) { Text(stringRes(R.string.common_abbrechen)) }
                } else {
                    OutlinedButton(onClick = { onEvent(GitUiEvent.AbortMergeRequested) }, enabled = enabled) { Text(stringRes(R.string.common_abbrechen)) }
                }
            }
        }
    }
}

@Composable
private fun IdentityBanner(onOpenSettings: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringRes(R.string.git_name_und_mail_fuer_commits), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onOpenSettings) { Text(stringRes(R.string.git_einstellen)) }
        }
    }
}

@Composable
private fun NotARepository(state: GitUiState, onEvent: (GitUiEvent) -> Unit, modifier: Modifier) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringRes(R.string.git_kein_git_repository), style = MaterialTheme.typography.titleMedium)
        Text(
            stringRes(R.string.git_dieses_projekt_wird_noch_nicht),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(onClick = { onEvent(GitUiEvent.InitRepository) }, enabled = state.busy == null) { Text(stringRes(R.string.git_repository_initialisieren)) }
        if (state.busy != null) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
