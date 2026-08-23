// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.runtime.Immutable

enum class GitClonePhase { IDLE, CLONING, DONE, FAILED }

@Immutable
data class GitCloneUiState(
    val repositoryUrl: String = "",
    val targetDirectory: String = "",
    val currentTask: String = "",
    val progressPercent: Int = 0,
    val phase: GitClonePhase = GitClonePhase.IDLE,
    val errorMessage: String? = null
)

sealed interface GitCloneUiEvent {
    data class UrlChanged(val url: String) : GitCloneUiEvent
    data class TargetDirChanged(val path: String) : GitCloneUiEvent
    data object CloneClicked : GitCloneUiEvent
    data object RetryClicked : GitCloneUiEvent
}

sealed interface GitCloneUiEffect {
    data class NavigateToFileTree(val rootPath: String) : GitCloneUiEffect
}
