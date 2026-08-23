// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.domain.model.GitCommitInfo
import com.codeforge.core.domain.model.GitFileStatus
import com.codeforge.core.domain.model.GitStatusEntry
import kotlinx.coroutines.flow.collectLatest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GitRoute(
    modifier: Modifier = Modifier,
    repoPath: String,
    viewModel: GitViewModel = hiltViewModel()
) {
    LaunchedEffect(repoPath) { viewModel.initialize(repoPath) }

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is GitUiEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
            }
        }
    }

    GitScreen(
        modifier = modifier,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::onEvent
    )
}

@Composable
private fun GitScreen(
    modifier: Modifier = Modifier,
    uiState: GitUiState,
    snackbarHostState: SnackbarHostState,
    onEvent: (GitUiEvent) -> Unit
) {
    Scaffold(
        modifier = modifier,
        snackBarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(uiState.currentBranch.ifBlank { "Git" }) },
                actions = {
                    IconButton(
                        onClick = { onEvent(GitUiEvent.PullClicked) },
                        enabled = !uiState.isOperationInProgress
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = "Pull")
                    }
                    IconButton(
                        onClick = { onEvent(GitUiEvent.PushClicked) },
                        enabled = !uiState.isOperationInProgress
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = "Push")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (uiState.isOperationInProgress) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Änderungen", style = MaterialTheme.typography.titleMedium)
            }

            if (uiState.statusEntries.isEmpty()) {
                item { Text("Keine Änderungen.", style = MaterialTheme.typography.bodySmall) }
            } else {
                items(uiState.statusEntries, key = { it.path }) { entry ->
                    StatusRow(entry)
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onEvent(GitUiEvent.StageAllClicked) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isOperationInProgress && uiState.statusEntries.isNotEmpty()
                ) {
                    Text("Alle stagen")
                }
            }

            item {
                OutlinedTextField(
                    value = uiState.commitMessage,
                    onValueChange = { onEvent(GitUiEvent.CommitMessageChanged(it)) },
                    label = { Text("Commit-Nachricht") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Button(
                    onClick = { onEvent(GitUiEvent.CommitClicked) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isOperationInProgress && uiState.commitMessage.isNotBlank()
                ) {
                    Text("Commit")
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text("Letzte Commits", style = MaterialTheme.typography.titleMedium)
            }

            items(uiState.recentCommits, key = { it.hash }) { commit ->
                CommitRow(commit)
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun StatusRow(entry: GitStatusEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(entry.path, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        AssistChip(onClick = {}, label = { Text(statusLabel(entry.status)) })
    }
}

private fun statusLabel(status: GitFileStatus): String = when (status) {
    GitFileStatus.ADDED -> "Neu"
    GitFileStatus.MODIFIED -> "Geändert"
    GitFileStatus.DELETED -> "Gelöscht"
    GitFileStatus.UNTRACKED -> "Unversioniert"
    GitFileStatus.CONFLICTING -> "Konflikt"
}

@Composable
private fun CommitRow(commit: GitCommitInfo) {
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(commit.message, style = MaterialTheme.typography.bodyMedium)
        Text(
            "${commit.shortHash} · ${commit.authorName} · ${dateFormatter.format(Date(commit.timestampEpochMillis))}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
