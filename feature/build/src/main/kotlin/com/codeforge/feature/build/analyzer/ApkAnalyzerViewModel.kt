// Modul: :feature:build
package com.codeforge.feature.build.analyzer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.analyzer.ApkReportFormatter
import com.codeforge.core.domain.model.ApkAnalysisException
import com.codeforge.core.domain.repository.ApkAnalyzerRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ApkAnalyzerViewModel @Inject constructor(
    private val repository: ApkAnalyzerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ApkAnalyzerUiState())
    val uiState: StateFlow<ApkAnalyzerUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<ApkAnalyzerUiEffect>()
    val effect: SharedFlow<ApkAnalyzerUiEffect> = _effect.asSharedFlow()

    fun onEvent(event: ApkAnalyzerUiEvent) {
        when (event) {
            is ApkAnalyzerUiEvent.LoadApk -> loadApk(event.path)
            is ApkAnalyzerUiEvent.SelectTab -> _uiState.update { it.copy(activeTab = event.tab) }
            is ApkAnalyzerUiEvent.UpdateSearchQuery -> _uiState.update { it.copy(searchQuery = event.query) }
            is ApkAnalyzerUiEvent.SetSortOrder -> _uiState.update { it.copy(sortOrder = event.order) }
            is ApkAnalyzerUiEvent.SetCategoryFilter -> _uiState.update { it.copy(categoryFilter = event.filter) }
            is ApkAnalyzerUiEvent.SetPermissionFilter -> _uiState.update { it.copy(permissionFilter = event.filter) }
            is ApkAnalyzerUiEvent.SetDiffFilter -> _uiState.update { it.copy(diffFilter = event.filter) }
            is ApkAnalyzerUiEvent.TogglePathExpanded -> togglePathExpanded(event.path)
            is ApkAnalyzerUiEvent.CompareWithApk -> compareWithApk(event.comparePath)
            is ApkAnalyzerUiEvent.ClearComparison -> _uiState.update { it.copy(comparisonResult = null, comparedApkPath = null) }
            is ApkAnalyzerUiEvent.ShareMarkdownReport -> shareMarkdownReport()
        }
    }

    private fun loadApk(path: String) {
        if (path.isBlank()) {
            _uiState.update { it.copy(errorMessage = Res.string(R.string.apk_analyzer_keine_datei)) }
            return
        }

        val file = File(path)
        if (!file.exists()) {
            _uiState.update {
                it.copy(
                    errorMessage = Res.string(R.string.apk_analyzer_datei_nicht_gefunden, path),
                    isLoading = false
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                baseApkPath = path,
                comparisonResult = null,
                comparedApkPath = null
            )
        }

        viewModelScope.launch {
            repository.analyzeApk(file).fold(
                onSuccess = { result ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            analysisResult = result,
                            errorMessage = null
                        )
                    }
                },
                onFailure = { error ->
                    val message = when (error) {
                        is ApkAnalysisException.ZipSlipDetected -> Res.string(R.string.apk_analyzer_fehler_zip_slip, error.message ?: "")
                        is ApkAnalysisException.CorruptArchive -> Res.string(R.string.apk_analyzer_fehler_beschaedigt, error.message ?: "")
                        is ApkAnalysisException.FileNotFound -> Res.string(R.string.apk_analyzer_datei_nicht_gefunden, path)
                        else -> Res.string(R.string.apk_analyzer_fehler_laden, error.message ?: "")
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = message
                        )
                    }
                }
            )
        }
    }

    private fun compareWithApk(comparePath: String) {
        val basePath = _uiState.value.baseApkPath
        if (basePath.isNullOrBlank()) {
            _uiState.update { it.copy(errorMessage = Res.string(R.string.apk_analyzer_keine_datei)) }
            return
        }

        val baseFile = File(basePath)
        val compareFile = File(comparePath)

        if (!compareFile.exists()) {
            viewModelScope.launch {
                _effect.emit(ApkAnalyzerUiEffect.ShowSnackbar(Res.string(R.string.apk_analyzer_datei_nicht_gefunden, comparePath)))
            }
            return
        }

        _uiState.update { it.copy(isComparing = true, comparedApkPath = comparePath) }

        viewModelScope.launch {
            repository.compareApks(baseFile, compareFile).fold(
                onSuccess = { compResult ->
                    _uiState.update {
                        it.copy(
                            isComparing = false,
                            comparisonResult = compResult,
                            activeTab = ApkAnalyzerTab.COMPARE
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isComparing = false) }
                    _effect.emit(ApkAnalyzerUiEffect.ShowSnackbar(Res.string(R.string.apk_analyzer_fehler_laden, error.message ?: "")))
                }
            )
        }
    }

    private fun togglePathExpanded(path: String) {
        _uiState.update { state ->
            val set = state.expandedPaths.toMutableSet()
            if (set.contains(path)) {
                set.remove(path)
            } else {
                set.add(path)
            }
            state.copy(expandedPaths = set)
        }
    }

    private fun shareMarkdownReport() {
        val state = _uiState.value
        val title = Res.string(R.string.apk_analyzer_teilen_dialog_titel)

        val markdown = if (state.activeTab == ApkAnalyzerTab.COMPARE && state.comparisonResult != null) {
            ApkReportFormatter.formatComparisonToMarkdown(state.comparisonResult)
        } else if (state.analysisResult != null) {
            ApkReportFormatter.formatAnalysisToMarkdown(state.analysisResult)
        } else {
            return
        }

        viewModelScope.launch {
            _effect.emit(ApkAnalyzerUiEffect.ShareReport(markdown, title))
        }
    }
}
