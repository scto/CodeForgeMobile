package {{packageName}}.feature.tasks

import androidx.compose.runtime.Immutable
import {{packageName}}.core.domain.model.Task

/** Welche Aufgaben die Liste zeigt. */
enum class TaskFilter { ALL, OPEN, DONE }

/** Zustand der Aufgabenliste; [newTitle] ist `null`, solange der Dialog zu ist. */
@Immutable
data class TasksUiState(
    val tasks: List<Task> = emptyList(),
    val filter: TaskFilter = TaskFilter.ALL,
    val newTitle: String? = null,
    val showTitleError: Boolean = false
)

/** Aktionen des Nutzers. */
sealed interface TasksUiEvent {
    data class FilterSelected(val filter: TaskFilter) : TasksUiEvent
    data class DoneChanged(val id: Long, val done: Boolean) : TasksUiEvent
    data class DeleteClicked(val id: Long) : TasksUiEvent
    data object AddClicked : TasksUiEvent
    data class TitleChanged(val value: String) : TasksUiEvent
    data object SaveClicked : TasksUiEvent
    data object DialogDismissed : TasksUiEvent
}
