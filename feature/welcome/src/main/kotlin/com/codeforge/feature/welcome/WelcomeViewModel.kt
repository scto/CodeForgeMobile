// Modul: :feature:welcome
package com.codeforge.feature.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.common.crash.CfmCrashHandler
import com.codeforge.core.domain.repository.LegacyProjectMigrator
import com.codeforge.core.domain.repository.ProjectsDirectoryProvider
import com.codeforge.core.domain.repository.RecentProjectsRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val recentProjectsRepository: RecentProjectsRepository,
    private val projectsDirectoryProvider: ProjectsDirectoryProvider? = null,
    private val legacyProjectMigrator: LegacyProjectMigrator? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(WelcomeUiState())
    val uiState: StateFlow<WelcomeUiState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<WelcomeUiEffect>()
    val effect: SharedFlow<WelcomeUiEffect> = _effect.asSharedFlow()

    init {
        recentProjectsRepository.recentProjects
            .onEach { projects -> _uiState.update { it.copy(recentProjects = projects, isLoading = false) } }
            .launchIn(viewModelScope)

        checkForCrashLogs()
        checkLegacyProjects()
    }

    private fun checkForCrashLogs() = viewModelScope.launch(Dispatchers.IO) {
        val root = projectsDirectoryProvider?.getProjectsRoot() ?: return@launch
        val crashDir = File(root, ".cfm/crash")
        val ackFile = File(crashDir, ".acknowledged")
        val acknowledgedNames = if (ackFile.exists()) {
            ackFile.readLines().map { it.trim() }.toSet()
        } else {
            emptySet()
        }

        val crashLogs = CfmCrashHandler.listCrashLogs(crashDir)
        val unacknowledged = crashLogs.firstOrNull { it.name !in acknowledgedNames }
        if (unacknowledged != null) {
            _uiState.update { it.copy(pendingCrashLog = unacknowledged) }
        }
    }

    private fun markCrashLogAcknowledged(file: File) {
        try {
            val ackFile = File(file.parentFile, ".acknowledged")
            ackFile.appendText("${file.name}\n")
        } catch (_: Throwable) {}
    }

    private fun checkLegacyProjects() = viewModelScope.launch {
        val migrator = legacyProjectMigrator ?: return@launch
        if (migrator.hasLegacyProjects() && !migrator.isMigrationDeclined()) {
            val count = migrator.listLegacyProjects().size
            _uiState.update { it.copy(showMigrationDialog = true, legacyProjectsCount = count) }
        }
    }

    fun onEvent(event: WelcomeUiEvent) {
        when (event) {
            WelcomeUiEvent.CreateProjectClicked -> emit(WelcomeUiEffect.NavigateToProjectWizard)
            WelcomeUiEvent.ImportProjectClicked -> emit(WelcomeUiEffect.NavigateToImportPicker)
            WelcomeUiEvent.CloneProjectClicked -> emit(WelcomeUiEffect.NavigateToCloneDialog)
            WelcomeUiEvent.TerminalClicked -> emit(WelcomeUiEffect.NavigateToTerminal)
            WelcomeUiEvent.SettingsClicked -> emit(WelcomeUiEffect.NavigateToSettings)
            is WelcomeUiEvent.RecentProjectSelected ->
                _uiState.update { it.copy(selectedProjectId = event.projectId) }
            is WelcomeUiEvent.RecentProjectOpened -> openRecentProject(event.projectId)
            is WelcomeUiEvent.RecentProjectRemoved -> removeRecentProject(event.projectId)
            is WelcomeUiEvent.RecentProjectSettingsClicked -> openProjectSettings(event.projectId)

            // Task 55 Crash-Log Events
            WelcomeUiEvent.ViewCrashLogClicked -> viewCrashDetails()
            WelcomeUiEvent.ShareCrashLogClicked -> shareCrashLog()
            WelcomeUiEvent.DeleteCrashLogClicked -> deleteCrashLog()
            WelcomeUiEvent.DismissCrashLogClicked -> dismissCrashLog()
            WelcomeUiEvent.DismissCrashDetails -> _uiState.update { it.copy(showCrashDetails = false) }

            // Task 55 Migration Events
            WelcomeUiEvent.ConfirmMigrationClicked -> executeMigration()
            WelcomeUiEvent.DeclineMigrationClicked -> declineMigration()
        }
    }

    private fun viewCrashDetails() {
        val file = _uiState.value.pendingCrashLog ?: return
        val text = try { file.readText() } catch (e: Exception) { e.message.orEmpty() }
        _uiState.update { it.copy(showCrashDetails = true, crashDetailsText = text) }
    }

    private fun shareCrashLog() {
        val file = _uiState.value.pendingCrashLog ?: return
        emit(WelcomeUiEffect.ShareCrashFile(file))
    }

    private fun deleteCrashLog() {
        val file = _uiState.value.pendingCrashLog ?: return
        file.delete()
        markCrashLogAcknowledged(file)
        _uiState.update { it.copy(pendingCrashLog = null, showCrashDetails = false) }
        emit(WelcomeUiEffect.ShowSnackbar(Res.string(R.string.crash_report_deleted)))
    }

    private fun dismissCrashLog() {
        val file = _uiState.value.pendingCrashLog ?: return
        markCrashLogAcknowledged(file)
        _uiState.update { it.copy(pendingCrashLog = null, showCrashDetails = false) }
    }

    private fun executeMigration() = viewModelScope.launch {
        _uiState.update { it.copy(showMigrationDialog = false) }
        val migrator = legacyProjectMigrator ?: return@launch
        val result = migrator.migrateLegacyProjects()
        result.fold(
            onSuccess = { summary ->
                emit(WelcomeUiEffect.ShowSnackbar(
                    Res.string(R.string.migration_success, summary.movedProjectCount, summary.backupZipPath.orEmpty())
                ))
            },
            onFailure = { error ->
                emit(WelcomeUiEffect.ShowSnackbar(
                    Res.string(R.string.migration_failed, error.message.orEmpty())
                ))
            }
        )
    }

    private fun declineMigration() = viewModelScope.launch {
        legacyProjectMigrator?.setMigrationDeclined(true)
        _uiState.update { it.copy(showMigrationDialog = false) }
    }

    private fun openProjectSettings(projectId: String) = viewModelScope.launch {
        val project = _uiState.value.recentProjects.find { it.id == projectId }
        if (project != null) {
            _effect.emit(WelcomeUiEffect.NavigateToProjectSettings(project.path))
        } else {
            _effect.emit(WelcomeUiEffect.ShowSnackbar(Res.string(R.string.welcome_projekt_nicht_gefunden)))
        }
    }

    private fun openRecentProject(projectId: String) = viewModelScope.launch {
        val project = _uiState.value.recentProjects.find { it.id == projectId }
        if (project != null) {
            recentProjectsRepository.addOrUpdate(
                project.copy(lastOpenedEpochMillis = System.currentTimeMillis())
            )
            _effect.emit(WelcomeUiEffect.NavigateToEditor(project.path))
        } else {
            _effect.emit(WelcomeUiEffect.ShowSnackbar(Res.string(R.string.welcome_projekt_nicht_gefunden)))
        }
    }

    private fun removeRecentProject(projectId: String) = viewModelScope.launch {
        recentProjectsRepository.remove(projectId)
    }

    private fun emit(effect: WelcomeUiEffect) = viewModelScope.launch {
        _effect.emit(effect)
    }
}
