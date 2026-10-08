// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ConflictResolution
import com.codeforge.core.domain.model.ConflictSegment
import com.codeforge.core.domain.model.DiffFile
import com.codeforge.core.domain.model.GitBlameLine
import com.codeforge.core.domain.model.GitBranch
import com.codeforge.core.domain.model.GitCommitDetail
import com.codeforge.core.domain.model.GitCommitInfo
import com.codeforge.core.domain.model.GitConflictSide
import com.codeforge.core.domain.model.GitHunkAction
import com.codeforge.core.domain.model.GitRemote
import com.codeforge.core.domain.model.GitRepoState
import com.codeforge.core.domain.model.GitRepoStatus
import com.codeforge.core.domain.model.GitResetMode
import com.codeforge.core.domain.model.GitStash
import com.codeforge.core.domain.model.GitTag
import com.codeforge.core.domain.model.GraphRow

enum class GitTab { CHANGES, GRAPH, BRANCHES, STASH }

/**
 * Geöffneter Diff. [commit] == `null`: Änderungen der Arbeitskopie ([staged] = Index gegen HEAD) mit Hunk-Aktionen;
 * sonst Diff einer Datei innerhalb dieses Commits (nur lesend).
 */
@Immutable
data class DiffViewState(
    val path: String,
    val staged: Boolean,
    val isLoading: Boolean = true,
    val files: List<DiffFile> = emptyList(),
    val error: String? = null,
    val commit: String? = null,
) {
    val editable: Boolean get() = commit == null
}

@Immutable
data class CommitDetailState(
    val hash: String,
    val isLoading: Boolean = true,
    val detail: GitCommitDetail? = null,
    val error: String? = null,
    val fileDiff: DiffViewState? = null,
    /** Nur ansehen (z. B. Stash-Inhalt): keine Commit-Aktionen. */
    val readOnly: Boolean = false,
)

@Immutable
data class HistoryState(
    val path: String,
    val isLoading: Boolean = true,
    val commits: List<GitCommitInfo> = emptyList(),
    val error: String? = null,
)

@Immutable
data class BlameState(
    val path: String,
    val isLoading: Boolean = true,
    val lines: List<GitBlameLine> = emptyList(),
    val error: String? = null,
)

/** Block-weiser Konflikt-Editor für eine Datei. */
@Immutable
data class ConflictEditorState(
    val path: String,
    val isLoading: Boolean = true,
    val segments: List<ConflictSegment> = emptyList(),
    val choices: Map<Int, ConflictResolution> = emptyMap(),
    val error: String? = null,
) {
    val conflictCount: Int get() = segments.count { it is ConflictSegment.Conflict }
    val unresolved: Int get() = conflictCount - choices.size
}

/** Modale Eingaben/Bestätigungen. */
sealed interface GitDialog {
    /** [startPoint] = Commit-Hash als Ausgangspunkt, `null` = HEAD. */
    data class NewBranch(val startPoint: String? = null) : GitDialog
    data class DeleteBranch(val name: String) : GitDialog
    data class RenameBranch(val name: String) : GitDialog
    data class MergeBranch(val name: String) : GitDialog
    data class RebaseOnto(val name: String) : GitDialog
    data class DeleteRemoteBranch(val name: String) : GitDialog
    data class RemoveRemote(val name: String) : GitDialog
    data class Discard(val paths: List<String>) : GitDialog
    data object SetRemote : GitDialog
    data object ConfirmAbortMerge : GitDialog
    data object ConfirmAbortRebase : GitDialog
    data object StashSave : GitDialog
    data class StashDrop(val stash: GitStash) : GitDialog

    /** [target] = Commit-Hash, `null` = HEAD. */
    data class NewTag(val target: String? = null) : GitDialog
    data class DeleteTag(val name: String) : GitDialog
    data class ResetTo(val hash: String, val label: String) : GitDialog
    data class RevertCommit(val hash: String, val label: String) : GitDialog
}

@Immutable
data class GitUiState(
    val repoPath: String = "",
    /** `null` = wird noch geprüft. */
    val isRepository: Boolean? = null,
    val status: GitRepoStatus? = null,
    val tab: GitTab = GitTab.CHANGES,
    val commitMessage: String = "",
    val amend: Boolean = false,
    /** Pull führt per Rebase statt Merge zusammen. */
    val pullRebase: Boolean = false,
    val graph: List<GraphRow> = emptyList(),
    val branches: List<GitBranch> = emptyList(),
    val tags: List<GitTag> = emptyList(),
    val stashes: List<GitStash> = emptyList(),
    val remotes: List<GitRemote> = emptyList(),
    val diff: DiffViewState? = null,
    val commitDetail: CommitDetailState? = null,
    val history: HistoryState? = null,
    val blame: BlameState? = null,
    val conflict: ConflictEditorState? = null,
    val dialog: GitDialog? = null,
    val identityMissing: Boolean = false,
    /** Beschriftung der laufenden Operation („Push…“), `null` = idle. */
    val busy: String? = null,
    val isLoading: Boolean = true,
) {
    val canCommit: Boolean
        get() = busy == null && commitMessage.isNotBlank() && (
            amend ||
                status?.staged?.isNotEmpty() == true ||
                status?.state?.completesWithCommit == true
            )

    /** Rebase wird fortgesetzt statt committet. */
    val rebasing: Boolean get() = status?.state == GitRepoState.REBASING
}

sealed interface GitUiEvent {
    data object Refresh : GitUiEvent
    data class TabSelected(val tab: GitTab) : GitUiEvent
    data class CommitMessageChanged(val text: String) : GitUiEvent
    data class AmendChanged(val amend: Boolean) : GitUiEvent
    data class PullRebaseChanged(val rebase: Boolean) : GitUiEvent
    data class Stage(val paths: List<String>) : GitUiEvent
    data class Unstage(val paths: List<String>) : GitUiEvent
    data object StageAll : GitUiEvent
    data object UnstageAll : GitUiEvent
    data class DiscardRequested(val paths: List<String>) : GitUiEvent
    data object CommitClicked : GitUiEvent
    data object PushClicked : GitUiEvent
    data object PullClicked : GitUiEvent
    data object FetchClicked : GitUiEvent
    data class OpenDiff(val path: String, val staged: Boolean) : GitUiEvent
    data object CloseDiff : GitUiEvent
    data class OpenFile(val path: String) : GitUiEvent
    data object InitRepository : GitUiEvent

    // Hunks
    data class HunkAction(val path: String, val staged: Boolean, val hunkIndex: Int, val action: GitHunkAction) : GitUiEvent

    // Datei-Aktionen
    data class OpenHistory(val path: String) : GitUiEvent
    data object CloseHistory : GitUiEvent
    data class OpenBlame(val path: String) : GitUiEvent
    data object CloseBlame : GitUiEvent
    data class AddToGitignore(val path: String, val untracked: Boolean) : GitUiEvent

    // Konflikte
    data class OpenConflict(val path: String) : GitUiEvent
    data object CloseConflict : GitUiEvent
    data class ConflictChoice(val index: Int, val resolution: ConflictResolution?) : GitUiEvent
    data class ConflictChooseAll(val resolution: ConflictResolution) : GitUiEvent
    data object ConflictApply : GitUiEvent
    data class ResolveConflictFile(val path: String, val side: GitConflictSide) : GitUiEvent

    // Branches / Merge / Rebase
    data class Checkout(val branch: String) : GitUiEvent
    data class NewBranchRequested(val startPoint: String? = null) : GitUiEvent
    data class CreateBranch(val name: String, val checkout: Boolean, val startPoint: String? = null) : GitUiEvent
    data class DeleteBranchRequested(val name: String) : GitUiEvent
    data class DeleteBranchConfirmed(val name: String, val force: Boolean) : GitUiEvent
    data class RenameBranchRequested(val name: String) : GitUiEvent
    data class RenameBranchConfirmed(val oldName: String, val newName: String) : GitUiEvent
    data class DeleteRemoteBranchRequested(val name: String) : GitUiEvent
    data class DeleteRemoteBranchConfirmed(val name: String) : GitUiEvent
    data class MergeRequested(val name: String) : GitUiEvent
    data class MergeConfirmed(val name: String) : GitUiEvent
    data class RebaseRequested(val name: String) : GitUiEvent
    data class RebaseConfirmed(val name: String) : GitUiEvent
    data object RebaseContinue : GitUiEvent
    data object RebaseSkip : GitUiEvent
    data object RebaseAbortRequested : GitUiEvent
    data object RebaseAbortConfirmed : GitUiEvent
    data object AbortMergeRequested : GitUiEvent
    data object AbortMergeConfirmed : GitUiEvent
    data object SetRemoteRequested : GitUiEvent
    data class SetRemoteConfirmed(val name: String, val url: String) : GitUiEvent
    data class RemoveRemoteRequested(val name: String) : GitUiEvent
    data class RemoveRemoteConfirmed(val name: String) : GitUiEvent
    data class DiscardConfirmed(val paths: List<String>) : GitUiEvent
    data object DismissDialog : GitUiEvent

    // Stash
    data object StashSaveRequested : GitUiEvent
    data class StashSaveConfirmed(val message: String, val includeUntracked: Boolean) : GitUiEvent
    data class StashApply(val index: Int, val drop: Boolean) : GitUiEvent
    data class StashDropRequested(val stash: GitStash) : GitUiEvent
    data class StashDropConfirmed(val index: Int) : GitUiEvent

    // Tags
    data class NewTagRequested(val target: String? = null) : GitUiEvent
    data class CreateTag(val name: String, val message: String, val target: String?) : GitUiEvent
    data class DeleteTagRequested(val name: String) : GitUiEvent
    data class DeleteTagConfirmed(val name: String) : GitUiEvent
    data class PushTag(val name: String) : GitUiEvent

    // Commits
    data class OpenCommit(val hash: String, val readOnly: Boolean = false) : GitUiEvent
    data object CloseCommit : GitUiEvent
    data class OpenCommitFile(val path: String) : GitUiEvent
    data object CloseCommitFile : GitUiEvent
    data class CherryPick(val hash: String) : GitUiEvent
    data class RevertRequested(val hash: String, val label: String) : GitUiEvent
    data class RevertConfirmed(val hash: String) : GitUiEvent
    data class ResetRequested(val hash: String, val label: String) : GitUiEvent
    data class ResetConfirmed(val hash: String, val mode: GitResetMode) : GitUiEvent
    data class CheckoutCommit(val hash: String) : GitUiEvent
}

sealed interface GitUiEffect {
    data class ShowSnackbar(val message: String) : GitUiEffect

    /** Datei im Editor geöffnet → Host (Drawer) kann sich schließen. */
    data object FileOpened : GitUiEffect
}
