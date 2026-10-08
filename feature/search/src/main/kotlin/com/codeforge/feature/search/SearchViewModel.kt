/**
 * Modul: :feature:search
 * @author Thomas Schmid
 */
package com.codeforge.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.navigation.OpenFileRequestBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.code_tools.search.ProjectSearchOptions
import com.codeforge.libs.code_tools.search.ProjectSearcher
import com.codeforge.libs.code_tools.search.SearchOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val openFileBridge: OpenFileRequestBridge,
    private val fileSyncBridge: FileSyncBridge,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _effects = Channel<SearchUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var searchJob: Job? = null

    fun onEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.Initialize -> if (_state.value.rootPath != event.rootPath) {
                _state.update { SearchUiState(rootPath = event.rootPath) }
            }
            is SearchUiEvent.QueryChanged -> { _state.update { it.copy(query = event.value) }; schedule() }
            is SearchUiEvent.ReplacementChanged -> _state.update { it.copy(replacement = event.value) }
            is SearchUiEvent.IncludeChanged -> { _state.update { it.copy(includeGlobs = event.value) }; schedule() }
            is SearchUiEvent.ExcludeChanged -> { _state.update { it.copy(excludeGlobs = event.value) }; schedule() }
            SearchUiEvent.ToggleCase -> { _state.update { it.copy(caseSensitive = !it.caseSensitive) }; schedule() }
            SearchUiEvent.ToggleRegex -> { _state.update { it.copy(regex = !it.regex) }; schedule() }
            SearchUiEvent.ToggleWholeWord -> { _state.update { it.copy(wholeWord = !it.wholeWord) }; schedule() }
            SearchUiEvent.ToggleReplace -> _state.update { it.copy(showReplace = !it.showReplace) }
            SearchUiEvent.ToggleFilters -> _state.update { it.copy(showFilters = !it.showFilters) }
            is SearchUiEvent.ToggleFile -> _state.update {
                it.copy(collapsed = if (event.relativePath in it.collapsed) it.collapsed - event.relativePath else it.collapsed + event.relativePath)
            }
            is SearchUiEvent.OpenMatch -> viewModelScope.launch {
                openFileBridge.requestOpenAt(event.path, event.match.line + 1, event.match.column + 1, event.match.end - event.match.start)
                _effects.send(SearchUiEffect.MatchOpened)
            }
            is SearchUiEvent.ReplaceOne -> replace(onlyFile = event.path, matchStart = event.match.start)
            is SearchUiEvent.ReplaceInFile -> replace(onlyFile = event.path, matchStart = null)
            SearchUiEvent.RequestReplaceAll -> _state.update { it.copy(confirmReplaceAll = true) }
            SearchUiEvent.DismissReplaceAll -> _state.update { it.copy(confirmReplaceAll = false) }
            SearchUiEvent.ConfirmReplaceAll -> {
                _state.update { it.copy(confirmReplaceAll = false) }
                replace(onlyFile = null, matchStart = null)
            }
            SearchUiEvent.Rerun -> schedule(immediate = true)
        }
    }

    private fun currentOptions(s: SearchUiState): ProjectSearchOptions = ProjectSearchOptions(
        search = SearchOptions(s.query, s.caseSensitive, s.regex, s.wholeWord),
        includeGlobs = s.includeGlobs.splitGlobs(),
        excludeGlobs = s.excludeGlobs.splitGlobs(),
    )

    private fun String.splitGlobs(): List<String> = split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }

    private fun schedule(immediate: Boolean = false) {
        searchJob?.cancel()
        val s = _state.value
        if (s.query.isEmpty() || s.rootPath.isEmpty()) {
            _state.update { it.copy(result = null, error = null, isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            if (!immediate) delay(DEBOUNCE_MS)
            val job = coroutineContext[Job]!!
            _state.update { it.copy(isSearching = true, error = null) }
            val outcome = withContext(Dispatchers.IO) {
                ProjectSearcher { !job.isActive }.search(File(s.rootPath), currentOptions(s))
            }
            outcome
                .onSuccess { r -> _state.update { it.copy(result = r, isSearching = false, error = null) } }
                .onFailure { e -> _state.update { it.copy(result = null, isSearching = false, error = e.message ?: Res.string(R.string.search_suche_fehlgeschlagen)) } }
        }
    }

    /** [onlyFile] `null` = alle Dateien; [matchStart] `null` = alle Treffer der Datei. */
    private fun replace(onlyFile: String?, matchStart: Int?) {
        val s = _state.value
        if (s.isReplacing || s.query.isEmpty()) return
        val options = currentOptions(s)
        viewModelScope.launch {
            _state.update { it.copy(isReplacing = true) }
            val affected = onlyFile?.let { setOf(it) } ?: s.result?.files?.map { it.path }?.toSet().orEmpty()
            // Ungespeicherte Editor-Puffer zuerst auf die Platte, damit Offsets/Inhalt stimmen.
            fileSyncBridge.requestFlush(affected)
            val searcher = ProjectSearcher()
            val message = withContext(Dispatchers.IO) {
                if (onlyFile != null && matchStart != null) {
                    searcher.replaceOne(File(onlyFile), options.search, s.replacement, matchStart).fold(
                        onSuccess = { ok -> if (ok) 1 to Res.string(R.string.search_1_treffer_ersetzt) else 0 to Res.string(R.string.search_treffer_nicht_mehr_aktuell_suche) },
                        onFailure = { 0 to Res.string(R.string.search_ersetzen_fehlgeschlagen, it.message) },
                    ).also { (n, _) -> if (n > 0) fileSyncBridge.notifyExternalChange(listOf(onlyFile)) }
                } else {
                    searcher.replaceAll(File(s.rootPath), options, s.replacement, onlyFile?.let { setOf(it) }).fold(
                        onSuccess = { r ->
                            fileSyncBridge.notifyExternalChange(r.changedFiles)
                            r.replacements to buildString {
                                append(Res.string(R.string.search_ersetzung_en_in_datei_en, r.replacements, r.changedFiles.size))
                                if (r.failedFiles.isNotEmpty()) append(Res.string(R.string.search_fehlgeschlagen, r.failedFiles.size))
                            }
                        },
                        onFailure = { 0 to Res.string(R.string.search_ersetzen_fehlgeschlagen, it.message) },
                    )
                }
            }
            _state.update { it.copy(isReplacing = false) }
            _effects.send(SearchUiEffect.ShowSnackbar(message.second))
            schedule(immediate = true)
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 350L
    }
}
