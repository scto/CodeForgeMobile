package {{packageName}}.core.domain.usecase

import {{packageName}}.core.domain.model.Task
import {{packageName}}.core.domain.model.TaskStats
import {{packageName}}.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Liefert alle Aufgaben. */
class ObserveTasksUseCase @Inject constructor(private val repository: TaskRepository) {
    operator fun invoke(): Flow<List<Task>> = repository.tasks
}

/** Liefert den Fortschritt (erledigt/gesamt). */
class ObserveTaskStatsUseCase @Inject constructor(private val repository: TaskRepository) {
    operator fun invoke(): Flow<TaskStats> = repository.tasks.map { tasks -> TaskStats(total = tasks.size, done = tasks.count { it.done }) }
}

/** Legt eine Aufgabe an; ein leerer Titel wird abgelehnt. */
class AddTaskUseCase @Inject constructor(private val repository: TaskRepository) {
    suspend operator fun invoke(title: String): Result<Task> {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return Result.failure(IllegalArgumentException("Titel fehlt"))
        return Result.success(repository.add(cleanTitle))
    }
}

/** Hakt eine Aufgabe ab oder öffnet sie wieder. */
class SetTaskDoneUseCase @Inject constructor(private val repository: TaskRepository) {
    suspend operator fun invoke(id: Long, done: Boolean) = repository.setDone(id, done)
}

/** Löscht eine Aufgabe. */
class DeleteTaskUseCase @Inject constructor(private val repository: TaskRepository) {
    suspend operator fun invoke(id: Long) = repository.delete(id)
}
