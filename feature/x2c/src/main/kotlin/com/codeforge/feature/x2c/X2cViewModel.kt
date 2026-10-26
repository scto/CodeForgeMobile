package com.codeforge.feature.x2c

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.datastore.SettingsRepository
import com.codeforge.core.domain.model.LayoutClassification
import com.codeforge.core.domain.model.X2cOptions
import com.codeforge.core.domain.repository.ComposeMigrationScanner
import com.codeforge.core.domain.repository.X2cRepository
import com.codeforge.core.navigation.OpenFileRequestBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class X2cViewModel @Inject constructor(
    private val scanner: ComposeMigrationScanner,
    private val repository: X2cRepository,
    private val settingsRepository: SettingsRepository,
    private val openFileRequestBridge: OpenFileRequestBridge
) : ViewModel() {

    private val _uiState = MutableStateFlow(X2cUiState())
    val uiState: StateFlow<X2cUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<X2cEffect>()
    val effects: SharedFlow<X2cEffect> = _effects.asSharedFlow()

    private var migrationJob: Job? = null
    private var currentProjectPath: String = ""

    init {
        viewModelScope.launch {
            repository.progress.collect { prog ->
                _uiState.update { it.copy(progress = prog) }
            }
        }
    }

    fun handleIntent(intent: X2cIntent) {
        when (intent) {
            is X2cIntent.ScanProject -> scanProject(intent.projectPath, intent.manualTrigger, intent.preselectedLayout)
            is X2cIntent.ToggleLayoutSelection -> toggleLayoutSelection(intent.relativePath)
            X2cIntent.SelectAllLayouts -> selectAllLayouts()
            X2cIntent.DeselectAllLayouts -> _uiState.update { it.copy(selectedLayouts = emptySet()) }
            is X2cIntent.SetSearchQuery -> _uiState.update { it.copy(searchQuery = intent.query) }
            is X2cIntent.SetWireActivities -> _uiState.update { it.copy(wireActivities = intent.enabled) }
            is X2cIntent.SetGenerateTheme -> _uiState.update { it.copy(generateTheme = intent.enabled) }
            is X2cIntent.SetRemoveXmlAfterSuccess -> _uiState.update { it.copy(removeXmlAfterSuccess = intent.enabled) }
            is X2cIntent.SetUseVersionCatalog -> _uiState.update { it.copy(useVersionCatalog = intent.enabled) }
            is X2cIntent.SetRunBuildCheck -> _uiState.update { it.copy(runBuildCheck = intent.enabled) }
            is X2cIntent.SetCreateGitBranch -> _uiState.update { it.copy(createGitBranch = intent.enabled) }
            is X2cIntent.SetIncludeGitInBackup -> _uiState.update { it.copy(includeGitInBackup = intent.enabled) }
            X2cIntent.StartMigration -> startMigration()
            X2cIntent.CancelMigration -> cancelMigration()
            is X2cIntent.DismissDialog -> dismissDialog(intent.dontAskAgain)
            is X2cIntent.OpenFile -> openFile(intent.filePath)
            X2cIntent.DismissResult -> _uiState.update { it.copy(showResultScreen = false) }
            X2cIntent.RestoreBackup -> restoreBackup()
            is X2cIntent.SetFilter -> _uiState.update { it.copy(selectedFilter = intent.filter) }
        }
    }

    private fun scanProject(path: String, manualTrigger: Boolean, preselectedLayout: String?) {
        currentProjectPath = path
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, errorMessage = null) }
            try {
                val isDismissed = settingsRepository.isX2cDismissed(path).first()
                if (!manualTrigger && isDismissed) {
                    _uiState.update { it.copy(isScanning = false) }
                    return@launch
                }

                val scan = scanner.scanProject(path)
                if (!manualTrigger && !scan.qualifiesForAutoPrompt) {
                    _uiState.update { it.copy(isScanning = false, scanResult = scan) }
                    return@launch
                }

                val plan = scanner.createPlan(path, preselectedLayout)
                val initialSelected = if (preselectedLayout != null) {
                    val matching = plan.layouts.find {
                        it.relativePath == preselectedLayout || it.relativePath.endsWith(preselectedLayout)
                    }
                    if (matching != null) setOf(matching.relativePath) else emptySet()
                } else {
                    plan.layouts.filter { it.classification != LayoutClassification.NOT_CONVERTIBLE }
                        .map { it.relativePath }
                        .toSet()
                }

                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanResult = scan,
                        plan = plan,
                        selectedLayouts = initialSelected,
                        showDialog = true,
                        useVersionCatalog = plan.hasVersionCatalog,
                        generateTheme = plan.detectedTheme != null,
                        wireActivities = false,
                        removeXmlAfterSuccess = false,
                        runBuildCheck = true,
                        includeGitInBackup = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false, errorMessage = e.message) }
            }
        }
    }

    private fun toggleLayoutSelection(relativePath: String) {
        _uiState.update { current ->
            val set = current.selectedLayouts.toMutableSet()
            if (set.contains(relativePath)) {
                set.remove(relativePath)
            } else {
                set.add(relativePath)
            }
            current.copy(selectedLayouts = set)
        }
    }

    private fun selectAllLayouts() {
        val plan = _uiState.value.plan ?: return
        val validLayouts = plan.layouts
            .filter { it.classification != LayoutClassification.NOT_CONVERTIBLE }
            .map { it.relativePath }
            .toSet()
        _uiState.update { it.copy(selectedLayouts = validLayouts) }
    }

    private fun dismissDialog(dontAskAgain: Boolean) {
        if (dontAskAgain && currentProjectPath.isNotBlank()) {
            viewModelScope.launch {
                settingsRepository.setX2cDismissed(currentProjectPath, true)
            }
        }
        _uiState.update { it.copy(showDialog = false) }
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

        val options = X2cOptions(
            wireActivities = state.wireActivities,
            generateTheme = state.generateTheme,
            removeXmlAfterSuccess = state.removeXmlAfterSuccess,
            useVersionCatalog = state.useVersionCatalog,
            runBuildCheck = state.runBuildCheck,
            createGitBranch = state.createGitBranch,
            includeGitInBackup = state.includeGitInBackup,
            selectedLayouts = state.selectedLayouts
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
                _effects.emit(X2cEffect.ShowMessage("Migration failed: ${e.message}"))
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
                    _effects.emit(X2cEffect.ShowMessage("Migration rückgängig gemacht"))
                    _uiState.update { it.copy(showResultScreen = false) }
                } else {
                    _effects.emit(X2cEffect.ShowMessage("Rückgängig machen fehlgeschlagen"))
                }
            } catch (e: Exception) {
                _effects.emit(X2cEffect.ShowMessage("Fehler: ${e.message}"))
            }
        }
    }

    private fun openFile(filePath: String) {
        viewModelScope.launch {
            openFileRequestBridge.requestOpen(filePath)
            _effects.emit(X2cEffect.NavigateToFile(filePath))
        }
    }
}
