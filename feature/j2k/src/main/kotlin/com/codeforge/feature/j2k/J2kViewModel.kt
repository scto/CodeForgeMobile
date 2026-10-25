package com.codeforge.feature.j2k

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.J2kOptions
import com.codeforge.core.domain.repository.J2kRepository
import com.codeforge.core.navigation.OpenFileRequestBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class J2kViewModel @Inject constructor(
    private val repository: J2kRepository,
    private val openFileRequestBridge: OpenFileRequestBridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(J2kUiState())
    val uiState: StateFlow<J2kUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<J2kEffect>()
    val effects: SharedFlow<J2kEffect> = _effects.asSharedFlow()

    private var migrationJob: Job? = null
    private var currentProjectPath: String = ""

    init {
        viewModelScope.launch {
            repository.progress.collect { prog ->
                _uiState.update { it.copy(progress = prog) }
            }
        }
    }

    fun handleIntent(intent: J2kIntent) {
        when (intent) {
            is J2kIntent.ScanProject -> scanProject(intent.projectPath, intent.manualTrigger)
            is J2kIntent.SetConvertGradle -> _uiState.update { it.copy(convertGradle = intent.enabled) }
            is J2kIntent.SetUseVersionCatalog -> _uiState.update { it.copy(useVersionCatalog = intent.enabled) }
            is J2kIntent.SetRunBuildCheck -> _uiState.update { it.copy(runBuildCheck = intent.enabled) }
            is J2kIntent.SetCreateGitBranch -> _uiState.update { it.copy(createGitBranch = intent.enabled) }
            is J2kIntent.SetIncludeGitInBackup -> _uiState.update { it.copy(includeGitInBackup = intent.enabled) }
            is J2kIntent.StartMigration -> startMigration()
            is J2kIntent.CancelMigration -> cancelMigration()
            is J2kIntent.DismissDialog -> _uiState.update { it.copy(showDialog = false) }
            is J2kIntent.RestoreBackup -> restoreBackup()
            is J2kIntent.SetFilter -> _uiState.update { it.copy(selectedFilter = intent.filter) }
            is J2kIntent.OpenFile -> openFile(intent.filePath)
            is J2kIntent.DismissResult -> _uiState.update { it.copy(showResultScreen = false) }
        }
    }

    private fun scanProject(path: String, manualTrigger: Boolean) {
        currentProjectPath = path
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, errorMessage = null) }
            try {
                val scan = repository.scanProject(path)
                val shouldShow = manualTrigger || scan.qualifiesForAutoPrompt
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanResult = scan,
                        showDialog = shouldShow,
                        convertGradle = false,
                        useVersionCatalog = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, errorMessage = e.message) }
            }
        }
    }

    private fun startMigration() {
        val state = _uiState.value
        val path = currentProjectPath
        if (path.isBlank()) return

        _uiState.update {
            it.copy(
                showDialog = false,
                isConverting = true,
                errorMessage = null
            )
        }

        val options = J2kOptions(
            convertGradle = state.convertGradle,
            useVersionCatalog = state.useVersionCatalog,
            includeGit = state.includeGitInBackup,
            runBuildCheck = state.runBuildCheck,
            createGitBranch = state.createGitBranch
        )

        migrationJob = viewModelScope.launch {
            try {
                val rep = repository.executeMigration(path, options)
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        report = rep,
                        showResultScreen = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        errorMessage = e.message
                    )
                }
                _effects.emit(J2kEffect.ShowMessage("Migration failed: ${e.message}"))
            }
        }
    }

    private fun cancelMigration() {
        repository.cancelMigration()
        migrationJob?.cancel()
        _uiState.update {
            it.copy(
                isConverting = false,
                errorMessage = "Abgebrochen durch Benutzer"
            )
        }
    }

    private fun restoreBackup() {
        val state = _uiState.value
        val journalPath = state.report?.journalPath ?: return
        val path = currentProjectPath
        viewModelScope.launch {
            try {
                val success = repository.undoMigration(path, journalPath)
                if (success) {
                    _effects.emit(J2kEffect.ShowMessage("Migration rückgängig gemacht"))
                    _uiState.update { it.copy(showResultScreen = false) }
                } else {
                    _effects.emit(J2kEffect.ShowMessage("Rückgängig machen fehlgeschlagen"))
                }
            } catch (e: Exception) {
                _effects.emit(J2kEffect.ShowMessage("Fehler: ${e.message}"))
            }
        }
    }

    private fun openFile(filePath: String) {
        viewModelScope.launch {
            openFileRequestBridge.requestOpen(filePath)
            _effects.emit(J2kEffect.NavigateToFile(filePath))
        }
    }
}
