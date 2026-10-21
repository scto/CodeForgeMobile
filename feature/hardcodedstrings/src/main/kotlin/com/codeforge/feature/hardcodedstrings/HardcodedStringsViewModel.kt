package com.codeforge.feature.hardcodedstrings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.libs.hardcodedstrings.HardcodedStringsEngine
import com.codeforge.libs.hardcodedstrings.backup.BackupManager
import com.codeforge.libs.hardcodedstrings.model.HardcodedStringFinding
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HardcodedStringsViewModel @Inject constructor() : ViewModel() {

    private val engine = HardcodedStringsEngine()

    private val _uiState = MutableStateFlow(HardcodedStringsUiState())
    val uiState: StateFlow<HardcodedStringsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<HardcodedStringsUiEffect>()
    val effects: SharedFlow<HardcodedStringsUiEffect> = _effects.asSharedFlow()

    fun onEvent(event: HardcodedStringsUiEvent) {
        when (event) {
            is HardcodedStringsUiEvent.ScanRequested -> scan(event.targetPath, event.projectRoot)
            is HardcodedStringsUiEvent.ConfidenceFilterChanged -> _uiState.update { it.copy(confidenceFilter = event.confidence) }
            is HardcodedStringsUiEvent.SearchQueryChanged -> _uiState.update { it.copy(searchQuery = event.query) }
            is HardcodedStringsUiEvent.FindingToggled -> toggleFinding(event.id)
            is HardcodedStringsUiEvent.SelectAllToggled -> selectAll(event.selectAll)
            is HardcodedStringsUiEvent.KeyChanged -> updateKey(event.id, event.newKey)
            is HardcodedStringsUiEvent.IgnoreFinding -> ignoreFinding(event.finding)
            is HardcodedStringsUiEvent.JumpToSource -> jumpToSource(event.finding)
            HardcodedStringsUiEvent.RequestPreview -> preparePreview()
            HardcodedStringsUiEvent.DismissPreview -> _uiState.update { it.copy(isPreviewDialogOpen = false) }
            HardcodedStringsUiEvent.ConfirmApply -> applyPlan()
            HardcodedStringsUiEvent.RequestUndo -> undoLastExtraction()
            HardcodedStringsUiEvent.DismissStatusMessage -> _uiState.update { it.copy(statusMessage = null) }
        }
    }

    private fun scan(targetPath: String, projectRoot: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    targetPath = targetPath,
                    projectRoot = projectRoot,
                    isScanning = true,
                    statusMessage = null
                )
            }

            val findings = withContext(Dispatchers.IO) {
                val targetFile = File(targetPath)
                val rootFile = File(projectRoot)
                engine.scan(targetFile, rootFile)
            }

            val backupManager = BackupManager(File(projectRoot))
            val hasBackup = backupManager.getLatestBackup() != null

            _uiState.update { state ->
                val allExtractableIds = findings.filter { it.isExtractable }.map { it.id }.toSet()
                state.copy(
                    isScanning = false,
                    findings = findings,
                    selectedIds = allExtractableIds,
                    customKeys = emptyMap(),
                    isUndoAvailable = hasBackup
                )
            }
        }
    }

    private fun toggleFinding(id: String) {
        _uiState.update { state ->
            val updated = if (state.selectedIds.contains(id)) {
                state.selectedIds - id
            } else {
                state.selectedIds + id
            }
            state.copy(selectedIds = updated)
        }
    }

    private fun selectAll(selectAll: Boolean) {
        _uiState.update { state ->
            val updated = if (selectAll) {
                state.filteredFindings.filter { it.isExtractable }.map { it.id }.toSet()
            } else {
                emptySet()
            }
            state.copy(selectedIds = updated)
        }
    }

    private fun updateKey(id: String, newKey: String) {
        _uiState.update { state ->
            val updated = state.customKeys.toMutableMap()
            updated[id] = newKey.trim()
            state.copy(customKeys = updated)
        }
    }

    private fun ignoreFinding(finding: HardcodedStringFinding) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val rootFile = File(_uiState.value.projectRoot)
                val currentConfig = engine.loadConfig(rootFile)
                val updatedConfig = currentConfig.copy(
                    ignoredStrings = currentConfig.ignoredStrings + finding.rawValue.trim()
                )
                engine.saveConfig(rootFile, updatedConfig)
            }

            _uiState.update { state ->
                val remaining = state.findings.filter { it.id != finding.id }
                state.copy(
                    findings = remaining,
                    selectedIds = state.selectedIds - finding.id,
                    statusMessage = "\"${finding.rawValue}\" ignoriert"
                )
            }
        }
    }

    private fun jumpToSource(finding: HardcodedStringFinding) {
        viewModelScope.launch {
            _effects.emit(
                HardcodedStringsUiEffect.OpenEditorAt(
                    filePath = finding.file.absolutePath,
                    line = finding.line,
                    column = finding.column
                )
            )
        }
    }

    private fun preparePreview() {
        val state = _uiState.value
        val rootFile = File(state.projectRoot)

        val selectedFindings = state.findings
            .filter { state.selectedIds.contains(it.id) }
            .map { finding ->
                val customKey = state.customKeys[finding.id]
                if (!customKey.isNullOrBlank()) {
                    finding.copy(selectedKey = customKey)
                } else {
                    finding
                }
            }

        if (selectedFindings.isEmpty()) return

        viewModelScope.launch {
            val plan = withContext(Dispatchers.IO) {
                engine.createPlan(selectedFindings, rootFile)
            }

            _uiState.update {
                it.copy(
                    pendingPlan = plan,
                    isPreviewDialogOpen = true
                )
            }
        }
    }

    private fun applyPlan() {
        val plan = _uiState.value.pendingPlan ?: return
        val rootFile = File(_uiState.value.projectRoot)

        viewModelScope.launch {
            _uiState.update { it.copy(isApplying = true, isPreviewDialogOpen = false) }

            val result = withContext(Dispatchers.IO) {
                engine.applyPlan(plan, rootFile)
            }

            _uiState.update { it.copy(isApplying = false) }

            if (result.success) {
                _effects.emit(
                    HardcodedStringsUiEffect.ShowSnackbar(
                        "${result.extractedCount} Strings erfolgreich extrahiert"
                    )
                )
                // Rescan after apply
                scan(_uiState.value.targetPath, _uiState.value.projectRoot)
            } else {
                _effects.emit(
                    HardcodedStringsUiEffect.ShowSnackbar(
                        "Fehler beim Extrahieren: ${result.errorMessage}"
                    )
                )
            }
        }
    }

    private fun undoLastExtraction() {
        val rootFile = File(_uiState.value.projectRoot)
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                engine.undoLastExtraction(rootFile)
            }

            if (success) {
                _effects.emit(
                    HardcodedStringsUiEffect.ShowSnackbar("Letzte Extraktion rückgängig gemacht")
                )
                scan(_uiState.value.targetPath, _uiState.value.projectRoot)
            } else {
                _effects.emit(
                    HardcodedStringsUiEffect.ShowSnackbar("Keine Sicherung zum Wiederherstellen gefunden")
                )
            }
        }
    }
}
