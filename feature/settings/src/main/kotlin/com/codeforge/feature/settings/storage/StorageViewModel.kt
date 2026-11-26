/**
 * Modul: :feature:settings
 * @author Thomas Schmid
 */
package com.codeforge.feature.settings.storage

import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import javax.inject.Inject

/** Zustand der Seiten „Speicher“ und „Daten & Datenschutz“. */
@Immutable
data class StorageUiState(
    val report: StorageReport? = null,
    val projectsRoot: String = "",
    val scanning: Boolean = false,
    /** Laufende Löschaktion (Kategorie, Projektpfad oder [BUSY_CACHES]/[BUSY_BACKUP]). */
    val busy: String? = null,
    val crashReports: Int = 0,
    /** Projekt, dessen Löschung bestätigt werden soll. */
    val confirmDelete: ProjectUsage? = null
) {
    companion object {
        const val BUSY_CACHES = "caches"
        const val BUSY_BACKUP = "backup"
        const val BUSY_ALL = "all"
    }
}

/** Aktionen. */
sealed interface StorageEvent {
    /** Neu messen. */
    data object Refresh : StorageEvent
    /** Eine Kategorie leeren. */
    data class Clear(val category: StorageCategory) : StorageEvent
    /** Alle löschbaren Kategorien leeren. */
    data object ClearAll : StorageEvent
    /** Regenerierbare Caches ohne Abhängigkeiten leeren. */
    data object ClearCaches : StorageEvent
    /** Löschen eines Projekts anfragen. */
    data class RequestDelete(val project: ProjectUsage) : StorageEvent
    /** Löschen bestätigen. */
    data object ConfirmDelete : StorageEvent
    /** Löschen abbrechen. */
    data object DismissDelete : StorageEvent
    /** Projekte nach [target] sichern. */
    data class Backup(val target: Uri) : StorageEvent
    /** Absturzberichte löschen. */
    data object ClearCrashReports : StorageEvent
}

/** ViewModel für Speicher und Daten. */
@HiltViewModel
class StorageViewModel @Inject constructor(
    private val repository: StorageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(StorageUiState())
    /** UI-Zustand. */
    val state: StateFlow<StorageUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Einmalige Hinweise. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(projectsRoot = repository.projectsRootPath(), crashReports = repository.crashReportCount()) }
        }
    }

    /** Verarbeitet eine Aktion. */
    fun onEvent(event: StorageEvent) {
        when (event) {
            StorageEvent.Refresh -> {
                refresh()
            }
            is StorageEvent.Clear -> {
                work(event.category.name) { repository.clear(event.category).freed() }
            }
            StorageEvent.ClearAll -> {
                work(StorageUiState.BUSY_ALL) { clearAll().freed() }
            }
            StorageEvent.ClearCaches -> {
                work(StorageUiState.BUSY_CACHES) { repository.clearCaches().freed() }
            }
            is StorageEvent.RequestDelete -> {
                _state.update { it.copy(confirmDelete = event.project) }
            }
            StorageEvent.DismissDelete -> {
                _state.update { it.copy(confirmDelete = null) }
            }
            StorageEvent.ConfirmDelete -> {
                val project = _state.value.confirmDelete ?: return
                _state.update { it.copy(confirmDelete = null) }
                work(project.path) { repository.deleteProject(project.path).freed() }
            }
            is StorageEvent.Backup -> {
                work(StorageUiState.BUSY_BACKUP) {
                    repository.backupProjects(event.target)
                        .onSuccess { say(Res.string(R.string.storage_backup_done, it)) }
                        .onFailure { say(Res.string(R.string.storage_failed, it.message.orEmpty())) }
                }
            }
            StorageEvent.ClearCrashReports -> {
                viewModelScope.launch {
                    val count = repository.clearCrashReports()
                    _state.update { it.copy(crashReports = repository.crashReportCount()) }
                    say(Res.string(R.string.storage_crash_cleared, count))
                }
            }
        }
    }

    private suspend fun clearAll(): Result<Long> {
        var freed = 0L
        var error: Throwable? = null
        StorageCategory.entries.filter { it.clearable }.forEach { category ->
            repository.clear(category).onSuccess { freed += it }.onFailure { error = error ?: it }
        }
        return error?.let { Result.failure(it) } ?: Result.success(freed)
    }

    private fun refresh() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true) }
        viewModelScope.launch {
            val report = runCatching { repository.scan() }.getOrNull()
            _state.update { it.copy(report = report ?: it.report, scanning = false) }
        }
    }

    private fun work(key: String, block: suspend () -> Result<*>) {
        if (_state.value.busy != null) return
        _state.update { it.copy(busy = key) }
        viewModelScope.launch {
            block()
            _state.update { it.copy(busy = null) }
            if (_state.value.report != null) refresh()
        }
    }

    private suspend fun Result<Long>.freed(): Result<Long> = onSuccess { say(Res.string(R.string.storage_freed, formatBytes(it))) }
        .onFailure { say(Res.string(R.string.storage_failed, it.message.orEmpty())) }

    private suspend fun say(text: String) {
        _messages.emit(text)
    }
}

/** Lesbare Größe (B, KB, MB, GB). */
fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= KILO && unit < units.lastIndex) {
        value /= KILO
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(java.util.Locale.getDefault(), "%.1f %s", value, units[unit])
}

private const val KILO = 1024.0
