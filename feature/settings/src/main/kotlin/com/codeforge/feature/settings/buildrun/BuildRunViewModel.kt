/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.buildrun

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codeforge.core.domain.model.GradleRunSettings
import com.codeforge.core.domain.repository.BuildEvent
import com.codeforge.core.domain.repository.BuildSettingsRepository
import com.codeforge.core.domain.repository.GradleBuildRepository
import com.codeforge.core.domain.repository.GradleMaintenanceRepository
import com.codeforge.core.domain.repository.RecentProjectsRepository
import com.codeforge.core.resources.R
import com.codeforge.core.resources.Res
import com.codeforge.feature.settings.formatSize
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ViewModel von „Build & Run“: Gradle-Optionen, Daemon-JVM, Caches und Wrapper. */
@HiltViewModel
class BuildRunViewModel @Inject constructor(
    private val buildSettings: BuildSettingsRepository,
    private val maintenance: GradleMaintenanceRepository,
    private val gradle: GradleBuildRepository,
    private val recentProjects: RecentProjectsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BuildRunState(
            settings = buildSettings.settings.value,
            gradleUserHome = maintenance.gradleUserHome(),
            gradleHomeInput = buildSettings.settings.value.customGradleHome,
        )
    )
    /** UI-Zustand. */
    val uiState: StateFlow<BuildRunState> = _uiState.asStateFlow()

    private val _effect = MutableSharedFlow<BuildRunEffect>(extraBufferCapacity = 8)
    /** Einmalige Effekte. */
    val effect: SharedFlow<BuildRunEffect> = _effect.asSharedFlow()

    init {
        viewModelScope.launch { buildSettings.settings.collect { s -> _uiState.update { it.copy(settings = s) } } }
        viewModelScope.launch {
            recentProjects.recentProjects.collect { list ->
                val latest = list.maxByOrNull { it.lastOpenedEpochMillis }
                _uiState.update { it.copy(projectPath = latest?.path.orEmpty(), projectName = latest?.name.orEmpty()) }
            }
        }
    }

    /** Verarbeitet eine Aktion des Nutzers. */
    fun onEvent(event: BuildRunEvent) {
        when (event) {
            is BuildRunEvent.SetLogLevel -> save { it.copy(logLevel = event.level) }
            is BuildRunEvent.SetStacktrace -> save { it.copy(stacktrace = event.mode) }
            is BuildRunEvent.SetOffline -> save { it.copy(offline = event.enabled) }
            is BuildRunEvent.SetNoBuildCache -> save { it.copy(noBuildCache = event.enabled) }
            is BuildRunEvent.SetNoDaemon -> save { it.copy(noDaemon = event.enabled) }
            is BuildRunEvent.SetContinue -> save { it.copy(continueOnFailure = event.enabled) }
            is BuildRunEvent.SetExtraArgs -> save { it.copy(extraArgs = event.value) }
            is BuildRunEvent.GradleHomeInputChanged -> _uiState.update { it.copy(gradleHomeInput = event.value) }
            BuildRunEvent.SaveGradleHome -> saveGradleHome()
            is BuildRunEvent.SaveDaemonJvm -> {
                save { it.copy(daemonJvm = event.options) }
                say(Res.string(R.string.jvm_saved))
            }
            is BuildRunEvent.WrapperVersionChanged -> _uiState.update { it.copy(wrapperVersion = event.value) }
            is BuildRunEvent.Request ->
                // Wrapper-Update löscht nichts und startet direkt; Löschaktionen fragen erst nach.
                if (event.action == BuildRunAction.UPDATE_WRAPPER) run(event.action)
                else _uiState.update { it.copy(pendingConfirm = event.action) }
            BuildRunEvent.DismissConfirm -> _uiState.update { it.copy(pendingConfirm = null) }
            BuildRunEvent.Confirm -> {
                val action = _uiState.value.pendingConfirm ?: return
                _uiState.update { it.copy(pendingConfirm = null) }
                run(action)
            }
        }
    }

    private fun save(change: (GradleRunSettings) -> GradleRunSettings) {
        viewModelScope.launch { buildSettings.update(change) }
    }

    private fun saveGradleHome() {
        val path = _uiState.value.gradleHomeInput.trim()
        if (path.isNotEmpty() && !File(path, "bin/gradle").isFile) {
            say(Res.string(R.string.buildrun_gradle_home_invalid, path))
            return
        }
        save { it.copy(customGradleHome = path) }
        say(Res.string(if (path.isEmpty()) R.string.buildrun_gradle_home_cleared else R.string.buildrun_gradle_home_saved))
    }

    private fun run(action: BuildRunAction) {
        val state = _uiState.value
        if (state.running != null) return
        if (action in NEEDS_PROJECT && state.projectPath.isBlank()) {
            say(Res.string(R.string.buildrun_no_project))
            return
        }
        _uiState.update { it.copy(running = action, runningOutput = "") }
        viewModelScope.launch {
            when (action) {
                BuildRunAction.CLEAR_BUILD_CACHE -> report(maintenance.clearBuildCache())
                BuildRunAction.DELETE_PROJECT_GRADLE -> report(maintenance.deleteProjectGradleDir(state.projectPath))
                BuildRunAction.DELETE_USER_CACHES -> report(maintenance.deleteUserCaches())
                BuildRunAction.UPDATE_WRAPPER -> updateWrapper(state.projectPath, state.wrapperVersion.trim())
            }
            _uiState.update { it.copy(running = null, runningOutput = "") }
        }
    }

    private fun report(result: Result<Long>) {
        result
            .onSuccess { say(Res.string(R.string.buildrun_freed, formatSize(it))) }
            .onFailure { say(it.message ?: Res.string(R.string.buildrun_failed)) }
    }

    /** `gradlew wrapper --gradle-version <v>` im zuletzt geöffneten Projekt. */
    private suspend fun updateWrapper(projectPath: String, version: String) {
        if (!_uiState.value.wrapperVersionValid) {
            say(Res.string(R.string.buildrun_wrapper_version_invalid))
            return
        }
        gradle.connect(projectPath).onFailure {
            say(it.message ?: Res.string(R.string.buildrun_failed))
            return
        }
        gradle.runTask("wrapper --gradle-version $version").collect { event ->
            when (event) {
                is BuildEvent.Output -> if (event.line.isNotBlank()) _uiState.update { it.copy(runningOutput = event.line) }
                is BuildEvent.BuildFinished ->
                    say(Res.string(if (event.success) R.string.buildrun_wrapper_done else R.string.buildrun_failed, version))
                is BuildEvent.BuildFailed -> say(event.message)
                else -> Unit
            }
        }
    }

    private fun say(message: String) {
        _effect.tryEmit(BuildRunEffect.ShowSnackbar(message))
    }

    private companion object {
        val NEEDS_PROJECT = setOf(BuildRunAction.DELETE_PROJECT_GRADLE, BuildRunAction.UPDATE_WRAPPER)
    }
}
