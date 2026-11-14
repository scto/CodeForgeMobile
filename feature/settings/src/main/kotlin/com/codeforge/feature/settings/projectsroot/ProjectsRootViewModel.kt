/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.projectsroot

import androidx.lifecycle.ViewModel
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** ViewModel für das Setzen des Projektstamms. */
@HiltViewModel
class ProjectsRootViewModel @Inject constructor(
    private val provider: ProjectsDirectoryProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(snapshot(input = null))
    /** UI-Zustand. */
    val uiState: StateFlow<ProjectsRootState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<ProjectsRootEffect>(extraBufferCapacity = 4)
    /** Einmalige Effekte. */
    val effect: SharedFlow<ProjectsRootEffect> = _effect.asSharedFlow()

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: ProjectsRootEvent) {
        when (event) {
            is ProjectsRootEvent.InputChanged -> _uiState.update { it.copy(input = event.value) }
            ProjectsRootEvent.UseExternalDefault -> _uiState.update { it.copy(input = it.externalDefaultPath) }
            ProjectsRootEvent.Refresh -> _uiState.update { snapshot(input = it.input) }
            ProjectsRootEvent.Apply -> apply(_uiState.value.input)
            ProjectsRootEvent.ResetToDefault -> apply(null)
        }
    }

    private fun apply(path: String?) {
        provider.setCustomRoot(path)
            .onSuccess { root ->
                _uiState.value = snapshot(input = null)
                say(Res.string(if (path.isNullOrBlank()) R.string.projroot_reset_done else R.string.projroot_applied, root.absolutePath))
            }
            .onFailure { say(Res.string(R.string.projroot_invalid, it.message.orEmpty())) }
    }

    private fun snapshot(input: String?): ProjectsRootState {
        val current = provider.getProjectsRoot().absolutePath
        return ProjectsRootState(
            currentPath = current,
            customPath = provider.getCustomRoot(),
            externalDefaultPath = provider.externalDefaultRoot().absolutePath,
            externalUsable = provider.isExternalStorageUsable(),
            input = input ?: provider.getCustomRoot() ?: current,
        )
    }

    private fun say(message: String) {
        _effect.tryEmit(ProjectsRootEffect.ShowSnackbar(message))
    }
}
