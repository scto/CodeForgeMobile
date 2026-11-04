// Modul: :feature:git
package com.codeforge.feature.git

import androidx.compose.runtime.Immutable
import com.codeforge.core.domain.model.ConflictParser
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

/** Tabs der Git-Ansicht. */
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
    /** Ob der Diff bearbeitbar ist (nur Arbeitskopie, nicht innerhalb eines Commits). */
    val editable: Boolean get() = commit == null
}

/** Zustand der Commit-Detailansicht. */
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

/** Zustand der Dateihistorie. */
@Immutable
data class HistoryState(
    val path: String,
    val isLoading: Boolean = true,
    val commits: List<GitCommitInfo> = emptyList(),
    val error: String? = null,
)

/** Zustand der Blame-Ansicht. */
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
    val customTexts: Map<Int, String> = emptyMap(),
    val error: String? = null,
    val allConflictingPaths: List<String> = emptyList(),
    val activeBlockIndex: Int = 0,
    val editingBlockIndex: Int? = null,
    val showPreview: Boolean = false,
    val showThreeWay: Boolean = false,
    val wordDiffEnabled: Boolean = true,
    val isBinary: Boolean = false,
    val isDeletedVsModified: Boolean = false,
    val history: List<Pair<Map<Int, ConflictResolution>, Map<Int, String>>> = emptyList(),
) {
    /** Anzahl der Konfliktblöcke. */
    val conflictCount: Int get() = segments.count { it is ConflictSegment.Conflict }
    /** Anzahl der noch nicht gelösten Konfliktblöcke. */
    val unresolved: Int get() = segments.filterIsInstance<ConflictSegment.Conflict>().count { choices[it.index] == null && customTexts[it.index] == null }
    /** Index der aktuellen Datei unter allen Konfliktdateien. */
    val currentFileIndex: Int get() = allConflictingPaths.indexOf(path).coerceAtLeast(0)
    /** Anzahl der Konfliktdateien. */
    val totalFiles: Int get() = allConflictingPaths.size.coerceAtLeast(1)
    /** Ob es eine vorherige Konfliktdatei gibt. */
    val hasPrevFile: Boolean get() = currentFileIndex > 0
    /** Ob es eine nächste Konfliktdatei gibt. */
    val hasNextFile: Boolean get() = currentFileIndex >= 0 && currentFileIndex < allConflictingPaths.size - 1
    /** Ob ein gemeinsamer Vorfahre (diff3) vorliegt. */
    val hasDiff3Base: Boolean get() = segments.filterIsInstance<ConflictSegment.Conflict>().any { it.base != null }
    /** Ergebnistext mit den gewählten Lösungen. */
    val previewText: String get() = ConflictParser.resolve(segments, choices, customTexts)
}

/** Modale Eingaben/Bestätigungen. */
sealed interface GitDialog {
    /** [startPoint] = Commit-Hash als Ausgangspunkt, `null` = HEAD. */
    data class NewBranch(val startPoint: String? = null) : GitDialog
    /** Branch löschen bestätigen. */
    data class DeleteBranch(val name: String) : GitDialog
    /** Branch umbenennen. */
    data class RenameBranch(val name: String) : GitDialog
    /** Merge bestätigen. */
    data class MergeBranch(val name: String) : GitDialog
    /** Rebase bestätigen. */
    data class RebaseOnto(val name: String) : GitDialog
    /** Remote-Branch löschen bestätigen. */
    data class DeleteRemoteBranch(val name: String) : GitDialog
    /** Remote entfernen bestätigen. */
    data class RemoveRemote(val name: String) : GitDialog
    /** Verwerfen bestätigen. */
    data class Discard(val paths: List<String>) : GitDialog
    /** Remote-URL setzen. */
    data object SetRemote : GitDialog
    /** Abbruch des Merges bestätigen. */
    data object ConfirmAbortMerge : GitDialog
    /** Abbruch des Rebase bestätigen. */
    data object ConfirmAbortRebase : GitDialog
    /** Stash anlegen. */
    data object StashSave : GitDialog
    /** Stash verwerfen bestätigen. */
    data class StashDrop(val stash: GitStash) : GitDialog

    /** [target] = Commit-Hash, `null` = HEAD. */
    data class NewTag(val target: String? = null) : GitDialog
    /** Tag löschen bestätigen. */
    data class DeleteTag(val name: String) : GitDialog
    /** Reset bestätigen und Modus wählen. */
    data class ResetTo(val hash: String, val label: String) : GitDialog
    /** Revert bestätigen. */
    data class RevertCommit(val hash: String, val label: String) : GitDialog
}

/** Zustand der Git-Ansicht (Status, Listen, geöffnete Detailansichten, Dialog und laufende Operation). */
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
    /** Ob ein Commit möglich ist (nichts läuft, Nachricht vorhanden, etwas vorgemerkt oder Amend). */
    val canCommit: Boolean
        get() = busy == null && commitMessage.isNotBlank() && (
            amend ||
                status?.staged?.isNotEmpty() == true ||
                status?.state?.completesWithCommit == true
            )

    /** Rebase wird fortgesetzt statt committet. */
    val rebasing: Boolean get() = status?.state == GitRepoState.REBASING
}

/** Aktionen des Nutzers in der Git-Ansicht. */
sealed interface GitUiEvent {
    /** Status und Listen neu laden. */
    data object Refresh : GitUiEvent
    /** Ein anderer Tab wurde gewählt. */
    data class TabSelected(val tab: GitTab) : GitUiEvent
    /** Die Commit-Nachricht wurde geändert. */
    data class CommitMessageChanged(val text: String) : GitUiEvent
    /** „Letzten Commit ändern“ wurde umgeschaltet. */
    data class AmendChanged(val amend: Boolean) : GitUiEvent
    /** „Pull per Rebase“ wurde umgeschaltet. */
    data class PullRebaseChanged(val rebase: Boolean) : GitUiEvent
    /** Dateien zum Commit vormerken. */
    data class Stage(val paths: List<String>) : GitUiEvent
    /** Dateien aus dem Index nehmen. */
    data class Unstage(val paths: List<String>) : GitUiEvent
    /** Alle Änderungen vormerken. */
    data object StageAll : GitUiEvent
    /** Alle Änderungen aus dem Index nehmen. */
    data object UnstageAll : GitUiEvent
    /** Das Verwerfen von Änderungen wurde angefragt. */
    data class DiscardRequested(val paths: List<String>) : GitUiEvent
    /** Commit ausführen. */
    data object CommitClicked : GitUiEvent
    /** Push ausführen. */
    data object PushClicked : GitUiEvent
    /** Pull ausführen. */
    data object PullClicked : GitUiEvent
    /** Fetch ausführen. */
    data object FetchClicked : GitUiEvent
    /** Diff einer Datei öffnen ([staged]: Index gegen HEAD). */
    data class OpenDiff(val path: String, val staged: Boolean) : GitUiEvent
    /** Diff schließen. */
    data object CloseDiff : GitUiEvent
    /** Datei im Editor öffnen. */
    data class OpenFile(val path: String) : GitUiEvent
    /** Repository im Projektordner anlegen. */
    data object InitRepository : GitUiEvent

    // Hunks
    /** Aktion auf einen einzelnen Hunk (stagen, zurücknehmen). */
    data class HunkAction(val path: String, val staged: Boolean, val hunkIndex: Int, val action: GitHunkAction) : GitUiEvent

    // Datei-Aktionen
    /** Verlauf einer Datei öffnen. */
    data class OpenHistory(val path: String) : GitUiEvent
    /** Dateiverlauf schließen. */
    data object CloseHistory : GitUiEvent
    /** Blame einer Datei öffnen. */
    data class OpenBlame(val path: String) : GitUiEvent
    /** Blame schließen. */
    data object CloseBlame : GitUiEvent
    /** Datei in `.gitignore` eintragen. */
    data class AddToGitignore(val path: String, val untracked: Boolean) : GitUiEvent

    // Konflikte
    /** Konflikt-Editor für eine Datei öffnen. */
    data class OpenConflict(val path: String) : GitUiEvent
    /** Konflikt-Editor schließen. */
    data object CloseConflict : GitUiEvent
    /** Lösung für einen Konfliktblock wählen oder zurücknehmen. */
    data class ConflictChoice(val index: Int, val resolution: ConflictResolution?) : GitUiEvent
    /** Für alle Konfliktblöcke dieselbe Lösung wählen. */
    data class ConflictChooseAll(val resolution: ConflictResolution) : GitUiEvent
    /** Aufgelöste Datei schreiben und vormerken. */
    data object ConflictApply : GitUiEvent
    /** Ganze Datei durch eine Seite ersetzen. */
    data class ResolveConflictFile(val path: String, val side: GitConflictSide) : GitUiEvent
    /** Zu einer anderen Konfliktdatei wechseln. */
    data class ConflictNavigateFile(val path: String) : GitUiEvent
    /** Zu einem Konfliktblock springen. */
    data class ConflictJumpToBlock(val blockIndex: Int) : GitUiEvent
    /** Eigenen Text für einen Block setzen. */
    data class ConflictCustomText(val index: Int, val text: String) : GitUiEvent
    /** Bearbeiten eines Blocks beginnen. */
    data class ConflictStartEditing(val index: Int) : GitUiEvent
    /** Bearbeiten abbrechen. */
    data object ConflictCancelEditing : GitUiEvent
    /** Vorschau ein-/ausblenden. */
    data object ConflictTogglePreview : GitUiEvent
    /** Dreiwege-Ansicht ein-/ausblenden. */
    data object ConflictToggleThreeWay : GitUiEvent
    /** Wortweisen Diff ein-/ausschalten. */
    data object ConflictToggleWordDiff : GitUiEvent
    /** Letzte Auswahl rückgängig machen. */
    data object ConflictUndo : GitUiEvent

    // Branches / Merge / Rebase
    /** Branch auschecken. */
    data class Checkout(val branch: String) : GitUiEvent
    /** Dialog „Neuer Branch“ öffnen. */
    data class NewBranchRequested(val startPoint: String? = null) : GitUiEvent
    /** Branch anlegen (optional auschecken). */
    data class CreateBranch(val name: String, val checkout: Boolean, val startPoint: String? = null) : GitUiEvent
    /** Löschen eines Branches wurde angefragt. */
    data class DeleteBranchRequested(val name: String) : GitUiEvent
    /** Löschen eines Branches wurde bestätigt ([force] auch ungemergt). */
    data class DeleteBranchConfirmed(val name: String, val force: Boolean) : GitUiEvent
    /** Umbenennen wurde angefragt. */
    data class RenameBranchRequested(val name: String) : GitUiEvent
    /** Umbenennen wurde bestätigt. */
    data class RenameBranchConfirmed(val oldName: String, val newName: String) : GitUiEvent
    /** Löschen eines Remote-Branches wurde angefragt. */
    data class DeleteRemoteBranchRequested(val name: String) : GitUiEvent
    /** Löschen eines Remote-Branches wurde bestätigt. */
    data class DeleteRemoteBranchConfirmed(val name: String) : GitUiEvent
    /** Merge wurde angefragt. */
    data class MergeRequested(val name: String) : GitUiEvent
    /** Merge wurde bestätigt. */
    data class MergeConfirmed(val name: String) : GitUiEvent
    /** Rebase wurde angefragt. */
    data class RebaseRequested(val name: String) : GitUiEvent
    /** Rebase wurde bestätigt. */
    data class RebaseConfirmed(val name: String) : GitUiEvent
    /** Rebase fortsetzen. */
    data object RebaseContinue : GitUiEvent
    /** Aktuellen Rebase-Schritt überspringen. */
    data object RebaseSkip : GitUiEvent
    /** Abbruch des Rebase wurde angefragt. */
    data object RebaseAbortRequested : GitUiEvent
    /** Abbruch des Rebase wurde bestätigt. */
    data object RebaseAbortConfirmed : GitUiEvent
    /** Abbruch des Merges wurde angefragt. */
    data object AbortMergeRequested : GitUiEvent
    /** Abbruch des Merges wurde bestätigt. */
    data object AbortMergeConfirmed : GitUiEvent
    /** Dialog „Remote setzen“ öffnen. */
    data object SetRemoteRequested : GitUiEvent
    /** Remote mit Name und URL setzen. */
    data class SetRemoteConfirmed(val name: String, val url: String) : GitUiEvent
    /** Entfernen eines Remotes wurde angefragt. */
    data class RemoveRemoteRequested(val name: String) : GitUiEvent
    /** Entfernen eines Remotes wurde bestätigt. */
    data class RemoveRemoteConfirmed(val name: String) : GitUiEvent
    /** Verwerfen wurde bestätigt. */
    data class DiscardConfirmed(val paths: List<String>) : GitUiEvent
    /** Dialog schließen. */
    data object DismissDialog : GitUiEvent

    // Stash
    /** Dialog „Stash anlegen“ öffnen. */
    data object StashSaveRequested : GitUiEvent
    /** Stash anlegen (optional mit nicht verfolgten Dateien). */
    data class StashSaveConfirmed(val message: String, val includeUntracked: Boolean) : GitUiEvent
    /** Stash anwenden ([drop]: danach entfernen). */
    data class StashApply(val index: Int, val drop: Boolean) : GitUiEvent
    /** Verwerfen eines Stashes wurde angefragt. */
    data class StashDropRequested(val stash: GitStash) : GitUiEvent
    /** Verwerfen eines Stashes wurde bestätigt. */
    data class StashDropConfirmed(val index: Int) : GitUiEvent

    // Tags
    /** Dialog „Neuer Tag“ öffnen. */
    data class NewTagRequested(val target: String? = null) : GitUiEvent
    /** Tag anlegen (mit [message] annotiert). */
    data class CreateTag(val name: String, val message: String, val target: String?) : GitUiEvent
    /** Löschen eines Tags wurde angefragt. */
    data class DeleteTagRequested(val name: String) : GitUiEvent
    /** Löschen eines Tags wurde bestätigt. */
    data class DeleteTagConfirmed(val name: String) : GitUiEvent
    /** Tag zum Remote pushen. */
    data class PushTag(val name: String) : GitUiEvent

    // Commits
    /** Commit-Details öffnen ([readOnly]: ohne Aktionen). */
    data class OpenCommit(val hash: String, val readOnly: Boolean = false) : GitUiEvent
    /** Commit-Details schließen. */
    data object CloseCommit : GitUiEvent
    /** Diff einer Datei des Commits öffnen. */
    data class OpenCommitFile(val path: String) : GitUiEvent
    /** Datei-Diff des Commits schließen. */
    data object CloseCommitFile : GitUiEvent
    /** Commit per Cherry-Pick übernehmen. */
    data class CherryPick(val hash: String) : GitUiEvent
    /** Revert eines Commits wurde angefragt. */
    data class RevertRequested(val hash: String, val label: String) : GitUiEvent
    /** Revert eines Commits wurde bestätigt. */
    data class RevertConfirmed(val hash: String) : GitUiEvent
    /** Reset auf einen Commit wurde angefragt. */
    data class ResetRequested(val hash: String, val label: String) : GitUiEvent
    /** Reset mit dem gewählten Modus wurde bestätigt. */
    data class ResetConfirmed(val hash: String, val mode: GitResetMode) : GitUiEvent
    /** Commit auschecken (detached HEAD). */
    data class CheckoutCommit(val hash: String) : GitUiEvent
}

/** Einmalige Effekte der Git-Ansicht. */
sealed interface GitUiEffect {
    /** Kurze Meldung anzeigen. */
    data class ShowSnackbar(val message: String) : GitUiEffect

    /** Datei im Editor geöffnet → Host (Drawer) kann sich schließen. */
    data object FileOpened : GitUiEffect
}
