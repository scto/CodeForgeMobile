// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.core.resources.stringRes
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GitCloneRoute(
    modifier: Modifier = Modifier,
    onCloned: (rootPath: String) -> Unit,
    viewModel: GitCloneViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                is GitCloneUiEffect.NavigateToFileTree -> onCloned(effect.rootPath)
            }
        }
    }

    GitCloneScreen(modifier = modifier, uiState = uiState, onEvent = viewModel::onEvent)
}

@Composable
private fun GitCloneScreen(
    modifier: Modifier = Modifier,
    uiState: GitCloneUiState,
    onEvent: (GitCloneUiEvent) -> Unit
) {
    Column(modifier = modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Text(stringRes(R.string.git_repository_klonen), style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        val fieldsEnabled = uiState.phase != GitClonePhase.CLONING

        OutlinedTextField(
            value = uiState.repositoryUrl,
            onValueChange = { onEvent(GitCloneUiEvent.UrlChanged(it)) },
            label = { Text("Repository-URL") },
            enabled = fieldsEnabled,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.targetDirectory,
            onValueChange = { onEvent(GitCloneUiEvent.TargetDirChanged(it)) },
            label = { Text(stringRes(R.string.git_zielverzeichnis)) },
            enabled = fieldsEnabled,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (uiState.phase) {
            GitClonePhase.IDLE -> {
                Button(onClick = { onEvent(GitCloneUiEvent.CloneClicked) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.git_klonen))
                }
            }

            GitClonePhase.CLONING -> {
                Text(uiState.currentTask.ifBlank { Res.string(R.string.git_klonen_laeuft) }, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { uiState.progressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            GitClonePhase.DONE -> {
                Text(stringRes(R.string.git_repository_geklont), style = MaterialTheme.typography.bodyMedium)
            }

            GitClonePhase.FAILED -> {
                Text(
                    uiState.errorMessage ?: stringRes(R.string.common_klonen_fehlgeschlagen),
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { onEvent(GitCloneUiEvent.RetryClicked) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringRes(R.string.common_erneut_versuchen))
                }
            }
        }
    }
}
