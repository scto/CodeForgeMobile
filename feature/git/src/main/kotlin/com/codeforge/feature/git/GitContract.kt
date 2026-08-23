// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.GitCommitInfo
import com.codeforge.core.domain.model.GitStatusEntry

@Immutable
data class GitUiState(
    val repoPath: String = "",
    val currentBranch: String = "",
    val statusEntries: List<GitStatusEntry> = emptyList(),
    val commitMessage: String = "",
    val recentCommits: List<GitCommitInfo> = emptyList(),
    val isLoading: Boolean = true,
    val isOperationInProgress: Boolean = false
)

sealed interface GitUiEvent {
    data object Refresh : GitUiEvent
    data class CommitMessageChanged(val text: String) : GitUiEvent
    data object StageAllClicked : GitUiEvent
    data object CommitClicked : GitUiEvent
    data object PushClicked : GitUiEvent
    data object PullClicked : GitUiEvent
}

sealed interface GitUiEffect {
    data class ShowSnackbar(val message: String) : GitUiEffect
}
