/**
 * Modul: :feature:dependencyupdates
 * @author Thomas Schmid
 */
package com.codeforge.feature.dependencyupdates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.navigation.FileSyncBridge
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.libs.dependency_updater_api.DependencyUpdate
import com.codeforge.libs.dependency_updater_api.DependencyUpdateRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Steuert den Update-Dialog beim Öffnen eines Projekts. Der ViewModel hängt am NavBackStackEntry
 * des Workspace: er überlebt Konfigurationswechsel (kein erneutes „Ask later“-Reset beim Drehen),
 * wird aber beim Verlassen/Wiederöffnen des Projekts neu erzeugt → [start] ruft dann
 * [DependencyUpdateRepository.onProjectOpened] erneut auf.
 */
@HiltViewModel
class DependencyUpdatesViewModel @Inject constructor(
    private val repository: DependencyUpdateRepository,
    private val fileSyncBridge: FileSyncBridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(DependencyUpdatesUiState())
    val uiState: StateFlow<DependencyUpdatesUiState> = _uiState.asStateFlow()

    private var startedRoot: String? = null
    private var observeJob: Job? = null

    /** Schlüssel erfolgreich angewendeter Updates — verhindert kurzes „Wiederauftauchen“ bis zur Neuprüfung. */
    private val handledKeys = MutableStateFlow<Set<String>>(emptySet())

    fun start(rootPath: String) {
        if (startedRoot == rootPath) return
        startedRoot = rootPath
        handledKeys.value = emptySet()
        repository.onProjectOpened(rootPath)
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observe(rootPath).collect { state ->
                publish(state.promptable.filter { it.key !in handledKeys.value })
            }
        }
    }

    fun onEvent(event: DependencyUpdatesEvent) {
        val root = startedRoot ?: return
        val update = _uiState.value.current ?: return
        if (_uiState.value.isApplying) return
        when (event) {
            DependencyUpdatesEvent.Dismiss -> viewModelScope.launch { repository.dismiss(root, update) }
            DependencyUpdatesEvent.AskLater -> repository.snooze(root, update)
            DependencyUpdatesEvent.Update -> apply(root, update)
        }
    }

    private fun apply(root: String, update: DependencyUpdate) {
        _uiState.update { it.copy(isApplying = true, errorMessage = null) }
        viewModelScope.launch {
            fileSyncBridge.requestFlush(update.locations.map { it.filePath }.toSet())
            repository.apply(root, listOf(update))
                .onSuccess { result ->
                    fileSyncBridge.notifyExternalChange(result.changedFiles)
                    val error = result.failed.firstOrNull()?.second
                    if (error == null) {
                        handledKeys.update { it + update.key }
                        _uiState.update { it.copy(isApplying = false) }
                        publishFromRepository(root)
                    } else {
                        _uiState.update { it.copy(isApplying = false, errorMessage = error) }
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isApplying = false, errorMessage = e.message ?: Res.string(R.string.dependencyupdates_update_fehlgeschlagen)) }
                }
        }
    }

    private suspend fun publishFromRepository(root: String) {
        // handledKeys wurde geändert → sofort neu filtern, ohne auf das nächste State-Update zu warten
        val state = repository.observe(root).first()
        publish(state.promptable.filter { it.key !in handledKeys.value })
    }

    private fun publish(queue: List<DependencyUpdate>) {
        _uiState.update {
            it.copy(
                current = queue.firstOrNull(),
                remaining = (queue.size - 1).coerceAtLeast(0),
                errorMessage = if (queue.firstOrNull()?.key == it.current?.key) it.errorMessage else null
            )
        }
    }
}
