// Modul: :feature:git
package com.codeforge.feature.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.ConflictParser
import com.codeforge.core.domain.model.ConflictResolution
import com.codeforge.core.domain.model.ConflictSegment
import com.codeforge.core.domain.model.GitGraphLayout
import com.codeforge.core.domain.model.GitMergeResult
import com.codeforge.core.domain.model.GitMergeStatus
import com.codeforge.core.domain.model.GitRebaseOperation
import com.codeforge.core.domain.model.UnifiedDiffParser
import com.codeforge.core.domain.repository.GitRepository
import com.codeforge.core.domain.repository.GitSettingsRepository
import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class GitViewModel @Inject constructor(
    private val git: GitRepository,
    private val gitSettings: GitSettingsRepository,
    private val openFileBridge: OpenFileRequestBridge,
    private val fileSync: FileSyncBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitUiState())
    val uiState: StateFlow<GitUiState> = _uiState.asStateFlow()

    private val _effects = Channel<GitUiEffect>(Channel.BUFFERED)
    val effects: Flow<GitUiEffect> = _effects.receiveAsFlow()

    private var initializedFor: String? = null
    private var identityJob: Job? = null
    private var amendPrefill: String? = null

    fun initialize(repoPath: String) {
        if (initializedFor == repoPath) return
        initializedFor = repoPath
        _uiState.update { GitUiState(repoPath = repoPath) }
        identityJob?.cancel()
        identityJob = viewModelScope.launch {
            gitSettings.identity.collect { id -> _uiState.update { it.copy(identityMissing = !id.isComplete) } }
        }
        refresh()
    }

    fun onEvent(event: GitUiEvent) {
        when (event) {
            GitUiEvent.Refresh -> refresh()
            is GitUiEvent.TabSelected -> { _uiState.update { it.copy(tab = event.tab) }; refresh() }
            is GitUiEvent.CommitMessageChanged -> _uiState.update { it.copy(commitMessage = event.text) }
            is GitUiEvent.AmendChanged -> setAmend(event.amend)
            is GitUiEvent.PullRebaseChanged -> _uiState.update { it.copy(pullRebase = event.rebase) }
            is GitUiEvent.Stage -> op(Res.string(R.string.git_op_stage)) { git.stage(repoPath(), event.paths) }
            is GitUiEvent.Unstage -> op(Res.string(R.string.git_zuruecknehmen_2)) { git.unstage(repoPath(), event.paths) }
            GitUiEvent.StageAll -> op(Res.string(R.string.git_alles_vormerken)) { git.stageAll(repoPath()) }
            GitUiEvent.UnstageAll -> op(Res.string(R.string.git_zuruecknehmen_2)) {
                git.unstage(repoPath(), _uiState.value.status?.staged?.map { it.path }.orEmpty().distinct())
            }
            is GitUiEvent.DiscardRequested -> _uiState.update { it.copy(dialog = GitDialog.Discard(event.paths)) }
            is GitUiEvent.DiscardConfirmed -> { dismiss(); op(Res.string(R.string.git_op_discard), touchesFiles = true) { git.discard(repoPath(), event.paths) } }
            GitUiEvent.CommitClicked -> commit()
            GitUiEvent.PushClicked -> op(Res.string(R.string.git_op_push), success = Res.string(R.string.git_push_erfolgreich)) { git.push(repoPath()) }
            GitUiEvent.FetchClicked -> op(Res.string(R.string.git_op_fetch), success = Res.string(R.string.git_fetch_erfolgreich)) { git.fetch(repoPath()) }
            GitUiEvent.PullClicked -> {
                val rebase = _uiState.value.pullRebase.takeIf { it }
                op(Res.string(R.string.git_op_pull), touchesFiles = true) {
                    git.pull(repoPath(), rebase).onSuccess { reportMerge(it, if (rebase == true) "Rebase" else "Merge") }
                }
            }
            is GitUiEvent.OpenDiff -> openDiff(event.path, event.staged)
            GitUiEvent.CloseDiff -> _uiState.update { it.copy(diff = null) }
            is GitUiEvent.OpenFile -> openFile(event.path)
            GitUiEvent.InitRepository -> op(Res.string(R.string.git_git_init), success = Res.string(R.string.git_repository_angelegt)) { git.init(repoPath(), null) }

            is GitUiEvent.HunkAction -> op(Res.string(R.string.git_op_hunk), touchesFiles = true) {
                git.applyHunks(repoPath(), event.path, listOf(event.hunkIndex), event.action)
                    .onSuccess { openDiff(event.path, event.staged, closeIfEmpty = true) }
            }
            is GitUiEvent.OpenHistory -> openHistory(event.path)
            GitUiEvent.CloseHistory -> _uiState.update { it.copy(history = null) }
            is GitUiEvent.OpenBlame -> openBlame(event.path)
            GitUiEvent.CloseBlame -> _uiState.update { it.copy(blame = null) }
            is GitUiEvent.AddToGitignore -> op(Res.string(R.string.git_op_ignore), success = Res.string(R.string.git_in_gitignore_eingetragen)) {
                git.addToGitignore(repoPath(), "/" + event.path.trimStart('/'))
            }

            is GitUiEvent.OpenConflict -> openConflict(event.path)
            GitUiEvent.CloseConflict -> _uiState.update { it.copy(conflict = null) }
            is GitUiEvent.ConflictChoice -> _uiState.update { s ->
                s.copy(conflict = s.conflict?.let { c ->
                    c.copy(choices = if (event.resolution == null) c.choices - event.index else c.choices + (event.index to event.resolution))
                })
            }
            is GitUiEvent.ConflictChooseAll -> _uiState.update { s ->
                s.copy(conflict = s.conflict?.let { c ->
                    c.copy(choices = c.segments.filterIsInstance<ConflictSegment.Conflict>().associate { it.index to event.resolution })
                })
            }
            GitUiEvent.ConflictApply -> applyConflict()
            is GitUiEvent.ResolveConflictFile -> op(Res.string(R.string.git_konflikt_loesen), touchesFiles = true) {
                git.resolveConflict(repoPath(), event.path, event.side)
            }

            is GitUiEvent.Checkout -> op(Res.string(R.string.git_op_checkout), touchesFiles = true) { git.checkout(repoPath(), event.branch) }
            is GitUiEvent.NewBranchRequested -> _uiState.update { it.copy(dialog = GitDialog.NewBranch(event.startPoint)) }
            is GitUiEvent.CreateBranch -> {
                dismiss()
                op(Res.string(R.string.git_branch_anlegen), touchesFiles = event.checkout) { git.createBranch(repoPath(), event.name.trim(), event.checkout, event.startPoint) }
                closeCommit()
            }
            is GitUiEvent.DeleteBranchRequested -> _uiState.update { it.copy(dialog = GitDialog.DeleteBranch(event.name)) }
            is GitUiEvent.DeleteBranchConfirmed -> { dismiss(); op(Res.string(R.string.git_branch_loeschen_2)) { git.deleteBranch(repoPath(), event.name, event.force) } }
            is GitUiEvent.RenameBranchRequested -> _uiState.update { it.copy(dialog = GitDialog.RenameBranch(event.name)) }
            is GitUiEvent.RenameBranchConfirmed -> {
                dismiss(); op(Res.string(R.string.git_op_rename)) { git.renameBranch(repoPath(), event.oldName, event.newName.trim()) }
            }
            is GitUiEvent.DeleteRemoteBranchRequested -> _uiState.update { it.copy(dialog = GitDialog.DeleteRemoteBranch(event.name)) }
            is GitUiEvent.DeleteRemoteBranchConfirmed -> {
                dismiss(); op(Res.string(R.string.git_remote_branch_loeschen_2), success = Res.string(R.string.git_auf_dem_remote_geloescht)) { git.deleteRemoteBranch(repoPath(), event.name) }
            }
            is GitUiEvent.MergeRequested -> _uiState.update { it.copy(dialog = GitDialog.MergeBranch(event.name)) }
            is GitUiEvent.MergeConfirmed -> {
                dismiss(); op(Res.string(R.string.git_op_merge), touchesFiles = true) { git.merge(repoPath(), event.name).onSuccess { reportMerge(it, "Merge") } }
            }
            is GitUiEvent.RebaseRequested -> _uiState.update { it.copy(dialog = GitDialog.RebaseOnto(event.name)) }
            is GitUiEvent.RebaseConfirmed -> {
                dismiss(); op(Res.string(R.string.git_op_rebase), touchesFiles = true) { git.rebase(repoPath(), event.name).onSuccess { reportMerge(it, "Rebase") } }
            }
            GitUiEvent.RebaseContinue -> op(Res.string(R.string.git_rebase_fortsetzen), touchesFiles = true) {
                git.rebaseControl(repoPath(), GitRebaseOperation.CONTINUE).onSuccess { reportMerge(it, "Rebase") }
            }
            GitUiEvent.RebaseSkip -> op(Res.string(R.string.git_commit_ueberspringen), touchesFiles = true) {
                git.rebaseControl(repoPath(), GitRebaseOperation.SKIP).onSuccess { reportMerge(it, "Rebase") }
            }
            GitUiEvent.RebaseAbortRequested -> _uiState.update { it.copy(dialog = GitDialog.ConfirmAbortRebase) }
            GitUiEvent.RebaseAbortConfirmed -> {
                dismiss(); op(Res.string(R.string.git_rebase_abbrechen_2), success = Res.string(R.string.git_rebase_abgebrochen), touchesFiles = true) {
                    git.rebaseControl(repoPath(), GitRebaseOperation.ABORT)
                }
            }
            GitUiEvent.AbortMergeRequested -> _uiState.update { it.copy(dialog = GitDialog.ConfirmAbortMerge) }
            GitUiEvent.AbortMergeConfirmed -> {
                dismiss(); op(Res.string(R.string.git_op_abort), success = Res.string(R.string.git_abgebrochen), touchesFiles = true) { git.abortMerge(repoPath()) }
            }
            GitUiEvent.SetRemoteRequested -> _uiState.update { it.copy(dialog = GitDialog.SetRemote) }
            is GitUiEvent.SetRemoteConfirmed -> { dismiss(); op(Res.string(R.string.git_remote_speichern), success = Res.string(R.string.git_remote_gespeichert)) { git.setRemote(repoPath(), event.name.trim(), event.url.trim()) } }
            is GitUiEvent.RemoveRemoteRequested -> _uiState.update { it.copy(dialog = GitDialog.RemoveRemote(event.name)) }
            is GitUiEvent.RemoveRemoteConfirmed -> { dismiss(); op(Res.string(R.string.git_remote_entfernen_2), success = Res.string(R.string.git_remote_entfernt)) { git.removeRemote(repoPath(), event.name) } }
            GitUiEvent.DismissDialog -> dismiss()

            GitUiEvent.StashSaveRequested -> _uiState.update { it.copy(dialog = GitDialog.StashSave) }
            is GitUiEvent.StashSaveConfirmed -> {
                dismiss()
                op(Res.string(R.string.git_op_stash), touchesFiles = true) {
                    git.stashSave(repoPath(), event.message.ifBlank { null }, event.includeUntracked).onSuccess { saved ->
                        snack(if (saved) Res.string(R.string.git_aenderungen_im_stash_abgelegt) else Res.string(R.string.git_nichts_zu_stashen_keine_aenderungen))
                    }
                }
            }
            is GitUiEvent.StashApply -> op(if (event.drop) Res.string(R.string.git_stash_anwenden_loeschen) else Res.string(R.string.git_stash_anwenden), touchesFiles = true) {
                git.stashApply(repoPath(), event.index, event.drop)
            }
            is GitUiEvent.StashDropRequested -> _uiState.update { it.copy(dialog = GitDialog.StashDrop(event.stash)) }
            is GitUiEvent.StashDropConfirmed -> { dismiss(); op(Res.string(R.string.git_stash_loeschen_2)) { git.stashDrop(repoPath(), event.index) } }

            is GitUiEvent.NewTagRequested -> _uiState.update { it.copy(dialog = GitDialog.NewTag(event.target)) }
            is GitUiEvent.CreateTag -> {
                dismiss()
                op(Res.string(R.string.git_tag_anlegen), success = Res.string(R.string.git_tag_angelegt)) { git.createTag(repoPath(), event.name.trim(), event.message.ifBlank { null }, event.target) }
                closeCommit()
            }
            is GitUiEvent.DeleteTagRequested -> _uiState.update { it.copy(dialog = GitDialog.DeleteTag(event.name)) }
            is GitUiEvent.DeleteTagConfirmed -> { dismiss(); op(Res.string(R.string.git_tag_loeschen_2)) { git.deleteTag(repoPath(), event.name) } }
            is GitUiEvent.PushTag -> op(Res.string(R.string.git_tag_pushen), success = Res.string(R.string.git_tag_gepusht)) { git.pushTag(repoPath(), event.name) }

            is GitUiEvent.OpenCommit -> openCommit(event.hash, event.readOnly)
            GitUiEvent.CloseCommit -> closeCommit()
            is GitUiEvent.OpenCommitFile -> openCommitFile(event.path)
            GitUiEvent.CloseCommitFile -> _uiState.update { s -> s.copy(commitDetail = s.commitDetail?.copy(fileDiff = null)) }
            is GitUiEvent.CherryPick -> {
                closeCommit()
                op(Res.string(R.string.git_op_cherry_pick), touchesFiles = true) { git.cherryPick(repoPath(), event.hash).onSuccess { reportMerge(it, "Cherry-Pick") } }
            }
            is GitUiEvent.RevertRequested -> _uiState.update { it.copy(dialog = GitDialog.RevertCommit(event.hash, event.label)) }
            is GitUiEvent.RevertConfirmed -> {
                dismiss(); closeCommit()
                op(Res.string(R.string.git_op_revert), touchesFiles = true) { git.revert(repoPath(), event.hash).onSuccess { reportMerge(it, "Revert") } }
            }
            is GitUiEvent.ResetRequested -> _uiState.update { it.copy(dialog = GitDialog.ResetTo(event.hash, event.label)) }
            is GitUiEvent.ResetConfirmed -> {
                dismiss(); closeCommit()
                op(Res.string(R.string.git_op_reset), success = Res.string(R.string.git_zurueckgesetzt), touchesFiles = true) { git.reset(repoPath(), event.hash, event.mode) }
            }
            is GitUiEvent.CheckoutCommit -> {
                closeCommit()
                op(Res.string(R.string.git_op_checkout), success = Res.string(R.string.git_commit_ausgecheckt_loser_head), touchesFiles = true) { git.checkoutCommit(repoPath(), event.hash) }
            }
        }
    }

    private fun repoPath() = _uiState.value.repoPath
    private fun dismiss() = _uiState.update { it.copy(dialog = null) }
    private fun closeCommit() = _uiState.update { it.copy(commitDetail = null) }
    private suspend fun snack(message: String) { _effects.send(GitUiEffect.ShowSnackbar(message)) }

    private fun openFile(path: String) = viewModelScope.launch {
        val f = File(repoPath(), path)
        if (f.isFile) {
            openFileBridge.requestOpen(f.path)
            _effects.send(GitUiEffect.FileOpened)
        } else snack(Res.string(R.string.git_datei_existiert_nicht_mehr, path))
    }

    private suspend fun reportMerge(result: GitMergeResult, what: String) {
        val n = result.conflicts.size
        snack(
            when (result.status) {
                GitMergeStatus.FAST_FORWARD -> Res.string(R.string.git_fast_forward, what)
                GitMergeStatus.MERGED -> if (what == "Merge") Res.string(R.string.git_zusammengefuehrt) else Res.string(R.string.git_abgeschlossen, what)
                GitMergeStatus.ALREADY_UP_TO_DATE -> Res.string(R.string.git_bereits_aktuell)
                GitMergeStatus.CONFLICTING -> when (what) {
                    "Rebase" -> Res.string(R.string.git_rebase_angehalten_konflikt_loesen_vorm, n)
                    "Cherry-Pick", "Revert" -> Res.string(R.string.git_konflikt_loesen_vormerken_committen_od, what, n)
                    else -> Res.string(R.string.git_konflikte_in_datei_en_loesen, n)
                }
                GitMergeStatus.ABORTED -> Res.string(R.string.git_abgebrochen_2, what)
                GitMergeStatus.NOTHING_TO_COMMIT -> Res.string(R.string.git_nichts_zu_committen_commit_mit)
                GitMergeStatus.FAILED -> Res.string(R.string.git_nicht_moeglich_lokale_aenderungen_im, what)
            }
        )
    }

    /**
     * Führt eine Git-Operation mit Busy-Anzeige aus, meldet Fehler und lädt danach neu. [touchesFiles]: Editor-Puffer
     * werden vorher gespeichert und danach neu geladen (die Operation ändert Dateien im Arbeitsverzeichnis).
     */
    private fun op(label: String, success: String? = null, touchesFiles: Boolean = false, block: suspend () -> Result<*>) {
        if (_uiState.value.busy != null) return
        _uiState.update { it.copy(busy = label) }
        viewModelScope.launch {
            if (touchesFiles) fileSync.requestFlushDirectory(repoPath())
            val result = block()
            result.onFailure { snack(it.message ?: Res.string(R.string.git_operation_fehlgeschlagen)) }
            if (result.isSuccess && success != null) snack(success)
            if (touchesFiles) fileSync.notifyDirectoryChange(repoPath())
            _uiState.update { it.copy(busy = null) }
            refresh()
        }
    }

    // ---------------------------------------------------------------- Commit / Amend

    private fun setAmend(amend: Boolean) {
        if (!amend) {
            _uiState.update { s -> s.copy(amend = false, commitMessage = if (s.commitMessage == amendPrefill) "" else s.commitMessage) }
            amendPrefill = null
            return
        }
        viewModelScope.launch {
            val last = git.lastCommitMessage(repoPath()).getOrNull()
            if (last == null) { snack(Res.string(R.string.common_noch_kein_commit_zum_aendern)); return@launch }
            _uiState.update { s ->
                if (s.commitMessage.isBlank()) { amendPrefill = last; s.copy(amend = true, commitMessage = last) } else s.copy(amend = true)
            }
        }
    }

    private fun commit() {
        val s = _uiState.value
        op(if (s.amend) Res.string(R.string.git_commit_aendern) else Res.string(R.string.git_op_commit), success = if (s.amend) Res.string(R.string.git_commit_geaendert) else Res.string(R.string.git_commit_erstellt)) {
            git.commit(repoPath(), s.commitMessage, s.amend).also {
                if (it.isSuccess) { amendPrefill = null; _uiState.update { st -> st.copy(commitMessage = "", amend = false) } }
            }
        }
    }

    // ---------------------------------------------------------------- Diff / Commit-Details / Verlauf / Blame

    private fun openDiff(path: String, staged: Boolean, closeIfEmpty: Boolean = false) {
        _uiState.update { it.copy(diff = DiffViewState(path, staged)) }
        viewModelScope.launch {
            val result = git.diff(repoPath(), path, staged)
            val files = result.getOrNull()?.let { text -> withContext(Dispatchers.Default) { UnifiedDiffParser.parse(text) } }.orEmpty()
            _uiState.update {
                when {
                    it.diff?.path != path -> it
                    closeIfEmpty && files.isEmpty() && result.isSuccess -> it.copy(diff = null)
                    else -> it.copy(diff = it.diff.copy(isLoading = false, files = files, error = result.exceptionOrNull()?.message))
                }
            }
        }
    }

    private fun openCommit(hash: String, readOnly: Boolean) {
        _uiState.update { it.copy(commitDetail = CommitDetailState(hash, readOnly = readOnly)) }
        viewModelScope.launch {
            val result = git.commitDetail(repoPath(), hash)
            _uiState.update { s ->
                if (s.commitDetail?.hash != hash) s
                else s.copy(commitDetail = s.commitDetail.copy(isLoading = false, detail = result.getOrNull(), error = result.exceptionOrNull()?.message))
            }
        }
    }

    private fun openCommitFile(path: String) {
        val hash = _uiState.value.commitDetail?.hash ?: return
        _uiState.update { s -> s.copy(commitDetail = s.commitDetail?.copy(fileDiff = DiffViewState(path, staged = false, commit = hash))) }
        viewModelScope.launch {
            val result = git.commitDiff(repoPath(), hash, path)
            val files = result.getOrNull()?.let { text -> withContext(Dispatchers.Default) { UnifiedDiffParser.parse(text) } }.orEmpty()
            _uiState.update { s ->
                val cd = s.commitDetail
                if (cd?.fileDiff?.path != path) s
                else s.copy(commitDetail = cd.copy(fileDiff = cd.fileDiff.copy(isLoading = false, files = files, error = result.exceptionOrNull()?.message)))
            }
        }
    }

    private fun openHistory(path: String) {
        _uiState.update { it.copy(history = HistoryState(path)) }
        viewModelScope.launch {
            val result = git.fileHistory(repoPath(), path)
            _uiState.update { s ->
                if (s.history?.path != path) s
                else s.copy(history = s.history.copy(isLoading = false, commits = result.getOrNull().orEmpty(), error = result.exceptionOrNull()?.message))
            }
        }
    }

    private fun openBlame(path: String) {
        _uiState.update { it.copy(blame = BlameState(path)) }
        viewModelScope.launch {
            val result = git.blame(repoPath(), path)
            _uiState.update { s ->
                if (s.blame?.path != path) s
                else s.copy(blame = s.blame.copy(isLoading = false, lines = result.getOrNull().orEmpty(), error = result.exceptionOrNull()?.message))
            }
        }
    }

    // ---------------------------------------------------------------- Konflikt-Editor

    private fun openConflict(path: String) {
        _uiState.update { it.copy(conflict = ConflictEditorState(path)) }
        viewModelScope.launch {
            fileSync.requestFlushDirectory(repoPath())
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val f = File(repoPath(), path)
                    check(f.isFile) { Res.string(R.string.git_datei_existiert_nicht_mehr_konflikt) }
                    ConflictParser.parse(f.readText())
                }
            }
            _uiState.update { s ->
                if (s.conflict?.path != path) s
                else {
                    val segments = loaded.getOrNull().orEmpty()
                    val hasBlocks = segments.any { it is ConflictSegment.Conflict }
                    s.copy(conflict = s.conflict.copy(
                        isLoading = false,
                        segments = segments,
                        error = loaded.exceptionOrNull()?.message ?: if (!hasBlocks) Res.string(R.string.git_keine_konfliktmarker_gefunden_datei_ka) else null,
                    ))
                }
            }
        }
    }

    private fun applyConflict() {
        val c = _uiState.value.conflict ?: return
        if (c.choices.isEmpty()) return
        op(Res.string(R.string.git_konflikt_loesen), touchesFiles = true) {
            val text = ConflictParser.resolve(c.segments, c.choices)
            val written = withContext(Dispatchers.IO) { runCatching { File(repoPath(), c.path).writeText(text) } }
            if (written.isFailure) return@op Result.failure<Unit>(written.exceptionOrNull()!!)
            if (c.unresolved == 0) {
                git.stage(repoPath(), listOf(c.path)).onSuccess {
                    _uiState.update { s -> s.copy(conflict = null) }
                    snack(Res.string(R.string.git_konflikt_geloest_und_vorgemerkt))
                }
            } else {
                snack(Res.string(R.string.git_block_bloecke_noch_offen, c.unresolved))
                openConflict(c.path)
                Result.success(Unit)
            }
        }
    }

    // ---------------------------------------------------------------- Laden

    private fun refresh() {
        val path = repoPath()
        if (path.isBlank()) return
        viewModelScope.launch {
            if (!git.isRepository(path)) {
                _uiState.update { it.copy(isRepository = false, isLoading = false, status = null) }
                return@launch
            }
            val status = git.status(path)
            val tab = _uiState.value.tab
            val remotes = git.remotes(path).getOrDefault(emptyList())
            val stashes = git.stashes(path).getOrNull()
            val graph = if (tab == GitTab.GRAPH) {
                git.log(path, GRAPH_LIMIT).getOrNull()?.let { withContext(Dispatchers.Default) { GitGraphLayout.layout(it) } }
            } else null
            val branches = if (tab == GitTab.BRANCHES) git.branches(path).getOrNull() else null
            val tags = if (tab == GitTab.BRANCHES) git.tags(path).getOrNull() else null
            _uiState.update {
                val newStatus = status.getOrNull() ?: it.status
                val pending = newStatus?.pendingMessage
                it.copy(
                    isRepository = true,
                    status = newStatus,
                    remotes = remotes,
                    stashes = stashes ?: it.stashes,
                    graph = graph ?: it.graph,
                    branches = branches ?: it.branches,
                    tags = tags ?: it.tags,
                    // Nachricht einer laufenden Operation (Merge/Cherry-Pick/Revert) vorbelegen
                    commitMessage = if (it.commitMessage.isBlank() && pending != null && newStatus != null && newStatus.state.completesWithCommit) pending else it.commitMessage,
                    isLoading = false,
                )
            }
            status.exceptionOrNull()?.let { snack(it.message ?: Res.string(R.string.git_status_konnte_nicht_geladen_werden)) }
        }
    }

    private companion object {
        const val GRAPH_LIMIT = 150
    }
}
