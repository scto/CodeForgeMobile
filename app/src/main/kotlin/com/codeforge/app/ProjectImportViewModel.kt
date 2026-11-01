/**
 * Modul: :app
 * @author Thomas Schmid
 *
 * Verdrahtet [ProjectImportRepository] (SAF-Verzeichnisbaum → app-privater Kopie) mit der
 * Navigation. Liegt in :app statt in einem :feature-Modul, da `ActivityResultContracts.
 * OpenDocumentTree` konzeptionell zur Host-Activity gehört und kein eigenes Feature-Modul
 * für einen einzigen Picker-Screen gerechtfertigt ist.
 */
package com.codeforge.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.RecentProject
import com.codeforge.core.domain.repository.ImportProgress
import com.codeforge.core.domain.repository.ProjectImportRepository
import com.codeforge.core.domain.repository.RecentProjectsRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProjectImportUiState {
    /** Auswahl: lokal öffnen (ohne Kopie) oder in den Projektstamm kopieren. */
    data object ChooseMode : ProjectImportUiState
    data object AwaitingPicker : ProjectImportUiState
    data class Copying(val currentPath: String, val filesCopiedSoFar: Int) : ProjectImportUiState
    data class Done(val rootPath: String) : ProjectImportUiState
    data class Failed(val message: String) : ProjectImportUiState
}

@HiltViewModel
class ProjectImportViewModel @Inject constructor(
    private val projectImportRepository: ProjectImportRepository,
    private val recentProjectsRepository: RecentProjectsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProjectImportUiState>(ProjectImportUiState.ChooseMode)
    val uiState: StateFlow<ProjectImportUiState> = _uiState.asStateFlow()

    /** Wird vom SAF-Launcher (`OpenDocumentTree`) aufgerufen, sobald der Nutzer ein Verzeichnis gewählt hat. */
    fun onTreeSelected(treeUriString: String?) {
        if (treeUriString == null) {
            _uiState.update { ProjectImportUiState.Failed(Res.string(R.string.app_kein_verzeichnis_ausgewaehlt)) }
            return
        }

        viewModelScope.launch {
            projectImportRepository.importFromTree(treeUriString).collect { progress ->
                _uiState.update {
                    when (progress) {
                        is ImportProgress.Copying ->
                            ProjectImportUiState.Copying(progress.currentPath, progress.filesCopiedSoFar)

                        is ImportProgress.Done -> {
                            recentProjectsRepository.addOrUpdate(
                                RecentProject(
                                    id = progress.result.rootPath,
                                    name = progress.result.name,
                                    path = progress.result.rootPath,
                                    lastOpenedEpochMillis = System.currentTimeMillis(),
                                    moduleCount = 1
                                )
                            )
                            ProjectImportUiState.Done(progress.result.rootPath)
                        }

                        is ImportProgress.Failed -> ProjectImportUiState.Failed(progress.message)
                    }
                }
            }
        }
    }

    /** Der Picker ist offen; solange wird ein Fortschrittszustand gezeigt. */
    fun onPickerOpened() {
        _uiState.update { ProjectImportUiState.AwaitingPicker }
    }

    /**
     * Öffnet den gewählten Ordner direkt am Speicherort (keine Kopie). Voraussetzung: lokaler Speicher
     * und „Zugriff auf alle Dateien“. Sonst Fehlermeldung mit Hinweis auf die Kopier-Variante.
     */
    fun onLocalTreeSelected(treeUriString: String?) {
        if (treeUriString == null) {
            _uiState.update { ProjectImportUiState.Failed(Res.string(R.string.app_kein_verzeichnis_ausgewaehlt)) }
            return
        }
        val path = projectImportRepository.resolveLocalDirectory(treeUriString)
        val failure = when {
            path == null -> Res.string(R.string.app_import_lokal_nicht_aufloesbar)
            !projectImportRepository.hasAllFilesAccess() -> Res.string(R.string.app_import_lokal_berechtigung)
            else -> null
        }
        if (path == null || failure != null) {
            _uiState.update { ProjectImportUiState.Failed(failure.orEmpty()) }
            return
        }
        viewModelScope.launch {
            recentProjectsRepository.addOrUpdate(
                RecentProject(
                    id = path,
                    name = path.substringAfterLast('/'),
                    path = path,
                    lastOpenedEpochMillis = System.currentTimeMillis(),
                    moduleCount = 1
                )
            )
            _uiState.update { ProjectImportUiState.Done(path) }
        }
    }

    fun retry() {
        _uiState.update { ProjectImportUiState.ChooseMode }
    }
}
