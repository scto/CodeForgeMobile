package {{packageName}}.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import {{packageName}}.core.domain.usecase.AddTaskUseCase
import {{packageName}}.core.domain.usecase.DeleteTaskUseCase
import {{packageName}}.core.domain.usecase.ObserveTasksUseCase
import {{packageName}}.core.domain.usecase.SetTaskDoneUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** MVI: Aufgaben aus dem Repository, Filter und Dialog als eigener Zustand. */
@HiltViewModel
class TasksViewModel @Inject constructor(
    observeTasks: ObserveTasksUseCase,
    private val addTask: AddTaskUseCase,
    private val setTaskDone: SetTaskDoneUseCase,
    private val deleteTask: DeleteTaskUseCase
) : ViewModel() {

    // Eingaben, die nur diese Oberfläche betreffen.
    private data class LocalState(
        val filter: TaskFilter = TaskFilter.ALL,
        val newTitle: String? = null,
        val showTitleError: Boolean = false
    )

    private val local = MutableStateFlow(LocalState())

    val uiState: StateFlow<TasksUiState> = combine(observeTasks(), local) { tasks, ui ->
        TasksUiState(
            tasks = when (ui.filter) {
                TaskFilter.ALL -> tasks
                TaskFilter.OPEN -> tasks.filterNot { it.done }
                TaskFilter.DONE -> tasks.filter { it.done }
            },
            filter = ui.filter,
            newTitle = ui.newTitle,
            showTitleError = ui.showTitleError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TasksUiState())

    fun onEvent(event: TasksUiEvent) {
        when (event) {
            is TasksUiEvent.FilterSelected -> local.update { it.copy(filter = event.filter) }
            is TasksUiEvent.DoneChanged -> viewModelScope.launch { setTaskDone(event.id, event.done) }
            is TasksUiEvent.DeleteClicked -> viewModelScope.launch { deleteTask(event.id) }
            TasksUiEvent.AddClicked -> local.update { it.copy(newTitle = "", showTitleError = false) }
            is TasksUiEvent.TitleChanged -> local.update { it.copy(newTitle = event.value, showTitleError = false) }
            TasksUiEvent.DialogDismissed -> local.update { it.copy(newTitle = null, showTitleError = false) }
            TasksUiEvent.SaveClicked -> save()
        }
    }

    private fun save() {
        val title = local.value.newTitle ?: return
        viewModelScope.launch {
            addTask(title)
                .onSuccess { local.update { it.copy(newTitle = null) } }
                .onFailure { local.update { it.copy(showTitleError = true) } }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
