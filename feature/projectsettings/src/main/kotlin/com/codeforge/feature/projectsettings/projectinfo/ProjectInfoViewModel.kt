/**
 * Modul: :feature:projectsettings
 * @author Thomas Schmid
 */
package com.codeforge.feature.projectsettings.projectinfo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.repository.ProjectStatsRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ViewModel von „Projektinfo“: wertet das geöffnete Projekt aus. */
@HiltViewModel
class ProjectInfoViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ProjectStatsRepository,
) : ViewModel() {

    /** Projektwurzel aus der Navigation. */
    val rootPath: String = savedStateHandle.get<String>(ARG_ROOT_PATH).orEmpty()

    private val _uiState = MutableStateFlow<ProjectInfoState>(ProjectInfoState.Loading())
    /** UI-Zustand. */
    val uiState: StateFlow<ProjectInfoState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    /** Wertet das Projekt (erneut) aus. */
    fun refresh() {
        job?.cancel()
        _uiState.value = ProjectInfoState.Loading()
        job = viewModelScope.launch {
            if (rootPath.isBlank()) {
                _uiState.value = ProjectInfoState.Failed(Res.string(R.string.projectinfo_no_project))
                return@launch
            }
            repository.analyze(rootPath) { read -> _uiState.value = ProjectInfoState.Loading(read) }
                .onSuccess { _uiState.value = ProjectInfoState.Loaded(it) }
                .onFailure { _uiState.value = ProjectInfoState.Failed(it.message ?: Res.string(R.string.projectinfo_failed)) }
        }
    }

    companion object {
        /** Name des Navigationsarguments. */
        const val ARG_ROOT_PATH = "rootPath"
    }
}
